package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "rag_documents")
data class RagDocument(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val category: String = "عام",
    val isEnabled: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val gcsUri: String = "",
    val searchStoreName: String = "",
    val sourceType: String = "TEXT",
    val sizeBytes: Long = 0L
) {
    val isCloudPdfBook: Boolean
        get() = sourceType == "PDF_GCS" || gcsUri.startsWith("gs://")
}
