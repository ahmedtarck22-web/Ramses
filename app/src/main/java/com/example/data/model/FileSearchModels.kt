package com.example.data.model

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Constants and domain models for uploading the 5 PDF books to Google Cloud Storage
 * and linking them to a Vertex AI / Gemini File Search instance.
 */
object FileSearchDefaults {
    const val REQUIRED_PDF_BOOK_COUNT = 5
    const val MAX_PDF_SIZE_BYTES = 50L * 1024L * 1024L // 50 MB per PDF book
    const val DEFAULT_PROJECT_ID = "gen-lang-client-0341227885"
    const val DEFAULT_STORAGE_BUCKET = "gen-lang-client-0341227885.firebasestorage.app"
    const val DEFAULT_GCS_BOOKS_FOLDER = "ramses_rag_books"
    const val DEFAULT_VERTEX_LOCATION = "us-central1"
    const val DEFAULT_DISCOVERY_ENGINE_LOCATION = "global"
    const val DEFAULT_FILE_SEARCH_STORE_DISPLAY_NAME = "ask-ramses-5-books-store"
    const val DEFAULT_VERTEX_GEMINI_MODEL = "gemini-3.8-flash"
    const val PDF_MIME_TYPE = "application/pdf"
}

/**
 * Represents an input PDF book to be uploaded to Google Cloud Storage.
 * Supports Android content [Uri], local [File], or in-memory [rawBytes].
 */
data class PdfBookUploadRequest(
    val slotIndex: Int,
    val title: String,
    val fileName: String,
    val uri: Uri? = null,
    val localFile: File? = null,
    val rawBytes: ByteArray? = null,
    val mimeType: String = FileSearchDefaults.PDF_MIME_TYPE
)

/**
 * Metadata for a PDF book uploaded to Google Cloud Storage (`gs://...`)
 * and optionally indexed into a Search instance.
 */
data class UploadedGcsPdfBook(
    val bookId: String = UUID.randomUUID().toString(),
    val slotIndex: Int,
    val title: String,
    val fileName: String,
    val gcsUri: String,
    val storagePath: String,
    val httpsDownloadUrl: String = "",
    val sizeBytes: Long,
    val sha256: String,
    val indexedDocumentName: String = "",
    val uploadedAtMs: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("bookId", bookId)
        put("slotIndex", slotIndex)
        put("title", title)
        put("fileName", fileName)
        put("gcsUri", gcsUri)
        put("storagePath", storagePath)
        put("httpsDownloadUrl", httpsDownloadUrl)
        put("sizeBytes", sizeBytes)
        put("sha256", sha256)
        put("indexedDocumentName", indexedDocumentName)
        put("uploadedAtMs", uploadedAtMs)
    }

    companion object {
        fun fromJson(json: JSONObject): UploadedGcsPdfBook = UploadedGcsPdfBook(
            bookId = json.optString("bookId", UUID.randomUUID().toString()),
            slotIndex = json.optInt("slotIndex", 0),
            title = json.optString("title", "PDF Book"),
            fileName = json.optString("fileName", "book.pdf"),
            gcsUri = json.optString("gcsUri", ""),
            storagePath = json.optString("storagePath", ""),
            httpsDownloadUrl = json.optString("httpsDownloadUrl", ""),
            sizeBytes = json.optLong("sizeBytes", 0L),
            sha256 = json.optString("sha256", ""),
            indexedDocumentName = json.optString("indexedDocumentName", ""),
            uploadedAtMs = json.optLong("uploadedAtMs", 0L)
        )
    }
}

/**
 * Identifies how the 5 uploaded GCS PDF books are linked to a Search / RAG instance.
 */
enum class SearchLinkType {
    /** Linked via a secure server-side backend proxy (zero client API keys). */
    SERVER_PROXY_INDEX,

    /** Linked to a Vertex AI Search (Discovery Engine) DataStore via `gcsSource.inputUris`. */
    VERTEX_AI_SEARCH_DATASTORE,

    /** Linked to a Gemini API managed `FileSearchStore` (`fileSearchStores/...`). */
    GEMINI_FILE_SEARCH_STORE,

    /** Linked via Vertex AI in Firebase SDK (`GenerativeBackend.vertexAI`) over `gs://` URIs. */
    VERTEX_AI_GCS_MULTIMODAL_CORPUS
}

/**
 * Configuration for Google Cloud Storage, Vertex AI SDK, and the target Search instance.
 * Public, non-secret identifiers default to the provisioned Firebase/GCP project.
 */
data class SearchInstanceConfig(
    val projectId: String = FileSearchDefaults.DEFAULT_PROJECT_ID,
    val storageBucket: String = FileSearchDefaults.DEFAULT_STORAGE_BUCKET,
    val gcsFolder: String = FileSearchDefaults.DEFAULT_GCS_BOOKS_FOLDER,
    val vertexLocation: String = FileSearchDefaults.DEFAULT_VERTEX_LOCATION,
    val discoveryEngineLocation: String = FileSearchDefaults.DEFAULT_DISCOVERY_ENGINE_LOCATION,
    val vertexSearchDataStoreId: String = "",
    val geminiFileSearchStoreName: String = "",
    val geminiFileSearchDisplayName: String = FileSearchDefaults.DEFAULT_FILE_SEARCH_STORE_DISPLAY_NAME,
    val serverProxyBaseUrl: String = ""
) {
    /**
     * Full Discovery Engine resource path for Vertex AI Search grounding:
     * `projects/{project}/locations/{location}/collections/default_collection/dataStores/{dataStoreId}`
     */
    fun buildVertexDataStoreResourceName(): String {
        val trimmed = vertexSearchDataStoreId.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("projects/")) {
            trimmed
        } else {
            "projects/${projectId.trim()}/locations/${discoveryEngineLocation.trim()}/collections/default_collection/dataStores/$trimmed"
        }
    }

    fun normalizedBucketName(): String {
        return storageBucket.trim().removePrefix("gs://").trimEnd('/')
    }

    fun toJsonString(): String = JSONObject().apply {
        put("projectId", projectId)
        put("storageBucket", storageBucket)
        put("gcsFolder", gcsFolder)
        put("vertexLocation", vertexLocation)
        put("discoveryEngineLocation", discoveryEngineLocation)
        put("vertexSearchDataStoreId", vertexSearchDataStoreId)
        put("geminiFileSearchStoreName", geminiFileSearchStoreName)
        put("geminiFileSearchDisplayName", geminiFileSearchDisplayName)
        put("serverProxyBaseUrl", serverProxyBaseUrl)
    }.toString()

    companion object {
        fun fromJsonString(raw: String?): SearchInstanceConfig {
            if (raw.isNullOrBlank()) return SearchInstanceConfig()
            return try {
                val json = JSONObject(raw)
                SearchInstanceConfig(
                    projectId = json.optString("projectId", FileSearchDefaults.DEFAULT_PROJECT_ID)
                        .ifBlank { FileSearchDefaults.DEFAULT_PROJECT_ID },
                    storageBucket = json.optString("storageBucket", FileSearchDefaults.DEFAULT_STORAGE_BUCKET)
                        .ifBlank { FileSearchDefaults.DEFAULT_STORAGE_BUCKET },
                    gcsFolder = json.optString("gcsFolder", FileSearchDefaults.DEFAULT_GCS_BOOKS_FOLDER)
                        .ifBlank { FileSearchDefaults.DEFAULT_GCS_BOOKS_FOLDER },
                    vertexLocation = json.optString("vertexLocation", FileSearchDefaults.DEFAULT_VERTEX_LOCATION)
                        .ifBlank { FileSearchDefaults.DEFAULT_VERTEX_LOCATION },
                    discoveryEngineLocation = json.optString(
                        "discoveryEngineLocation",
                        FileSearchDefaults.DEFAULT_DISCOVERY_ENGINE_LOCATION
                    ).ifBlank { FileSearchDefaults.DEFAULT_DISCOVERY_ENGINE_LOCATION },
                    vertexSearchDataStoreId = json.optString("vertexSearchDataStoreId", ""),
                    geminiFileSearchStoreName = json.optString("geminiFileSearchStoreName", ""),
                    geminiFileSearchDisplayName = json.optString(
                        "geminiFileSearchDisplayName",
                        FileSearchDefaults.DEFAULT_FILE_SEARCH_STORE_DISPLAY_NAME
                    ).ifBlank { FileSearchDefaults.DEFAULT_FILE_SEARCH_STORE_DISPLAY_NAME },
                    serverProxyBaseUrl = json.optString("serverProxyBaseUrl", "")
                )
            } catch (_: Exception) {
                SearchInstanceConfig()
            }
        }
    }
}

/**
 * Represents an active Search instance linked to the uploaded Cloud Storage PDF books.
 */
data class LinkedSearchInstance(
    val instanceId: String = UUID.randomUUID().toString(),
    val linkType: SearchLinkType,
    val storeResourceName: String,
    val importOperationName: String = "",
    val books: List<UploadedGcsPdfBook>,
    val isReady: Boolean = true,
    val linkedAtMs: Long = System.currentTimeMillis()
) {
    fun toJsonString(): String = JSONObject().apply {
        put("instanceId", instanceId)
        put("linkType", linkType.name)
        put("storeResourceName", storeResourceName)
        put("importOperationName", importOperationName)
        put("isReady", isReady)
        put("linkedAtMs", linkedAtMs)
        val arr = JSONArray()
        books.forEach { arr.put(it.toJson()) }
        put("books", arr)
    }.toString()

    companion object {
        fun fromJsonString(raw: String?): LinkedSearchInstance? {
            if (raw.isNullOrBlank()) return null
            return try {
                val json = JSONObject(raw)
                val typeStr = json.optString("linkType", SearchLinkType.VERTEX_AI_GCS_MULTIMODAL_CORPUS.name)
                val linkType = runCatching { SearchLinkType.valueOf(typeStr) }
                    .getOrDefault(SearchLinkType.VERTEX_AI_GCS_MULTIMODAL_CORPUS)
                val booksArr = json.optJSONArray("books") ?: JSONArray()
                val books = (0 until booksArr.length()).map { idx ->
                    UploadedGcsPdfBook.fromJson(booksArr.getJSONObject(idx))
                }
                LinkedSearchInstance(
                    instanceId = json.optString("instanceId", UUID.randomUUID().toString()),
                    linkType = linkType,
                    storeResourceName = json.optString("storeResourceName", ""),
                    importOperationName = json.optString("importOperationName", ""),
                    books = books,
                    isReady = json.optBoolean("isReady", true),
                    linkedAtMs = json.optLong("linkedAtMs", 0L)
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Observable UI/Service state for the 5-PDF Cloud Storage upload & Search linking pipeline.
 */
sealed interface FileSearchPipelineState {
    data object Idle : FileSearchPipelineState

    data class UploadingToCloudStorage(
        val currentBookNumber: Int,
        val totalBooks: Int,
        val fileName: String,
        val progressFraction: Float
    ) : FileSearchPipelineState

    data class LinkingToSearchInstance(
        val uploadedBooks: List<UploadedGcsPdfBook>,
        val targetDescription: String
    ) : FileSearchPipelineState

    data class Ready(
        val instance: LinkedSearchInstance
    ) : FileSearchPipelineState

    data class Error(
        val message: String,
        val uploadedBooks: List<UploadedGcsPdfBook> = emptyList()
    ) : FileSearchPipelineState
}

/**
 * Passage snippet retrieved from an indexed PDF book via File Search / Vertex AI Search.
 */
data class RetrievedBookChunk(
    val title: String,
    val uri: String,
    val snippet: String
)

/**
 * Grounded response returned by [com.example.service.VertexGeminiFileSearchService].
 */
data class GroundedFileSearchResponse(
    val answerText: String,
    val groundingSources: List<GroundingSource> = emptyList(),
    val retrievedChunks: List<RetrievedBookChunk> = emptyList(),
    val backendUsed: String
)
