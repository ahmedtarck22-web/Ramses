package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.CloudRagBook
import com.example.data.model.CloudRagChunk
import com.example.data.model.CloudRagFolder
import com.example.data.model.SeedRagBookBundle
import com.example.data.repository.FirestoreRagRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Automated PDF -> Small Folders -> Cited RAG Chunks Cloud Converter.
 * Allows the user to select 1 or dozens/hundreds of PDF books (scaling to 1,000+ books)
 * directly from their phone without touching Google Cloud Console and with 0 KB added to APK size.
 *
 * Pipeline:
 * 1. Renders PDF pages locally using Android's native `PdfRenderer` into crisp bitmaps (handling any Arabic font/scan).
 * 2. Uses `Firebase.ai.generativeModel("gemini-3.8-flash")` with strict `Schema.obj` JSON output to organize pages
 *    into small thematic folders + clean page-cited RAG chunks.
 * 3. Uploads the resulting `CloudRagBook`, `CloudRagFolder`s, and `CloudRagChunk`s straight to Cloud Firestore,
 *    then deletes the temporary cache PDF immediately.
 */
class PdfToCloudRagConverter(
    private val appContext: Context,
    private val ragRepository: FirestoreRagRepository
) {
    companion object {
        private const val TAG = "PdfToCloudRagConverter"
        private const val PAGES_PER_BATCH = 6
        private const val MAX_RENDER_WIDTH_PX = 1080
    }

    private val batchExtractionSchema = Schema.obj(
        properties = mapOf(
            "detectedBookAuthor" to Schema.string(description = "Detected author name in Arabic if visible, or empty string"),
            "detectedEraCategory" to Schema.string(description = "Historical era or academic subject in Arabic"),
            "folderTitle" to Schema.string(description = "Concise Arabic title for this thematic small folder / chapter"),
            "topicSummary" to Schema.string(description = "2-3 sentence Arabic summary of the topics in these pages"),
            "keywords" to Schema.array(
                items = Schema.string(),
                description = "Up to 10 key historical names, places, or concepts in Arabic"
            ),
            "chunks" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "pageRange" to Schema.string(description = "Exact page citation in Arabic such as ص 1 - 3"),
                        "title" to Schema.string(description = "Clear Arabic sub-heading for this knowledge chunk"),
                        "content" to Schema.string(description = "Comprehensive, clean, well-formatted Arabic factual content extracted from these pages"),
                        "keywords" to Schema.array(items = Schema.string())
                    )
                ),
                description = "2 to 4 detailed, self-contained RAG knowledge chunks covering all facts on these pages"
            )
        )
    )

    private val structuredExtractorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = batchExtractionSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun convertAndUploadPdfUris(
        uris: List<Uri>,
        onProgress: (String, Int, Int) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext Result.success(0)
        var convertedCount = 0
        try {
            for ((bookIdx, uri) in uris.withIndex()) {
                val displayName = resolveDisplayName(uri)
                val cleanTitle = displayName.substringBeforeLast(".").replace("_", " ").trim()
                    .ifBlank { "كتاب مرفوع رقم ${bookIdx + 1}" }

                onProgress(
                    "جارٍ قراءة وتقسيم كتاب «$cleanTitle» (${bookIdx + 1}/${uris.size})...",
                    bookIdx + 1,
                    uris.size
                )

                val bundle = convertSinglePdfUri(
                    uri = uri,
                    bookTitle = cleanTitle,
                    onBatchProgress = { batchNum, totalBatches ->
                        onProgress(
                            "تحويل «$cleanTitle» إلى مجلدات صغيرة ونسخ RAG: دفعة $batchNum من $totalBatches...",
                            bookIdx + 1,
                            uris.size
                        )
                    }
                )

                onProgress(
                    "جارٍ رفع مجلدات ونسخ «$cleanTitle» إلى سحابة Firebase...",
                    bookIdx + 1,
                    uris.size
                )
                ragRepository.uploadBookBundle(bundle).getOrThrow()
                convertedCount++
            }
            Result.success(convertedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "PDF to Cloud Firebase RAG conversion failed", e)
            Result.failure(e)
        }
    }

    private suspend fun convertSinglePdfUri(
        uri: Uri,
        bookTitle: String,
        onBatchProgress: (Int, Int) -> Unit
    ): SeedRagBookBundle {
        val tempPdf = File(appContext.cacheDir, "tmp_rag_${UUID.randomUUID()}.pdf")
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempPdf).use { output -> input.copyTo(output) }
            } ?: throw IllegalArgumentException("تعذر فتح ملف الـ PDF المختار.")

            val bookSlug = "book_usr_" + UUID.randomUUID().toString().replace("-", "").take(12)
            val folders = mutableListOf<CloudRagFolder>()
            val chunks = mutableListOf<CloudRagChunk>()
            val bookKeywords = linkedSetOf<String>()
            var detectedAuthor = ""
            var detectedCategory = "كتب مرفوعة إلى السحابة"
            var totalPageCount = 0

            val pfd = android.os.ParcelFileDescriptor.open(
                tempPdf,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    totalPageCount = renderer.pageCount
                    if (totalPageCount <= 0) {
                        throw IllegalArgumentException("ملف الـ PDF لا يحتوي على صفحات قابلة للقراءة.")
                    }

                    // Process sequential 6-page folders up to 96 pages, or sample evenly across larger books
                    val isSampled = totalPageCount > 96
                    val pageIndices = if (!isSampled) {
                        (0 until totalPageCount).toList()
                    } else {
                        val step = (totalPageCount.toDouble() / 96.0).coerceAtLeast(1.0)
                        (0 until 96).map { (it * step).toInt().coerceAtMost(totalPageCount - 1) }.distinct()
                    }

                    val pageBatches = pageIndices.chunked(PAGES_PER_BATCH)
                    var globalChunkIdx = 1

                    for ((bIdx, batchPages) in pageBatches.withIndex()) {
                        onBatchProgress(bIdx + 1, pageBatches.size)
                        val firstPageNum = batchPages.first() + 1
                        val lastPageNum = batchPages.last() + 1
                        val isContiguous = batchPages.size == (lastPageNum - firstPageNum + 1)
                        val batchRangeLabel = when {
                            firstPageNum == lastPageNum -> "ص $firstPageNum"
                            isContiguous -> "ص $firstPageNum - $lastPageNum"
                            else -> "ص " + batchPages.joinToString("، ") { (it + 1).toString() }
                        }.take(75)

                        val bitmaps = batchPages.map { pageIndex ->
                            renderer.openPage(pageIndex).use { page ->
                                val scale = (MAX_RENDER_WIDTH_PX.toFloat() / page.width.coerceAtLeast(1)).coerceAtMost(1.8f)
                                val w = (page.width * scale).toInt().coerceAtLeast(1)
                                val h = (page.height * scale).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }

                        try {
                            var parsed: JSONObject? = null
                            var attempt = 0
                            while (attempt < 2 && parsed == null) {
                                attempt++
                                try {
                                    val response = withTimeout(60_000L) {
                                        structuredExtractorModel.generateContent(
                                            content {
                                                for ((idx, bmp) in bitmaps.withIndex()) {
                                                    text("صفحة رقم ${batchPages[idx] + 1} من كتاب «$bookTitle»:")
                                                    image(bmp)
                                                }
                                                text(
                                                    """
                                                    حلل هذه الصفحات ($batchRangeLabel) من كتاب «$bookTitle» بدقة علمية وتاريخية كاملة،
                                                    وحوّلها إلى مجلد موضوعي صغير (Small Folder) يضم فقرات معرفية (RAG Chunks) مفصلة باللغة العربية الفصحى السليمة،
                                                    مع ذكر أرقام الصفحات الدقيقة الموضحة أعلاه وأهم الكلمات المفتاحية والأسماء والتواريخ دون حذف المعلومات الجوهرية.
                                                    """.trimIndent()
                                                )
                                            }
                                        )
                                    }
                                    val rawJson = response.text ?: throw IllegalStateException("لم يُرجع النموذج بيانات JSON.")
                                    parsed = JSONObject(rawJson)
                                } catch (e: CancellationException) {
                                    if (attempt >= 2) Log.w(TAG, "Batch ${bIdx + 1} timed out; continuing remaining batches", e)
                                } catch (e: Exception) {
                                    if (attempt >= 2) Log.w(TAG, "Batch ${bIdx + 1} extraction failed; continuing remaining batches", e)
                                }
                            }

                            if (parsed == null) continue

                            val authorCand = parsed.optString("detectedBookAuthor", "").trim().take(190)
                            if (detectedAuthor.isBlank() && authorCand.isNotBlank()) {
                                detectedAuthor = authorCand
                            }
                            val catCand = parsed.optString("detectedEraCategory", "").trim().take(110)
                            if (bIdx == 0 && catCand.isNotBlank()) {
                                detectedCategory = catCand
                            }

                            val folderId = "${bookSlug}_f${(bIdx + 1).toString().padStart(2, '0')}"
                            val folderTitle = parsed.optString("folderTitle", "المجلد ${bIdx + 1} ($batchRangeLabel)")
                                .trim().ifBlank { "المجلد ${bIdx + 1}" }.take(290)
                            val topicSummary = parsed.optString("topicSummary", folderTitle)
                                .trim().ifBlank { folderTitle }.take(3900)
                            val folderKws = parsed.optJSONArray("keywords")?.let { arr ->
                                (0 until arr.length()).mapNotNull {
                                    arr.optString(it)?.trim()?.take(95)?.takeIf { s -> s.isNotBlank() }
                                }
                            } ?: emptyList()
                            bookKeywords.addAll(folderKws)

                            val safeBookTitle = bookTitle.take(290)
                            val chunksJson = parsed.optJSONArray("chunks")
                            var folderChunkCount = 0
                            if (chunksJson != null && chunksJson.length() > 0) {
                                for (c in 0 until chunksJson.length()) {
                                    val cObj = chunksJson.getJSONObject(c)
                                    val cBody = cObj.optString("content", "").trim().take(24000)
                                    if (cBody.isBlank()) continue
                                    val cTitle = cObj.optString("title", folderTitle).trim().ifBlank { folderTitle }.take(290)
                                    val cPages = cObj.optString("pageRange", batchRangeLabel).trim().ifBlank { batchRangeLabel }.take(75)
                                    val cKws = cObj.optJSONArray("keywords")?.let { arr ->
                                        (0 until arr.length()).mapNotNull {
                                            arr.optString(it)?.trim()?.take(95)?.takeIf { s -> s.isNotBlank() }
                                        }
                                    } ?: folderKws

                                    val chunkId = "${folderId}_c${globalChunkIdx.toString().padStart(3, '0')}"
                                    chunks.add(
                                        CloudRagChunk(
                                            docId = chunkId,
                                            chunkId = chunkId,
                                            bookId = bookSlug,
                                            bookTitle = safeBookTitle,
                                            folderId = folderId,
                                            folderTitle = folderTitle,
                                            isShared = true,
                                            chunkIndex = globalChunkIdx,
                                            pageRange = cPages,
                                            title = cTitle,
                                            content = cBody,
                                            keywords = cKws.take(25)
                                        )
                                    )
                                    globalChunkIdx++
                                    folderChunkCount++
                                }
                            }

                            if (folderChunkCount > 0) {
                                folders.add(
                                    CloudRagFolder(
                                        docId = folderId,
                                        folderId = folderId,
                                        bookId = bookSlug,
                                        bookTitle = safeBookTitle,
                                        isShared = true,
                                        folderIndex = folders.size + 1,
                                        folderTitle = folderTitle,
                                        category = detectedCategory.take(110),
                                        pageRange = batchRangeLabel,
                                        topicSummary = topicSummary,
                                        chunkCount = folderChunkCount,
                                        keywords = folderKws.take(30)
                                    )
                                )
                            }
                        } finally {
                            bitmaps.forEach { it.recycle() }
                        }
                    }
                }
            }

            if (folders.isEmpty() || chunks.isEmpty()) {
                throw IllegalStateException("لم يتم استخراج محتوى نصي من ملف الـ PDF.")
            }

            val summaryText = folders.take(4).joinToString(" • ") { it.folderTitle }
            val book = CloudRagBook(
                docId = bookSlug,
                bookId = bookSlug,
                isShared = true,
                title = bookTitle.take(290),
                author = detectedAuthor.take(190),
                eraCategory = detectedCategory.take(110),
                summary = "كتاب محوّل تلقائياً إلى ${folders.size} مجلدات صغيرة و${chunks.size} فقرة RAG: $summaryText".take(2900),
                pageCount = totalPageCount,
                folderCount = folders.size,
                chunkCount = chunks.size,
                keywords = bookKeywords.map { it.take(95) }.take(35)
            )
            return SeedRagBookBundle(book = book, folders = folders, chunks = chunks)
        } finally {
            if (tempPdf.exists()) {
                tempPdf.delete()
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        return try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment ?: "كتاب_PDF"
        } catch (_: Exception) {
            "كتاب_PDF"
        }
    }
}
