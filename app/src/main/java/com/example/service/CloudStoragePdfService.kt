package com.example.service

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.FileSearchDefaults
import com.example.data.model.PdfBookUploadRequest
import com.example.data.model.SearchInstanceConfig
import com.example.data.model.UploadedGcsPdfBook
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/**
 * Abstraction over Google Cloud Storage object uploads so production code uses
 * [FirebaseStorage] (`gs://gen-lang-client-0341227885.firebasestorage.app`) while
 * unit/Robolectric tests can verify upload behavior deterministically.
 */
interface CloudStorageGateway {
    suspend fun uploadPdfBytes(
        bucketName: String,
        storagePath: String,
        pdfBytes: ByteArray,
        customMetadata: Map<String, String>,
        onProgress: (Float) -> Unit
    ): String
}

/**
 * Production implementation backed by Cloud Storage for Firebase (`com.google.firebase:firebase-storage`).
 */
class FirebaseCloudStorageGateway : CloudStorageGateway {

    companion object {
        private const val TAG = "FirebaseGcsGateway"
    }

    override suspend fun uploadPdfBytes(
        bucketName: String,
        storagePath: String,
        pdfBytes: ByteArray,
        customMetadata: Map<String, String>,
        onProgress: (Float) -> Unit
    ): String {
        val cleanBucket = bucketName.removePrefix("gs://").trimEnd('/')
        val storage = FirebaseStorage.getInstance("gs://$cleanBucket")
        val targetRef = storage.reference.child(storagePath.trimStart('/'))

        val metadataBuilder = StorageMetadata.Builder()
            .setContentType(FileSearchDefaults.PDF_MIME_TYPE)
        for ((key, value) in customMetadata) {
            metadataBuilder.setCustomMetadata(key, value)
        }

        val uploadTask = targetRef.putBytes(pdfBytes, metadataBuilder.build())
        uploadTask.addOnProgressListener { snapshot ->
            val total = snapshot.totalByteCount
            if (total > 0L) {
                val fraction = (snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                onProgress(fraction)
            }
        }

        uploadTask.await()
        onProgress(1f)

        return try {
            targetRef.downloadUrl.await().toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Uploaded gs://$cleanBucket/$storagePath (HTTPS downloadUrl skipped: ${e.message})")
            ""
        }
    }
}

/**
 * Service responsible for validating, hashing, and uploading the 5 PDF books
 * to Google Cloud Storage (`gs://...`) so they can be linked to Vertex AI / Gemini File Search.
 */
class CloudStoragePdfService(
    private val storageGateway: CloudStorageGateway = FirebaseCloudStorageGateway(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "CloudStoragePdfService"
        private val PDF_MAGIC_HEADER = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // "%PDF-"
    }

    /**
     * Carries both the uploaded GCS metadata and the in-memory PDF bytes so downstream
     * File Search store ingestion does not need to re-read from disk or re-download from GCS.
     */
    data class UploadedPdfPayload(
        val book: UploadedGcsPdfBook,
        val bytes: ByteArray
    )

    /**
     * Resolves Android document picker [Uri]s into [PdfBookUploadRequest] items (up to 5 PDFs).
     */
    fun buildUploadRequestsFromUris(
        context: Context,
        uris: List<Uri>
    ): List<PdfBookUploadRequest> {
        return uris.take(FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT).mapIndexed { index, uri ->
            val resolvedName = queryDisplayName(context, uri) ?: "ramses_book_${index + 1}.pdf"
            val normalizedFileName = if (resolvedName.lowercase(Locale.ROOT).endsWith(".pdf")) {
                resolvedName
            } else {
                "$resolvedName.pdf"
            }
            val cleanTitle = normalizedFileName.substringBeforeLast(".").ifBlank {
                "Book ${index + 1}"
            }
            PdfBookUploadRequest(
                slotIndex = index,
                title = cleanTitle,
                fileName = normalizedFileName,
                uri = uri
            )
        }
    }

    /**
     * Strictly uploads the 5 test PDF books to Google Cloud Storage (`gs://<bucket>/<folder>/...`).
     * Throws [IllegalArgumentException] if [requests] does not contain exactly 5 PDF files.
     */
    suspend fun uploadFivePdfBooks(
        context: Context,
        requests: List<PdfBookUploadRequest>,
        config: SearchInstanceConfig = SearchInstanceConfig(),
        onProgress: (bookNumber: Int, totalBooks: Int, fileName: String, fraction: Float) -> Unit = { _, _, _, _ -> }
    ): List<UploadedPdfPayload> {
        require(requests.size == FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT) {
            "Expected exactly ${FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT} PDF files for the shared knowledge base, but received ${requests.size}."
        }
        return uploadPdfBatch(
            context = context,
            requests = requests,
            config = config,
            enforceExactFive = true,
            onProgress = onProgress
        )
    }

    /**
     * Uploads a batch of 1..5 PDF files to Google Cloud Storage and returns their `gs://` URIs.
     */
    suspend fun uploadPdfBatch(
        context: Context,
        requests: List<PdfBookUploadRequest>,
        config: SearchInstanceConfig = SearchInstanceConfig(),
        enforceExactFive: Boolean = false,
        onProgress: (bookNumber: Int, totalBooks: Int, fileName: String, fraction: Float) -> Unit = { _, _, _, _ -> }
    ): List<UploadedPdfPayload> = withContext(ioDispatcher) {
        if (enforceExactFive) {
            require(requests.size == FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT) {
                "Exactly ${FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT} PDF books are required, got ${requests.size}."
            }
        } else {
            require(requests.isNotEmpty()) { "At least 1 PDF file must be provided for upload." }
            require(requests.size <= FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT) {
                "Maximum ${FileSearchDefaults.REQUIRED_PDF_BOOK_COUNT} PDF files allowed per Search batch, got ${requests.size}."
            }
        }

        val bucket = config.normalizedBucketName()
        val folder = config.gcsFolder.trim('/')
        val results = mutableListOf<UploadedPdfPayload>()

        for ((idx, request) in requests.withIndex()) {
            val bookNumber = idx + 1
            onProgress(bookNumber, requests.size, request.fileName, 0f)

            val pdfBytes = readRequestBytes(context, request)
            validatePdfBytes(pdfBytes, request.fileName)

            val sha256 = sha256Hex(pdfBytes)
            val safeSlug = sanitizeSlug(request.title.ifBlank { request.fileName })
            val storagePath = "$folder/book_${bookNumber}_${safeSlug}_${sha256.take(8)}.pdf"
            val gcsUri = "gs://$bucket/$storagePath"

            val metadataMap = mapOf(
                "bookTitle" to request.title,
                "originalFileName" to request.fileName,
                "slotIndex" to request.slotIndex.toString(),
                "sha256" to sha256,
                "corpus" to "ask_ramses_shared_rag"
            )

            Log.d(TAG, "Uploading PDF ($bookNumber/${requests.size}) '${request.fileName}' -> $gcsUri")
            val httpsUrl = storageGateway.uploadPdfBytes(
                bucketName = bucket,
                storagePath = storagePath,
                pdfBytes = pdfBytes,
                customMetadata = metadataMap,
                onProgress = { fraction ->
                    onProgress(bookNumber, requests.size, request.fileName, fraction)
                }
            )

            val uploadedBook = UploadedGcsPdfBook(
                bookId = "pdf_${sha256.take(16)}",
                slotIndex = request.slotIndex,
                title = request.title,
                fileName = request.fileName,
                gcsUri = gcsUri,
                storagePath = storagePath,
                httpsDownloadUrl = httpsUrl,
                sizeBytes = pdfBytes.size.toLong(),
                sha256 = sha256
            )
            results.add(UploadedPdfPayload(book = uploadedBook, bytes = pdfBytes))
        }

        results
    }

    private fun readRequestBytes(context: Context, request: PdfBookUploadRequest): ByteArray {
        request.rawBytes?.let { return it }
        request.localFile?.let { file ->
            if (!file.exists() || !file.canRead()) {
                throw IOException("Cannot read local PDF file: ${file.absolutePath}")
            }
            if (file.length() > FileSearchDefaults.MAX_PDF_SIZE_BYTES) {
                throw IllegalArgumentException("PDF '${request.fileName}' exceeds 50 MB limit.")
            }
            return file.readBytes()
        }
        request.uri?.let { uri ->
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open InputStream for PDF URI: $uri")
            return stream.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var totalRead = 0L
                var read: Int
                while (input.read(chunk).also { read = it } != -1) {
                    totalRead += read
                    if (totalRead > FileSearchDefaults.MAX_PDF_SIZE_BYTES) {
                        throw IllegalArgumentException("PDF '${request.fileName}' exceeds maximum 50 MB size.")
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
        }
        throw IllegalArgumentException("PdfBookUploadRequest '${request.fileName}' has no uri, localFile, or rawBytes.")
    }

    internal fun validatePdfBytes(bytes: ByteArray, fileName: String) {
        if (bytes.size < PDF_MAGIC_HEADER.size) {
            throw IllegalArgumentException("File '$fileName' is empty or too small to be a valid PDF.")
        }
        // Per PDF 1.7 spec, %PDF- header must appear within the first 1024 bytes
        val searchLimit = minOf(bytes.size - PDF_MAGIC_HEADER.size, 1024)
        var foundHeader = false
        for (offset in 0..searchLimit) {
            var match = true
            for (j in PDF_MAGIC_HEADER.indices) {
                if (bytes[offset + j] != PDF_MAGIC_HEADER[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                foundHeader = true
                break
            }
        }
        if (!foundHeader) {
            throw IllegalArgumentException("File '$fileName' is not a valid PDF document (missing %PDF- signature).")
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    cursor.getString(idx)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    private fun sanitizeSlug(input: String): String {
        val cleaned = input.substringBeforeLast(".")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\u0600-\\u06FF]+"), "_")
            .trim('_')
        return cleaned.take(36).ifBlank { "book" }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }
}
