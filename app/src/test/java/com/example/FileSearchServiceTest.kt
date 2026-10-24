package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.FileSearchDefaults
import com.example.data.model.FileSearchPipelineState
import com.example.data.model.PdfBookUploadRequest
import com.example.data.model.SearchInstanceConfig
import com.example.data.model.SearchLinkType
import com.example.service.CloudStorageGateway
import com.example.service.CloudStoragePdfService
import com.example.service.RamsesFileSearchOrchestrator
import com.example.service.VertexGeminiFileSearchService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileSearchServiceTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun buildSamplePdfBytes(bookIndex: Int): ByteArray {
        val content = "%PDF-1.7\n1 0 obj\n<< /Title (Ramses Historical Book $bookIndex) >>\nendobj\n%%EOF"
        return content.toByteArray(Charsets.UTF_8)
    }

    private fun buildFiveBookRequests(): List<PdfBookUploadRequest> {
        val titles = listOf(
            "Ramesses II: The Great Pharaoh",
            "Battle of Kadesh Royal Inscriptions",
            "Abu Simbel & Ramesseum Architecture",
            "Egyptian Hieroglyphs & 19th Dynasty",
            "Treaty of Kadesh & Diplomacy"
        )
        return titles.mapIndexed { index, title ->
            PdfBookUploadRequest(
                slotIndex = index,
                title = title,
                fileName = "ramses_book_${index + 1}.pdf",
                rawBytes = buildSamplePdfBytes(index + 1)
            )
        }
    }

    private class RecordingCloudStorageGateway : CloudStorageGateway {
        data class RecordedUpload(
            val bucketName: String,
            val storagePath: String,
            val sizeBytes: Int,
            val metadata: Map<String, String>
        )

        val uploads = mutableListOf<RecordedUpload>()

        override suspend fun uploadPdfBytes(
            bucketName: String,
            storagePath: String,
            pdfBytes: ByteArray,
            customMetadata: Map<String, String>,
            onProgress: (Float) -> Unit
        ): String {
            onProgress(0.5f)
            uploads.add(
                RecordedUpload(
                    bucketName = bucketName,
                    storagePath = storagePath,
                    sizeBytes = pdfBytes.size,
                    metadata = customMetadata
                )
            )
            onProgress(1.0f)
            return "https://firebasestorage.googleapis.com/v0/b/$bucketName/o/${storagePath.replace("/", "%2F")}"
        }
    }

    @Test
    fun uploadFivePdfBooks_uploadsFivePdfsToGoogleCloudStorageWithDeterministicGcsUris() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = RecordingCloudStorageGateway()
        val storageService = CloudStoragePdfService(
            storageGateway = gateway,
            ioDispatcher = dispatcher
        )

        val requests = buildFiveBookRequests()
        val uploaded = storageService.uploadFivePdfBooks(
            context = context,
            requests = requests,
            config = SearchInstanceConfig()
        )

        assertEquals(5, uploaded.size)
        assertEquals(5, gateway.uploads.size)

        uploaded.forEachIndexed { idx, payload ->
            assertTrue(
                payload.book.gcsUri.startsWith(
                    "gs://${FileSearchDefaults.DEFAULT_STORAGE_BUCKET}/${FileSearchDefaults.DEFAULT_GCS_BOOKS_FOLDER}/book_${idx + 1}_"
                )
            )
            assertEquals(64, payload.book.sha256.length)
            assertEquals("ask_ramses_shared_rag", gateway.uploads[idx].metadata["corpus"])
        }
    }

    @Test
    fun uploadFivePdfBooks_rejectsNonFiveCountAndInvalidNonPdfBytes() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = RecordingCloudStorageGateway()
        val storageService = CloudStoragePdfService(
            storageGateway = gateway,
            ioDispatcher = dispatcher
        )

        // 1. Reject 4 books when 5 are strictly required
        try {
            storageService.uploadFivePdfBooks(
                context = context,
                requests = buildFiveBookRequests().take(4)
            )
            fail("Expected IllegalArgumentException when uploading fewer than 5 PDFs")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("5"))
        }

        // 2. Reject corrupted / non-PDF payload missing %PDF- signature
        val corruptedRequests = buildFiveBookRequests().toMutableList().apply {
            this[2] = PdfBookUploadRequest(
                slotIndex = 2,
                title = "Not A PDF",
                fileName = "fake.pdf",
                rawBytes = "PLAIN TEXT WITHOUT PDF HEADER".toByteArray(Charsets.UTF_8)
            )
        }
        try {
            storageService.uploadFivePdfBooks(
                context = context,
                requests = corruptedRequests
            )
            fail("Expected IllegalArgumentException for missing %PDF- header")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("%PDF-"))
        }
    }

    @Test
    fun linkUploadedBooksToSearchInstance_createsGeminiFileSearchStoreAndPollsIndexingOperation() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = RecordingCloudStorageGateway()
        val storageService = CloudStoragePdfService(gateway, dispatcher)
        val uploadedPayloads = storageService.uploadFivePdfBooks(context, buildFiveBookRequests())

        var uploadSessionCounter = 0
        val mockHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val jsonMedia = "application/json".toMediaType()

                when {
                    req.method == "POST" && url.endsWith("/v1beta/fileSearchStores") -> {
                        val respJson = JSONObject().put("name", "fileSearchStores/ramses-five-books-store").toString()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(respJson.toResponseBody(jsonMedia))
                            .build()
                    }
                    req.method == "POST" && url.contains(":uploadToFileSearchStore") -> {
                        uploadSessionCounter++
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .addHeader(
                                "X-Goog-Upload-Url",
                                "https://generativelanguage.googleapis.com/upload/session/$uploadSessionCounter"
                            )
                            .body("{}".toResponseBody(jsonMedia))
                            .build()
                    }
                    req.method == "POST" && url.contains("/upload/session/") -> {
                        val sessionNum = url.substringAfterLast("/")
                        val opJson = JSONObject()
                            .put("name", "fileSearchStores/ramses-five-books-store/operations/op-$sessionNum")
                            .put("done", false)
                            .toString()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(opJson.toResponseBody(jsonMedia))
                            .build()
                    }
                    req.method == "GET" && url.contains("/operations/op-") -> {
                        val opId = url.substringAfterLast("/")
                        val doneJson = JSONObject()
                            .put("name", "fileSearchStores/ramses-five-books-store/operations/$opId")
                            .put("done", true)
                            .put(
                                "response",
                                JSONObject().put(
                                    "documentName",
                                    "fileSearchStores/ramses-five-books-store/documents/doc-$opId"
                                )
                            )
                            .toString()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(doneJson.toResponseBody(jsonMedia))
                            .build()
                    }
                    else -> error("Unexpected HTTP request in test: ${req.method} $url")
                }
            })
            .build()

        val searchService = VertexGeminiFileSearchService(
            configProvider = { SearchInstanceConfig() },
            okHttpClient = mockHttp,
            ioDispatcher = dispatcher,
            pollIntervalMs = 1L
        )

        val linked = searchService.linkUploadedBooksToSearchInstance(
            uploadedPayloads = uploadedPayloads,
            customApiKey = "test-ephemeral-key"
        )

        assertEquals(SearchLinkType.GEMINI_FILE_SEARCH_STORE, linked.linkType)
        assertEquals("fileSearchStores/ramses-five-books-store", linked.storeResourceName)
        assertEquals(5, linked.books.size)
        assertTrue(linked.books.all { it.indexedDocumentName.startsWith("fileSearchStores/ramses-five-books-store/documents/") })

        val toolJson = searchService.buildFileSearchToolJson(linked)
        assertNotNull(toolJson)
        assertEquals(
            "fileSearchStores/ramses-five-books-store",
            toolJson!!.getJSONObject("fileSearch").getJSONArray("fileSearchStoreNames").getString(0)
        )
    }

    @Test
    fun linkUploadedBooksToSearchInstance_importsGcsUrisIntoVertexAiSearchDataStore() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = RecordingCloudStorageGateway()
        val storageService = CloudStoragePdfService(gateway, dispatcher)
        val uploadedPayloads = storageService.uploadFivePdfBooks(context, buildFiveBookRequests())

        var capturedImportBody = ""
        var capturedImportUrl = ""

        val mockHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedImportUrl = req.url.toString()
                val buf = Buffer()
                req.body?.writeTo(buf)
                capturedImportBody = buf.readUtf8()

                val respJson = JSONObject()
                    .put(
                        "name",
                        "projects/gen-lang-client-0341227885/locations/global/collections/default_collection/dataStores/ramses-books-ds/branches/0/operations/import-5-pdfs"
                    )
                    .toString()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(respJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val config = SearchInstanceConfig(vertexSearchDataStoreId = "ramses-books-ds")
        val searchService = VertexGeminiFileSearchService(
            configProvider = { config },
            okHttpClient = mockHttp,
            ioDispatcher = dispatcher
        )

        val linked = searchService.linkUploadedBooksToSearchInstance(
            uploadedPayloads = uploadedPayloads,
            gcpAccessToken = "ya29.test-vertex-oauth-token"
        )

        assertEquals(SearchLinkType.VERTEX_AI_SEARCH_DATASTORE, linked.linkType)
        assertTrue(capturedImportUrl.endsWith("/dataStores/ramses-books-ds/branches/default_branch/documents:import"))

        val parsedBody = JSONObject(capturedImportBody)
        val inputUris = parsedBody.getJSONObject("gcsSource").getJSONArray("inputUris")
        assertEquals(5, inputUris.length())
        for (i in 0 until 5) {
            assertTrue(inputUris.getString(i).startsWith("gs://${FileSearchDefaults.DEFAULT_STORAGE_BUCKET}/"))
        }

        val toolJson = searchService.buildFileSearchToolJson(linked)
        assertNotNull(toolJson)
        assertTrue(
            toolJson!!.getJSONObject("retrieval")
                .getJSONObject("vertexAiSearch")
                .getString("datastore")
                .endsWith("/dataStores/ramses-books-ds")
        )
    }

    @Test
    fun orchestrator_uploadsFivePdfsLinksViaServerProxyAndPersistsInRoomDatabase() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = RecordingCloudStorageGateway()
        val storageService = CloudStoragePdfService(gateway, dispatcher)
        val prefs = context.getSharedPreferences("test_ramses_file_search_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val mockProxyHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val respJson = JSONObject()
                    .put("storeResourceName", "projects/gen-lang-client-0341227885/locations/global/collections/default_collection/dataStores/server-rag-ds")
                    .put("operationName", "operations/server-link-op-99")
                    .toString()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(respJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val orchestrator = RamsesFileSearchOrchestrator(
            appContext = context,
            ragDao = db.ragDao(),
            prefs = prefs,
            cloudStorageService = storageService,
            ioDispatcher = dispatcher,
            vertexServiceFactory = { cfgProvider ->
                VertexGeminiFileSearchService(
                    configProvider = cfgProvider,
                    okHttpClient = mockProxyHttp,
                    ioDispatcher = dispatcher
                )
            }
        )

        orchestrator.updateSearchInstanceConfig(
            SearchInstanceConfig(serverProxyBaseUrl = "https://rag-proxy.ramses.example.com")
        )

        val result = orchestrator.uploadRequestsAndLinkToSearch(
            requests = buildFiveBookRequests(),
            enforceExactFive = true
        )

        assertTrue(result.isSuccess)
        val state = orchestrator.pipelineState.value
        assertTrue(state is FileSearchPipelineState.Ready)

        val persistedPdfDocs = db.ragDao().getActiveCloudPdfBooks()
        assertEquals(5, persistedPdfDocs.size)
        assertTrue(persistedPdfDocs.all { it.isCloudPdfBook && it.gcsUri.startsWith("gs://") })
    }

    @Test
    fun extractGroundingFromCandidate_parsesRetrievedContextAndWebCitations() {
        val candidateJson = JSONObject().put(
            "groundingMetadata",
            JSONObject().put(
                "groundingChunks",
                JSONArray()
                    .put(
                        JSONObject().put(
                            "retrievedContext",
                            JSONObject()
                                .put("title", "Battle of Kadesh Royal Inscriptions")
                                .put("uri", "gs://gen-lang-client-0341227885.firebasestorage.app/ramses_rag_books/book_2.pdf")
                                .put("text", "King Ramesses II led the Amun division across the Orontes river.")
                        )
                    )
                    .put(
                        JSONObject().put(
                            "web",
                            JSONObject()
                                .put("title", "Abu Simbel Temples - UNESCO")
                                .put("uri", "https://whc.unesco.org/en/list/88/")
                        )
                    )
            )
        )

        val (sources, chunks) = VertexGeminiFileSearchService.extractGroundingFromCandidate(candidateJson)
        assertEquals(2, sources.size)
        assertEquals(1, chunks.size)
        assertEquals("Battle of Kadesh Royal Inscriptions", chunks[0].title)
        assertTrue(chunks[0].snippet.contains("Orontes"))
    }
}
