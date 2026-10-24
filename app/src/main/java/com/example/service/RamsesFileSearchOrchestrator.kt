package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.data.db.RagDao
import com.example.data.model.FileSearchPipelineState
import com.example.data.model.GroundedFileSearchResponse
import com.example.data.model.LinkedSearchInstance
import com.example.data.model.PdfBookUploadRequest
import com.example.data.model.RagDocument
import com.example.data.model.SearchInstanceConfig
import com.example.data.model.UploadedGcsPdfBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * High-level orchestrator service that coordinates:
 * 1. Uploading the 5 PDF books to Google Cloud Storage via [CloudStoragePdfService],
 * 2. Initializing Vertex AI / Gemini SDK and linking the uploaded `gs://` PDFs to a
 *    Search instance via [VertexGeminiFileSearchService], and
 * 3. Persisting linked book records in Room ([RagDao]) & [SharedPreferences].
 */
class RamsesFileSearchOrchestrator(
    private val appContext: Context,
    private val ragDao: RagDao,
    private val prefs: SharedPreferences,
    val cloudStorageService: CloudStoragePdfService = CloudStoragePdfService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    vertexServiceFactory: ((() -> SearchInstanceConfig) -> VertexGeminiFileSearchService)? = null
) {

    companion object {
        private const val TAG = "RamsesFileSearchOrch"
        const val PREF_SEARCH_CONFIG_JSON = "file_search_instance_config_json"
        const val PREF_LINKED_INSTANCE_JSON = "file_search_linked_instance_json"
    }

    private val _searchConfig = MutableStateFlow(
        SearchInstanceConfig.fromJsonString(prefs.getString(PREF_SEARCH_CONFIG_JSON, null))
    )
    val searchConfig: StateFlow<SearchInstanceConfig> = _searchConfig.asStateFlow()

    private val _linkedInstance = MutableStateFlow(
        LinkedSearchInstance.fromJsonString(prefs.getString(PREF_LINKED_INSTANCE_JSON, null))
    )
    val linkedInstance: StateFlow<LinkedSearchInstance?> = _linkedInstance.asStateFlow()

    private val _pipelineState = MutableStateFlow<FileSearchPipelineState>(
        _linkedInstance.value?.let { FileSearchPipelineState.Ready(it) } ?: FileSearchPipelineState.Idle
    )
    val pipelineState: StateFlow<FileSearchPipelineState> = _pipelineState.asStateFlow()

    val vertexFileSearchService: VertexGeminiFileSearchService =
        vertexServiceFactory?.invoke { _searchConfig.value }
            ?: VertexGeminiFileSearchService(configProvider = { _searchConfig.value })

    fun updateSearchInstanceConfig(newConfig: SearchInstanceConfig) {
        _searchConfig.value = newConfig
        prefs.edit().putString(PREF_SEARCH_CONFIG_JSON, newConfig.toJsonString()).apply()
    }

    /**
     * Uploads PDF files selected via Android's document picker (`1..5` PDFs) to Google Cloud Storage
     * and links them to the active Vertex AI / Gemini File Search instance.
     */
    suspend fun uploadUrisAndLinkToSearch(
        uris: List<Uri>,
        customApiKey: String? = null,
        gcpAccessToken: String? = null,
        enforceExactFive: Boolean = false
    ): Result<LinkedSearchInstance> = withContext(ioDispatcher) {
        val requests = cloudStorageService.buildUploadRequestsFromUris(appContext, uris)
        uploadRequestsAndLinkToSearch(
            requests = requests,
            customApiKey = customApiKey,
            gcpAccessToken = gcpAccessToken,
            enforceExactFive = enforceExactFive
        )
    }

    /**
     * Uploads the 5 PDF books (`enforceExactFive = true` by default for programmatic 5-book cohort)
     * to Google Cloud Storage (`gs://...`) and links them to the configured Search instance.
     */
    suspend fun uploadRequestsAndLinkToSearch(
        requests: List<PdfBookUploadRequest>,
        customApiKey: String? = null,
        gcpAccessToken: String? = null,
        enforceExactFive: Boolean = true
    ): Result<LinkedSearchInstance> = withContext(ioDispatcher) {
        var uploadedBooksSnapshot: List<UploadedGcsPdfBook> = emptyList()
        try {
            val currentConfig = _searchConfig.value

            // Step 1: Upload PDFs to Google Cloud Storage (`gs://gen-lang-client-0341227885.firebasestorage.app/...`)
            val uploadedPayloads = cloudStorageService.uploadPdfBatch(
                context = appContext,
                requests = requests,
                config = currentConfig,
                enforceExactFive = enforceExactFive,
                onProgress = { bookNum, total, fileName, fraction ->
                    _pipelineState.value = FileSearchPipelineState.UploadingToCloudStorage(
                        currentBookNumber = bookNum,
                        totalBooks = total,
                        fileName = fileName,
                        progressFraction = fraction
                    )
                }
            )
            uploadedBooksSnapshot = uploadedPayloads.map { it.book }

            // Step 2: Link uploaded `gs://` PDF URIs to the Search instance (Vertex AI Search / Gemini FileSearchStore)
            val targetDesc = when {
                currentConfig.serverProxyBaseUrl.isNotBlank() -> currentConfig.serverProxyBaseUrl
                currentConfig.vertexSearchDataStoreId.isNotBlank() -> currentConfig.buildVertexDataStoreResourceName()
                else -> currentConfig.geminiFileSearchDisplayName
            }
            _pipelineState.value = FileSearchPipelineState.LinkingToSearchInstance(
                uploadedBooks = uploadedBooksSnapshot,
                targetDescription = targetDesc
            )

            val linked = vertexFileSearchService.linkUploadedBooksToSearchInstance(
                uploadedPayloads = uploadedPayloads,
                customApiKey = customApiKey,
                gcpAccessToken = gcpAccessToken
            )

            // If a new Gemini FileSearchStore was dynamically created, persist its store name in config
            if (linked.storeResourceName.startsWith("fileSearchStores/") &&
                currentConfig.geminiFileSearchStoreName != linked.storeResourceName
            ) {
                updateSearchInstanceConfig(
                    currentConfig.copy(geminiFileSearchStoreName = linked.storeResourceName)
                )
            }

            // Step 3: Save each linked PDF book into Room (`rag_documents`) so it integrates seamlessly with Ask Rameses
            for (book in linked.books) {
                val ragDoc = RagDocument(
                    id = book.bookId,
                    title = book.title,
                    content = "كتاب PDF مفهرس سحابياً (${formatSizeKb(book.sizeBytes)}) • ${book.gcsUri}",
                    category = "كتاب PDF • File Search",
                    isEnabled = true,
                    timestamp = book.uploadedAtMs,
                    gcsUri = book.gcsUri,
                    searchStoreName = linked.storeResourceName,
                    sourceType = "PDF_GCS",
                    sizeBytes = book.sizeBytes
                )
                ragDao.insertDocument(ragDoc)
            }

            _linkedInstance.value = linked
            prefs.edit().putString(PREF_LINKED_INSTANCE_JSON, linked.toJsonString()).apply()
            _pipelineState.value = FileSearchPipelineState.Ready(linked)

            Log.i(TAG, "Successfully uploaded & linked ${linked.books.size} PDFs -> ${linked.storeResourceName}")
            Result.success(linked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload/link PDF books to Search instance", e)
            val message = e.localizedMessage ?: "Failed to upload PDFs to Cloud Storage or link Search instance."
            _pipelineState.value = FileSearchPipelineState.Error(
                message = message,
                uploadedBooks = uploadedBooksSnapshot
            )
            Result.failure(e)
        }
    }

    /**
     * Queries the linked 5-PDF Search instance with grounded citations.
     */
    suspend fun queryLinkedKnowledgeBase(
        prompt: String,
        systemInstruction: String,
        activeRagDocs: List<RagDocument>,
        customApiKey: String? = null,
        enableGoogleSearch: Boolean = false,
        onChunk: suspend (String) -> Unit = {}
    ): GroundedFileSearchResponse? {
        val activePdfDocs = activeRagDocs.filter { it.isEnabled && it.isCloudPdfBook }
        val instance = _linkedInstance.value ?: buildAdHocInstanceFromDocs(activePdfDocs) ?: return null
        if (activePdfDocs.isEmpty() && instance.books.isEmpty()) return null

        val activeUris = activePdfDocs.mapNotNull { it.gcsUri.takeIf { u -> u.isNotBlank() } }
        return vertexFileSearchService.streamGroundedQuery(
            prompt = prompt,
            systemInstruction = systemInstruction,
            linkedInstance = instance,
            activeGcsUris = activeUris,
            customApiKey = customApiKey,
            enableGoogleSearch = enableGoogleSearch,
            onChunk = onChunk
        )
    }

    private fun buildAdHocInstanceFromDocs(pdfDocs: List<RagDocument>): LinkedSearchInstance? {
        if (pdfDocs.isEmpty()) return null
        val storeName = pdfDocs.firstOrNull { it.searchStoreName.isNotBlank() }?.searchStoreName
            ?: "gs://${_searchConfig.value.normalizedBucketName()}/${_searchConfig.value.gcsFolder}"
        val linkType = when {
            storeName.startsWith("fileSearchStores/") -> com.example.data.model.SearchLinkType.GEMINI_FILE_SEARCH_STORE
            storeName.startsWith("projects/") -> com.example.data.model.SearchLinkType.VERTEX_AI_SEARCH_DATASTORE
            storeName.startsWith("http") -> com.example.data.model.SearchLinkType.SERVER_PROXY_INDEX
            else -> com.example.data.model.SearchLinkType.VERTEX_AI_GCS_MULTIMODAL_CORPUS
        }
        val books = pdfDocs.mapIndexed { index, doc ->
            UploadedGcsPdfBook(
                bookId = doc.id,
                slotIndex = index,
                title = doc.title,
                fileName = "${doc.title}.pdf",
                gcsUri = doc.gcsUri,
                storagePath = doc.gcsUri.removePrefix("gs://${_searchConfig.value.normalizedBucketName()}/"),
                sizeBytes = doc.sizeBytes,
                sha256 = ""
            )
        }
        return LinkedSearchInstance(
            linkType = linkType,
            storeResourceName = storeName,
            books = books,
            isReady = true
        )
    }

    private fun formatSizeKb(bytes: Long): String {
        return if (bytes >= 1024 * 1024) {
            String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        } else {
            "${maxOf(1L, bytes / 1024L)} KB"
        }
    }
}
