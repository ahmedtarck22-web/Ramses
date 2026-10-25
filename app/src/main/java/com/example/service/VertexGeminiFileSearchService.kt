package com.example.service

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.FileSearchDefaults
import com.example.data.model.GroundedFileSearchResponse
import com.example.data.model.GroundingSource
import com.example.data.model.LinkedSearchInstance
import com.example.data.model.RetrievedBookChunk
import com.example.data.model.SearchInstanceConfig
import com.example.data.model.SearchLinkType
import com.example.data.model.UploadedGcsPdfBook
import com.example.security.SecureKeyStoreHelper
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Service layer that:
 * 1. Lazily initializes the Vertex AI / Gemini SDK (`Firebase.ai(backend = GenerativeBackend.vertexAI(...))`)
 *    with App Check protection and `gemini-3.8-flash`.
 * 2. Links the 5 uploaded Google Cloud Storage PDFs (`gs://...`) to a Search instance:
 *    - Server-Side RAG Proxy (keeps API keys & service-account credentials 100% server-side)
 *    - Vertex AI Search / Discovery Engine DataStore (`documents:import` from `gcsSource.inputUris`)
 *    - Gemini API Managed `FileSearchStore` (`v1beta/fileSearchStores` + resumable upload + LRO polling)
 *    - Vertex AI in Firebase Native `gs://` Corpus (`GenerativeBackend.vertexAI` + `fileData("gs://...", "application/pdf")`)
 * 3. Executes grounded File Search queries and extracts `retrievedContext` book citations.
 */
class VertexGeminiFileSearchService(
    private val configProvider: () -> SearchInstanceConfig = { SearchInstanceConfig() },
    private val okHttpClient: OkHttpClient = defaultHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMs: Long = 1_500L
) {

    companion object {
        private const val TAG = "VertexFileSearchSvc"
        private const val GEMINI_API_ROOT = "https://generativelanguage.googleapis.com"
        private const val DISCOVERY_ENGINE_ROOT = "https://discoveryengine.googleapis.com/v1"
        private const val SDK_TIMEOUT_MS = 60_000L

        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Parses both File Search / Vertex AI Search `retrievedContext` chunks AND
         * Google Search `web` chunks from a Gemini `candidate` JSON object.
         */
        fun extractGroundingFromCandidate(
            candidate: JSONObject
        ): Pair<List<GroundingSource>, List<RetrievedBookChunk>> {
            val sources = mutableListOf<GroundingSource>()
            val bookChunks = mutableListOf<RetrievedBookChunk>()

            val metadata = candidate.optJSONObject("groundingMetadata") ?: return Pair(emptyList(), emptyList())
            val chunks = metadata.optJSONArray("groundingChunks") ?: return Pair(emptyList(), emptyList())

            for (i in 0 until chunks.length()) {
                val chunkObj = chunks.optJSONObject(i) ?: continue

                // 1. Gemini File Search & Vertex AI Search citations (`retrievedContext`)
                val retrieved = chunkObj.optJSONObject("retrievedContext")
                if (retrieved != null) {
                    val rawTitle = retrieved.optString("title", "").ifBlank { "مرجع من الكتب المفهرسة" }
                    val rawUri = retrieved.optString("uri", "").ifBlank { "fileSearchStores/book_${i + 1}" }
                    val snippet = retrieved.optString("text", "")
                    sources.add(GroundingSource(title = rawTitle, uri = rawUri))
                    bookChunks.add(RetrievedBookChunk(title = rawTitle, uri = rawUri, snippet = snippet))
                }

                // 2. Live Google Search citations (`web`)
                val web = chunkObj.optJSONObject("web")
                if (web != null) {
                    val title = web.optString("title", "Google Search")
                    val uri = web.optString("uri", "")
                    if (uri.isNotBlank()) {
                        sources.add(GroundingSource(title = title, uri = uri))
                    }
                }
            }

            return Pair(sources.distinctBy { "${it.title}|${it.uri}" }, bookChunks)
        }
    }

    @Volatile
    private var sdkInitialized: Boolean = false

    // Lazy Vertex AI in Firebase SDK initialization (mandated by firebase-ai-logic-android)
    private val vertexAiDefaultModel by lazy {
        val location = configProvider().vertexLocation.ifBlank { FileSearchDefaults.DEFAULT_VERTEX_LOCATION }
        sdkInitialized = true
        Firebase.ai(backend = GenerativeBackend.vertexAI(location = location))
            .generativeModel(modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL)
    }

    // Lazy Google AI backend fallback model
    private val googleAiDefaultModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL)
    }

    /**
     * Explicitly warms/verifies lazy SDK configuration metadata without triggering eager network calls.
     */
    fun isSdkReady(): Boolean = sdkInitialized

    private fun createVertexModelWithInstruction(
        systemInstruction: String,
        enableGoogleSearch: Boolean = false
    ) = Firebase.ai(
        backend = GenerativeBackend.vertexAI(
            location = configProvider().vertexLocation.ifBlank { FileSearchDefaults.DEFAULT_VERTEX_LOCATION }
        )
    ).generativeModel(
        modelName = FileSearchDefaults.DEFAULT_VERTEX_GEMINI_MODEL,
        systemInstruction = content { text(systemInstruction) },
        tools = if (enableGoogleSearch) listOf(Tool.googleSearch()) else emptyList()
    ).also {
        sdkInitialized = true
    }

    private fun resolveEffectiveApiKey(customApiKey: String?): String {
        return when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" ->
                BuildConfig.GEMINI_API_KEY.trim()
            else -> ""
        }
    }

    /**
     * Links the uploaded Google Cloud Storage PDFs (`gs://...`) to a Search instance.
     *
     * Priority order:
     * 1. Server-side RAG proxy (`config.serverProxyBaseUrl`) — keeps API keys 100% server-side.
     * 2. Vertex AI Search / Discovery Engine DataStore (`config.vertexSearchDataStoreId`) — imports
     *    the 5 `gs://` URIs via `documents:import` if [gcpAccessToken] is supplied, and binds the datastore path.
     * 3. Gemini API Managed `FileSearchStore` (`v1beta/fileSearchStores`) — creates/reuses the store,
     *    uploads each PDF via the resumable upload protocol, and polls indexing LROs until ready.
     * 4. Vertex AI in Firebase Native GCS Corpus (`GenerativeBackend.vertexAI`) — binds the `gs://` URIs
     *    directly for App-Check-protected multimodal PDF grounding.
     */
    suspend fun linkUploadedBooksToSearchInstance(
        uploadedPayloads: List<CloudStoragePdfService.UploadedPdfPayload>,
        customApiKey: String? = null,
        gcpAccessToken: String? = null
    ): LinkedSearchInstance = withContext(ioDispatcher) {
        require(uploadedPayloads.isNotEmpty()) { "Cannot link an empty PDF list to Search instance." }

        val config = configProvider()
        val books = uploadedPayloads.map { it.book }

        // 1. Server-Side Proxy (Production-grade zero-client-secret architecture)
        if (config.serverProxyBaseUrl.isNotBlank()) {
            return@withContext linkViaServerProxy(config.serverProxyBaseUrl, config, books)
        }

        // 2. Vertex AI Search / Discovery Engine DataStore (`gcsSource.inputUris`)
        if (config.vertexSearchDataStoreId.isNotBlank()) {
            val dataStorePath = config.buildVertexDataStoreResourceName()
            var operationName = ""
            if (!gcpAccessToken.isNullOrBlank()) {
                operationName = importGcsBooksToVertexSearchDataStore(
                    dataStoreResourcePath = dataStorePath,
                    gcsUris = books.map { it.gcsUri },
                    gcpAccessToken = gcpAccessToken
                )
            }
            Log.d(TAG, "Linked ${books.size} GCS PDFs to Vertex AI Search DataStore: $dataStorePath")
            return@withContext LinkedSearchInstance(
                linkType = SearchLinkType.VERTEX_AI_SEARCH_DATASTORE,
                storeResourceName = dataStorePath,
                importOperationName = operationName,
                books = books,
                isReady = true
            )
        }

        // 3. Gemini Managed File Search Store (`v1beta/fileSearchStores`)
        val apiKey = resolveEffectiveApiKey(customApiKey)
        if (apiKey.isNotBlank()) {
            return@withContext linkViaGeminiFileSearchStore(
                uploadedPayloads = uploadedPayloads,
                config = config,
                apiKey = apiKey
            )
        }

        // 4. Native Vertex AI in Firebase (`GenerativeBackend.vertexAI`) over `gs://` PDF URIs
        val corpusUri = "gs://${config.normalizedBucketName()}/${config.gcsFolder.trim('/')}"
        sdkInitialized = true
        Log.d(TAG, "Linked ${books.size} GCS PDFs to Vertex AI in Firebase corpus: $corpusUri")
        LinkedSearchInstance(
            linkType = SearchLinkType.VERTEX_AI_GCS_MULTIMODAL_CORPUS,
            storeResourceName = corpusUri,
            books = books,
            isReady = true
        )
    }

    /**
     * Imports `gs://` PDF URIs into a Vertex AI Search (Discovery Engine) DataStore:
     * `POST https://discoveryengine.googleapis.com/v1/{dataStoreResourcePath}/branches/default_branch/documents:import`
     */
    suspend fun importGcsBooksToVertexSearchDataStore(
        dataStoreResourcePath: String,
        gcsUris: List<String>,
        gcpAccessToken: String
    ): String = withContext(ioDispatcher) {
        val url = "$DISCOVERY_ENGINE_ROOT/${dataStoreResourcePath.trimStart('/')}/branches/default_branch/documents:import"
        val payload = JSONObject().apply {
            put(
                "gcsSource",
                JSONObject().apply {
                    val urisArray = JSONArray()
                    gcsUris.forEach { urisArray.put(it) }
                    put("inputUris", urisArray)
                    put("dataSchema", "content")
                }
            )
            put("reconciliationMode", "INCREMENTAL")
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${gcpAccessToken.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val safeBody = SecureKeyStoreHelper.redactSecret(body.take(240), gcpAccessToken)
                throw IOException("Vertex AI Search documents:import failed (${response.code}): $safeBody")
            }
            JSONObject(body).optString("name", "")
        }
    }

    /**
     * Creates or reuses a Gemini `FileSearchStore`, uploads all PDFs via the 2-phase resumable
     * upload protocol (`uploadToFileSearchStore`), and polls indexing operations until complete.
     */
    private suspend fun linkViaGeminiFileSearchStore(
        uploadedPayloads: List<CloudStoragePdfService.UploadedPdfPayload>,
        config: SearchInstanceConfig,
        apiKey: String
    ): LinkedSearchInstance {
        val storeName = ensureGeminiFileSearchStore(config, apiKey)
        val indexedBooks = mutableListOf<UploadedGcsPdfBook>()
        var lastOpName = ""

        for (payload in uploadedPayloads) {
            val opJson = uploadPdfToGeminiFileSearchStore(
                storeResourceName = storeName,
                book = payload.book,
                pdfBytes = payload.bytes,
                apiKey = apiKey
            )
            val opName = opJson.optString("name", "")
            val isDoneImmediately = opJson.optBoolean("done", false)

            val completedJson = if (opName.isNotBlank() && !isDoneImmediately) {
                lastOpName = opName
                pollGeminiOperationUntilDone(opName, apiKey)
            } else {
                opJson
            }

            val indexedDocName = completedJson.optJSONObject("response")
                ?.optString("documentName", "")
                ?.ifBlank { completedJson.optJSONObject("response")?.optString("name", "").orEmpty() }
                .orEmpty()

            indexedBooks.add(payload.book.copy(indexedDocumentName = indexedDocName))
        }

        return LinkedSearchInstance(
            linkType = SearchLinkType.GEMINI_FILE_SEARCH_STORE,
            storeResourceName = storeName,
            importOperationName = lastOpName,
            books = indexedBooks,
            isReady = true
        )
    }

    /**
     * Creates a new `FileSearchStore` via `POST /v1beta/fileSearchStores` if not already configured.
     */
    suspend fun ensureGeminiFileSearchStore(
        config: SearchInstanceConfig,
        apiKey: String
    ): String = withContext(ioDispatcher) {
        val existing = config.geminiFileSearchStoreName.trim()
        if (existing.startsWith("fileSearchStores/")) {
            return@withContext existing
        }

        val createBody = JSONObject().apply {
            put("displayName", config.geminiFileSearchDisplayName)
        }
        val request = Request.Builder()
            .url("$GEMINI_API_ROOT/v1beta/fileSearchStores")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(createBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val safeBody = SecureKeyStoreHelper.redactSecret(body.take(240), apiKey)
                throw IOException("Failed to create Gemini FileSearchStore (${response.code}): $safeBody")
            }
            val storeName = JSONObject(body).optString("name", "")
            if (storeName.isBlank()) {
                throw IOException("Gemini FileSearchStore creation returned an empty store name.")
            }
            storeName
        }
    }

    /**
     * Uploads a single PDF book into `fileSearchStores/{id}:uploadToFileSearchStore` using
     * Google's 2-step resumable media upload protocol.
     */
    private fun uploadPdfToGeminiFileSearchStore(
        storeResourceName: String,
        book: UploadedGcsPdfBook,
        pdfBytes: ByteArray,
        apiKey: String
    ): JSONObject {
        val normalizedStore = storeResourceName.trimStart('/')
        val initUrl = "$GEMINI_API_ROOT/upload/v1beta/$normalizedStore:uploadToFileSearchStore"

        val metadataJson = JSONObject().apply {
            put("displayName", book.title)
        }

        // Step 1: Initiate resumable upload session
        val startRequest = Request.Builder()
            .url(initUrl)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "start")
            .addHeader("X-Goog-Upload-Header-Content-Length", pdfBytes.size.toString())
            .addHeader("X-Goog-Upload-Header-Content-Type", FileSearchDefaults.PDF_MIME_TYPE)
            .addHeader("Content-Type", "application/json")
            .post(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val uploadSessionUrl = okHttpClient.newCall(startRequest).execute().use { startResp ->
            if (!startResp.isSuccessful) {
                val err = SecureKeyStoreHelper.redactSecret(startResp.body?.string().orEmpty().take(200), apiKey)
                throw IOException("FileSearchStore upload start failed (${startResp.code}) for '${book.fileName}': $err")
            }
            startResp.header("X-Goog-Upload-Url")
                ?: startResp.header("x-goog-upload-url")
                ?: throw IOException("Missing X-Goog-Upload-Url header for '${book.fileName}'.")
        }

        // Step 2: Stream PDF bytes and finalize
        val finalizeRequest = Request.Builder()
            .url(uploadSessionUrl)
            .addHeader("Content-Length", pdfBytes.size.toString())
            .addHeader("X-Goog-Upload-Offset", "0")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .post(pdfBytes.toRequestBody(FileSearchDefaults.PDF_MIME_TYPE.toMediaType()))
            .build()

        return okHttpClient.newCall(finalizeRequest).execute().use { uploadResp ->
            val respBody = uploadResp.body?.string().orEmpty()
            if (!uploadResp.isSuccessful) {
                val safeResp = SecureKeyStoreHelper.redactSecret(respBody.take(200), apiKey)
                throw IOException("FileSearchStore byte finalize failed (${uploadResp.code}) for '${book.fileName}': $safeResp")
            }
            if (respBody.isBlank()) JSONObject().put("done", true) else JSONObject(respBody)
        }
    }

    private suspend fun pollGeminiOperationUntilDone(
        operationName: String,
        apiKey: String,
        maxAttempts: Int = 20
    ): JSONObject {
        val cleanOp = operationName.trimStart('/')
        val pollUrl = "$GEMINI_API_ROOT/v1beta/$cleanOp"

        repeat(maxAttempts) {
            val req = Request.Builder()
                .url(pollUrl)
                .addHeader("x-goog-api-key", apiKey)
                .get()
                .build()

            val json = okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val safeBody = SecureKeyStoreHelper.redactSecret(body.take(180), apiKey)
                    throw IOException("Polling FileSearch operation '$cleanOp' failed (${resp.code}): $safeBody")
                }
                JSONObject(body)
            }

            if (json.optBoolean("done", false)) {
                val errObj = json.optJSONObject("error")
                if (errObj != null) {
                    throw IOException("FileSearch indexing failed: ${errObj.optString("message", "Unknown indexing error")}")
                }
                return json
            }
            delay(pollIntervalMs)
        }
        return JSONObject().put("name", cleanOp).put("done", false)
    }

    /**
     * Links uploaded GCS `gs://` PDF URIs via a server-side Cloud Run / Functions endpoint
     * authenticated with Firebase Auth + App Check (zero client API keys).
     */
    private suspend fun linkViaServerProxy(
        proxyBaseUrl: String,
        config: SearchInstanceConfig,
        books: List<UploadedGcsPdfBook>
    ): LinkedSearchInstance {
        val endpoint = "${proxyBaseUrl.trimEnd('/')}/v1/rag/link-books"
        val payload = JSONObject().apply {
            put("projectId", config.projectId)
            put("storageBucket", config.normalizedBucketName())
            put("vertexSearchDataStoreId", config.vertexSearchDataStoreId)
            put("geminiFileSearchDisplayName", config.geminiFileSearchDisplayName)
            val urisArr = JSONArray()
            val booksArr = JSONArray()
            books.forEach { b ->
                urisArr.put(b.gcsUri)
                booksArr.put(b.toJson())
            }
            put("gcsUris", urisArr)
            put("books", booksArr)
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        attachFirebaseSecurityHeaders(reqBuilder)

        return okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Server RAG proxy link-books failed (${resp.code}): ${body.take(220)}")
            }
            val json = JSONObject(body)
            val storeResource = json.optString("storeResourceName", "")
                .ifBlank { "proxy://ask-ramses-rag" }
            val opName = json.optString("operationName", "")
            LinkedSearchInstance(
                linkType = SearchLinkType.SERVER_PROXY_INDEX,
                storeResourceName = storeResource,
                importOperationName = opName,
                books = books,
                isReady = true
            )
        }
    }

    /**
     * Builds the JSON `tools` object required to attach a linked Search instance
     * (`fileSearch` store or `retrieval.vertexAiSearch` datastore) to a Gemini `generateContent` request.
     */
    fun buildFileSearchToolJson(instance: LinkedSearchInstance): JSONObject? {
        if (!instance.isReady || instance.storeResourceName.isBlank()) return null
        return when (instance.linkType) {
            SearchLinkType.GEMINI_FILE_SEARCH_STORE -> {
                JSONObject().put(
                    "fileSearch",
                    JSONObject().put(
                        "fileSearchStoreNames",
                        JSONArray().put(instance.storeResourceName)
                    )
                )
            }
            SearchLinkType.VERTEX_AI_SEARCH_DATASTORE -> {
                JSONObject().put(
                    "retrieval",
                    JSONObject().put(
                        "vertexAiSearch",
                        JSONObject().put("datastore", instance.storeResourceName)
                    )
                )
            }
            else -> null
        }
    }

    /**
     * Streams a grounded response against the linked 5 PDF books using:
     * 1. Server-side RAG proxy (if linked via `SERVER_PROXY_INDEX`),
     * 2. Gemini File Search / Vertex AI Search tool over SSE (if API key & store available), or
     * 3. Vertex AI in Firebase SDK (`GenerativeBackend.vertexAI`) with `fileData(uri = gcsUri, mimeType = "application/pdf")`
     *    inside a 60-second coroutine timeout window.
     */
    suspend fun streamGroundedQuery(
        prompt: String,
        systemInstruction: String,
        linkedInstance: LinkedSearchInstance,
        activeGcsUris: List<String> = linkedInstance.books.map { it.gcsUri },
        customApiKey: String? = null,
        enableGoogleSearch: Boolean = false,
        onChunk: suspend (String) -> Unit = {}
    ): GroundedFileSearchResponse = withContext(ioDispatcher) {
        val config = configProvider()

        // A. Server-Side Proxy Query
        if (linkedInstance.linkType == SearchLinkType.SERVER_PROXY_INDEX && config.serverProxyBaseUrl.isNotBlank()) {
            return@withContext queryServerProxy(
                proxyBaseUrl = config.serverProxyBaseUrl,
                prompt = prompt,
                systemInstruction = systemInstruction,
                instance = linkedInstance,
                onChunk = onChunk
            )
        }

        // B. Gemini File Search Store / Vertex AI Search REST SSE with citations
        val apiKey = resolveEffectiveApiKey(customApiKey)
        val searchToolJson = buildFileSearchToolJson(linkedInstance)
        if (apiKey.isNotBlank() && searchToolJson != null) {
            try {
                return@withContext streamWithRestFileSearchTool(
                    prompt = prompt,
                    systemInstruction = systemInstruction,
                    searchToolJson = searchToolJson,
                    enableGoogleSearch = enableGoogleSearch,
                    apiKey = apiKey,
                    onChunk = onChunk
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "REST FileSearch stream fallback to Vertex AI SDK: ${SecureKeyStoreHelper.redactSecret(e.message, apiKey)}")
            }
        }

        // C. Vertex AI in Firebase SDK (`Firebase.ai(backend = GenerativeBackend.vertexAI())`) with `gs://` PDF URIs
        streamWithVertexAiFirebaseSdk(
            prompt = prompt,
            systemInstruction = systemInstruction,
            linkedBooks = linkedInstance.books.filter { activeGcsUris.isEmpty() || it.gcsUri in activeGcsUris },
            enableGoogleSearch = enableGoogleSearch,
            onChunk = onChunk
        )
    }

    private suspend fun streamWithRestFileSearchTool(
        prompt: String,
        systemInstruction: String,
        searchToolJson: JSONObject,
        enableGoogleSearch: Boolean,
        apiKey: String,
        onChunk: suspend (String) -> Unit
    ): GroundedFileSearchResponse {
        val toolsArray = JSONArray().apply {
            put(searchToolJson)
            if (enableGoogleSearch) {
                put(JSONObject().put("googleSearch", JSONObject()))
            }
        }

        val requestJson = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            put("tools", toolsArray)
            put("generationConfig", JSONObject().put("temperature", 0.3))
        }

        val modelName = "gemini-3.1-flash-lite"
        val url = "$GEMINI_API_ROOT/v1beta/models/$modelName:streamGenerateContent?alt=sse"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val accumulated = StringBuilder()
        val allSources = mutableListOf<GroundingSource>()
        val allChunks = mutableListOf<RetrievedBookChunk>()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = SecureKeyStoreHelper.redactSecret(response.body?.string().orEmpty().take(200), apiKey)
                throw IOException("Gemini File Search SSE failed (${response.code}): $err")
            }
            val source = response.body?.source() ?: throw IOException("Empty SSE response stream")
            val reader = BufferedReader(InputStreamReader(source.inputStream(), Charsets.UTF_8))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim() ?: continue
                if (!trimmed.startsWith("data: ")) continue
                val data = trimmed.removePrefix("data: ").trim()
                if (data.isEmpty() || data == "[DONE]") continue

                val frame = JSONObject(data)
                val candidates = frame.optJSONArray("candidates") ?: continue
                if (candidates.length() == 0) continue

                val candidate = candidates.getJSONObject(0)
                val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val partText = parts.getJSONObject(i).optString("text", "")
                        if (partText.isNotEmpty()) {
                            accumulated.append(partText)
                            onChunk(accumulated.toString())
                        }
                    }
                }
                val (sources, chunks) = extractGroundingFromCandidate(candidate)
                allSources.addAll(sources)
                allChunks.addAll(chunks)
            }
        }

        return GroundedFileSearchResponse(
            answerText = accumulated.toString().trim(),
            groundingSources = allSources.distinctBy { "${it.title}|${it.uri}" },
            retrievedChunks = allChunks,
            backendUsed = "GEMINI_FILE_SEARCH_REST"
        )
    }

    /**
     * Uses the official Vertex AI in Firebase SDK (`Firebase.ai(backend = GenerativeBackend.vertexAI())`)
     * with `fileData(uri = gcsUri, mimeType = "application/pdf")` and mandated 60s timeout.
     */
    private suspend fun streamWithVertexAiFirebaseSdk(
        prompt: String,
        systemInstruction: String,
        linkedBooks: List<UploadedGcsPdfBook>,
        enableGoogleSearch: Boolean,
        onChunk: suspend (String) -> Unit
    ): GroundedFileSearchResponse {
        return try {
            val model = if (systemInstruction.isNotBlank() || enableGoogleSearch) {
                createVertexModelWithInstruction(systemInstruction, enableGoogleSearch)
            } else {
                vertexAiDefaultModel
            }

            val input = content("user") {
                for (book in linkedBooks.take(FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT)) {
                    if (book.gcsUri.startsWith("gs://")) {
                        fileData(uri = book.gcsUri, mimeType = FileSearchDefaults.PDF_MIME_TYPE)
                    }
                }
                text(prompt)
            }

            val accumulated = StringBuilder()
            withTimeout(SDK_TIMEOUT_MS) {
                model.generateContentStream(input).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        onChunk(accumulated.toString())
                    }
                }
            }

            val bookCitations = linkedBooks.map {
                GroundingSource(title = it.title, uri = it.gcsUri)
            }

            GroundedFileSearchResponse(
                answerText = accumulated.toString().trim(),
                groundingSources = bookCitations,
                backendUsed = "VERTEX_AI_FIREBASE_SDK"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vertex AI Firebase SDK generation failed, attempting Google AI fallback", e)
            // Secondary SDK fallback without gs:// fileData if Blaze billing is not yet enabled on Vertex AI
            withTimeout(SDK_TIMEOUT_MS) {
                val bookTitlesSummary = linkedBooks.joinToString(", ") { it.title }
                val fallbackResp = googleAiDefaultModel.generateContent("$systemInstruction\n\n[Linked Books: $bookTitlesSummary]\n$prompt")
                val txt = fallbackResp.text?.trim().orEmpty()
                if (txt.isNotEmpty()) onChunk(txt)
                GroundedFileSearchResponse(
                    answerText = txt,
                    groundingSources = linkedBooks.map { GroundingSource(it.title, it.gcsUri) },
                    backendUsed = "GOOGLE_AI_FIREBASE_SDK_FALLBACK"
                )
            }
        }
    }

    private suspend fun queryServerProxy(
        proxyBaseUrl: String,
        prompt: String,
        systemInstruction: String,
        instance: LinkedSearchInstance,
        onChunk: suspend (String) -> Unit
    ): GroundedFileSearchResponse {
        val endpoint = "${proxyBaseUrl.trimEnd('/')}/v1/rag/query"
        val payload = JSONObject().apply {
            put("prompt", prompt)
            put("systemInstruction", systemInstruction)
            put("storeResourceName", instance.storeResourceName)
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        attachFirebaseSecurityHeaders(reqBuilder)

        return okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Server RAG proxy query failed (${resp.code}): ${body.take(200)}")
            }
            val json = JSONObject(body)
            val answer = json.optString("text", "")
            if (answer.isNotEmpty()) onChunk(answer)

            val sources = mutableListOf<GroundingSource>()
            val srcArr = json.optJSONArray("groundingSources")
            if (srcArr != null) {
                for (i in 0 until srcArr.length()) {
                    val s = srcArr.optJSONObject(i) ?: continue
                    sources.add(GroundingSource(s.optString("title", "PDF"), s.optString("uri", "")))
                }
            }
            GroundedFileSearchResponse(
                answerText = answer,
                groundingSources = sources,
                backendUsed = "SERVER_PROXY"
            )
        }
    }

    private suspend fun attachFirebaseSecurityHeaders(builder: Request.Builder) {
        try {
            val idToken = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
            if (!idToken.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $idToken")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Auth token unavailable for proxy header: ${e.message}")
        }

        try {
            val appCheckToken = FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
            if (appCheckToken.isNotBlank()) {
                builder.addHeader("X-Firebase-AppCheck", appCheckToken)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "AppCheck token unavailable for proxy header: ${e.message}")
        }
    }
}
