package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Top-level book metadata stored in Cloud Firestore `/rag_books/{bookId}`.
 * Raw PDF files are never stored inside the APK; only structured small folders & chunks live in Cloud Firestore.
 */
@IgnoreExtraProperties
data class CloudRagBook(
    @DocumentId val docId: String = "",
    val bookId: String = "",
    val ownerId: String = "",
    @get:PropertyName("isShared")
    val isShared: Boolean = true,
    val title: String = "",
    val author: String = "",
    val eraCategory: String = "",
    val summary: String = "",
    val pageCount: Int = 0,
    val folderCount: Int = 0,
    val chunkCount: Int = 0,
    val keywords: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(authenticatedUid: String): Map<String, Any> = mapOf(
        "bookId" to bookId,
        "ownerId" to authenticatedUid,
        "isShared" to isShared,
        "title" to title.take(300),
        "author" to author.take(200),
        "eraCategory" to eraCategory.ifBlank { "عام" }.take(120),
        "summary" to summary.ifBlank { title }.take(3000),
        "pageCount" to pageCount.coerceIn(0, 20000),
        "folderCount" to folderCount.coerceIn(0, 5000),
        "chunkCount" to chunkCount.coerceIn(0, 50000),
        "keywords" to keywords.map { it.replace('\u001F', ' ').replace(Regex("\\s+"), " ").trim().take(100) }.filter { it.isNotEmpty() }.take(40),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): CloudRagBook {
            val kws = (doc.get("keywords") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            return CloudRagBook(
                docId = doc.id,
                bookId = doc.getString("bookId") ?: doc.id,
                ownerId = doc.getString("ownerId") ?: "",
                isShared = doc.getBoolean("isShared") ?: true,
                title = doc.getString("title") ?: "",
                author = doc.getString("author") ?: "",
                eraCategory = doc.getString("eraCategory") ?: "",
                summary = doc.getString("summary") ?: "",
                pageCount = (doc.getLong("pageCount") ?: 0L).toInt(),
                folderCount = (doc.getLong("folderCount") ?: 0L).toInt(),
                chunkCount = (doc.getLong("chunkCount") ?: 0L).toInt(),
                keywords = kws,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

/**
 * Thematic small folder / chapter stored in Cloud Firestore `/rag_folders/{folderId}`.
 * Enables fast 2-stage retrieval across up to 1,000+ books without downloading irrelevant chunks.
 */
@IgnoreExtraProperties
data class CloudRagFolder(
    @DocumentId val docId: String = "",
    val folderId: String = "",
    val bookId: String = "",
    val bookTitle: String = "",
    val ownerId: String = "",
    @get:PropertyName("isShared")
    val isShared: Boolean = true,
    val folderIndex: Int = 0,
    val folderTitle: String = "",
    val category: String = "",
    val pageRange: String = "",
    val topicSummary: String = "",
    val chunkCount: Int = 0,
    val keywords: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(authenticatedUid: String): Map<String, Any> = mapOf(
        "folderId" to folderId,
        "bookId" to bookId,
        "bookTitle" to bookTitle.take(300),
        "ownerId" to authenticatedUid,
        "isShared" to isShared,
        "folderIndex" to folderIndex.coerceIn(0, 5000),
        "folderTitle" to folderTitle.take(300),
        "category" to category.ifBlank { "فصل" }.take(120),
        "pageRange" to pageRange.take(80),
        "topicSummary" to topicSummary.ifBlank { folderTitle }.take(4000),
        "chunkCount" to chunkCount.coerceIn(0, 5000),
        "keywords" to keywords.map { it.replace('\u001F', ' ').replace(Regex("\\s+"), " ").trim().take(100) }.filter { it.isNotEmpty() }.take(50),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): CloudRagFolder {
            val kws = (doc.get("keywords") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            return CloudRagFolder(
                docId = doc.id,
                folderId = doc.getString("folderId") ?: doc.id,
                bookId = doc.getString("bookId") ?: "",
                bookTitle = doc.getString("bookTitle") ?: "",
                ownerId = doc.getString("ownerId") ?: "",
                isShared = doc.getBoolean("isShared") ?: true,
                folderIndex = (doc.getLong("folderIndex") ?: 0L).toInt(),
                folderTitle = doc.getString("folderTitle") ?: "",
                category = doc.getString("category") ?: "",
                pageRange = doc.getString("pageRange") ?: "",
                topicSummary = doc.getString("topicSummary") ?: "",
                chunkCount = (doc.getLong("chunkCount") ?: 0L).toInt(),
                keywords = kws,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

/**
 * Searchable knowledge chunk with exact book, folder, and page citation in `/rag_chunks/{chunkId}`.
 */
@IgnoreExtraProperties
data class CloudRagChunk(
    @DocumentId val docId: String = "",
    val chunkId: String = "",
    val bookId: String = "",
    val bookTitle: String = "",
    val folderId: String = "",
    val folderTitle: String = "",
    val ownerId: String = "",
    @get:PropertyName("isShared")
    val isShared: Boolean = true,
    val chunkIndex: Int = 0,
    val pageRange: String = "",
    val title: String = "",
    val content: String = "",
    val keywords: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(authenticatedUid: String): Map<String, Any> = mapOf(
        "chunkId" to chunkId,
        "bookId" to bookId,
        "bookTitle" to bookTitle.take(300),
        "folderId" to folderId,
        "folderTitle" to folderTitle.take(300),
        "ownerId" to authenticatedUid,
        "isShared" to isShared,
        "chunkIndex" to chunkIndex.coerceIn(0, 50000),
        "pageRange" to pageRange.take(80),
        "title" to title.ifBlank { folderTitle }.take(300),
        "content" to content.ifBlank { title }.take(25000),
        "keywords" to keywords.map { it.replace('\u001F', ' ').replace(Regex("\\s+"), " ").trim().take(100) }.filter { it.isNotEmpty() }.take(40),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): CloudRagChunk {
            val kws = (doc.get("keywords") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            return CloudRagChunk(
                docId = doc.id,
                chunkId = doc.getString("chunkId") ?: doc.id,
                bookId = doc.getString("bookId") ?: "",
                bookTitle = doc.getString("bookTitle") ?: "",
                folderId = doc.getString("folderId") ?: "",
                folderTitle = doc.getString("folderTitle") ?: "",
                ownerId = doc.getString("ownerId") ?: "",
                isShared = doc.getBoolean("isShared") ?: true,
                chunkIndex = (doc.getLong("chunkIndex") ?: 0L).toInt(),
                pageRange = doc.getString("pageRange") ?: "",
                title = doc.getString("title") ?: "",
                content = doc.getString("content") ?: "",
                keywords = kws,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

/**
 * Complete structured bundle for a single book (metadata + small folders + chunks).
 */
data class SeedRagBookBundle(
    val book: CloudRagBook,
    val folders: List<CloudRagFolder>,
    val chunks: List<CloudRagChunk>
)

/**
 * Real-time UI state for Cloud Firebase RAG synchronization and PDF-to-RAG conversion.
 */
sealed interface CloudRagSyncState {
    data object Idle : CloudRagSyncState
    data class Syncing(
        val progressMessage: String,
        val currentStep: Int = 0,
        val totalSteps: Int = 1
    ) : CloudRagSyncState

    data class Ready(
        val syncedBooksCount: Int,
        val syncedFoldersCount: Int,
        val syncedChunksCount: Int,
        val isCloudConnected: Boolean,
        val statusMessage: String
    ) : CloudRagSyncState

    data class Error(val message: String) : CloudRagSyncState
}

/**
 * Retrieval result returned when matching a user prompt against the Cloud Firebase RAG corpus.
 */
data class RetrievedRagPassage(
    val chunk: CloudRagChunk,
    val score: Double
) {
    fun toCitationTitle(): String = "${chunk.bookTitle} • ${chunk.folderTitle} (${chunk.pageRange})"
    fun toCitationUri(): String = "firestore://rag_books/${chunk.bookId}/folders/${chunk.folderId}#${chunk.chunkId}"
}
