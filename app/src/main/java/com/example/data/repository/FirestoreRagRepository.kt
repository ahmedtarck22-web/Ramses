package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.CloudRagBook
import com.example.data.model.CloudRagChunk
import com.example.data.model.CloudRagFolder
import com.example.data.model.RetrievedRagPassage
import com.example.data.model.SeedRagBookBundle
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

class FirestoreRagRepository(
    private val db: FirebaseFirestore,
    private val appContext: Context? = null,
    private val authProvider: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid }
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        appContext = context.applicationContext
    )

    companion object {
        private const val TAG = "FirestoreRagRepo"
        const val SEED_ASSET_PATH = "rag/ramses_5books_rag.pack"
        private const val MAX_FOLDER_BATCH_OPS = 15
        private const val MAX_CHUNK_BATCH_OPS = 9
        private const val MAX_DELETE_BATCH_OPS = 380
        private val SEED_LOCK = Any()

        private val ARABIC_STOPWORDS = setOf(
            "من", "في", "فى", "على", "علي", "الى", "إلى", "عن", "مع", "هو", "هي",
            "هم", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي", "الذين", "ما", "ماذا",
            "كيف", "متى", "أين", "اين", "هل", "كان", "كانت", "بين", "كل", "بعد",
            "قبل", "عند", "او", "أو", "ثم", "يا", "حدثني", "اشرح", "اذكر", "قولي",
            "عايز", "اعرف", "the", "of", "and", "in", "to", "what", "who", "how", "tell", "me", "about"
        )

        private val DIACRITICS_REGEX = Regex("[\u064B-\u065F\u0670\u0640]")
        private val ALEF_REGEX = Regex("[أإآٱ]")
        private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{N}\\s]")
        private val WHITESPACE_REGEX = Regex("\\s+")

        private data class IndexedFolder(
            val folder: CloudRagFolder,
            val titleNorm: String,
            val summaryNorm: String,
            val bookNorm: String,
            val kwNorm: List<String>,
            val stems: Set<String>,
            val trigrams: Set<String>
        )

        private data class IndexedChunk(
            val chunk: CloudRagChunk,
            val titleNorm: String,
            val contentNorm: String,
            val kwNorm: List<String>,
            val stems: Set<String>
        )

        @Volatile
        private var cachedSeedBundles: List<SeedRagBookBundle>? = null

        @Volatile
        private var cachedSeedFolderIndex: Map<String, IndexedFolder>? = null

        @Volatile
        private var cachedSeedChunkIndexByFolder: Map<String, List<IndexedChunk>>? = null

        private fun decodePossiblyGzippedUtf8(rawStream: InputStream): String {
            val buffered = BufferedInputStream(rawStream)
            buffered.mark(2)
            val b0 = buffered.read()
            val b1 = buffered.read()
            buffered.reset()
            val isGzip = (b0 == 0x1f && b1 == 0x8b)
            val effectiveStream: InputStream = if (isGzip) GZIPInputStream(buffered) else buffered
            return effectiveStream.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        }

        private fun indexFolder(folder: CloudRagFolder): IndexedFolder {
            val titleNorm = normalizeSearchText(folder.folderTitle)
            val summaryNorm = normalizeSearchText(folder.topicSummary)
            val bookNorm = normalizeSearchText("${folder.bookTitle} ${folder.category}")
            val kwNorm = folder.keywords.map { normalizeSearchText(it) }
            val combined = "$titleNorm $summaryNorm ${kwNorm.joinToString(" ")} $bookNorm"
            return IndexedFolder(
                folder = folder,
                titleNorm = titleNorm,
                summaryNorm = summaryNorm,
                bookNorm = bookNorm,
                kwNorm = kwNorm,
                stems = extractSearchStems(combined),
                trigrams = buildCharTrigrams("$titleNorm ${kwNorm.joinToString(" ")}")
            )
        }

        private fun indexChunk(chunk: CloudRagChunk): IndexedChunk {
            val titleNorm = normalizeSearchText(chunk.title)
            val contentNorm = normalizeSearchText(chunk.content)
            val kwNorm = chunk.keywords.map { normalizeSearchText(it) }
            return IndexedChunk(
                chunk = chunk,
                titleNorm = titleNorm,
                contentNorm = contentNorm,
                kwNorm = kwNorm,
                stems = extractSearchStems("$titleNorm ${kwNorm.joinToString(" ")} $contentNorm")
            )
        }

        fun loadBundledFiveBooks(context: Context?): List<SeedRagBookBundle> {
            cachedSeedBundles?.let { return it }
            val ctx = context ?: return emptyList()
            return synchronized(SEED_LOCK) {
                cachedSeedBundles?.let { return@synchronized it }
                try {
                    val jsonText = decodePossiblyGzippedUtf8(ctx.assets.open(SEED_ASSET_PATH))
                    val root = JSONObject(jsonText)
                    val booksArr = root.getJSONArray("books")
                    val parsed = mutableListOf<SeedRagBookBundle>()

                    for (i in 0 until booksArr.length()) {
                        val itemObj = booksArr.getJSONObject(i)
                        val bObj = itemObj.getJSONObject("book")
                        val bookKws = bObj.optJSONArray("keywords")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList()

                        val book = CloudRagBook(
                            docId = bObj.getString("bookId"),
                            bookId = bObj.getString("bookId"),
                            isShared = true,
                            title = bObj.getString("title"),
                            author = bObj.optString("author", ""),
                            eraCategory = bObj.optString("eraCategory", "تاريخ مصر"),
                            summary = bObj.optString("summary", ""),
                            pageCount = bObj.optInt("pageCount", 0),
                            folderCount = bObj.optInt("folderCount", 0),
                            chunkCount = bObj.optInt("chunkCount", 0),
                            keywords = bookKws
                        )

                        val foldersArr = itemObj.getJSONArray("folders")
                        val folders = (0 until foldersArr.length()).map { fIdx ->
                            val fObj = foldersArr.getJSONObject(fIdx)
                            val fKws = fObj.optJSONArray("keywords")?.let { arr ->
                                (0 until arr.length()).map { arr.getString(it) }
                            } ?: emptyList()
                            CloudRagFolder(
                                docId = fObj.getString("folderId"),
                                folderId = fObj.getString("folderId"),
                                bookId = fObj.getString("bookId"),
                                bookTitle = fObj.getString("bookTitle"),
                                isShared = true,
                                folderIndex = fObj.optInt("folderIndex", fIdx + 1),
                                folderTitle = fObj.getString("folderTitle"),
                                category = fObj.optString("category", ""),
                                pageRange = fObj.optString("pageRange", ""),
                                topicSummary = fObj.optString("topicSummary", ""),
                                chunkCount = fObj.optInt("chunkCount", 0),
                                keywords = fKws
                            )
                        }

                        val chunksArr = itemObj.getJSONArray("chunks")
                        val chunks = (0 until chunksArr.length()).map { cIdx ->
                            val cObj = chunksArr.getJSONObject(cIdx)
                            val cKws = cObj.optJSONArray("keywords")?.let { arr ->
                                (0 until arr.length()).map { arr.getString(it) }
                            } ?: emptyList()
                            CloudRagChunk(
                                docId = cObj.getString("chunkId"),
                                chunkId = cObj.getString("chunkId"),
                                bookId = cObj.getString("bookId"),
                                bookTitle = cObj.getString("bookTitle"),
                                folderId = cObj.getString("folderId"),
                                folderTitle = cObj.getString("folderTitle"),
                                isShared = true,
                                chunkIndex = cObj.optInt("chunkIndex", cIdx + 1),
                                pageRange = cObj.optString("pageRange", ""),
                                title = cObj.getString("title"),
                                content = cObj.getString("content"),
                                keywords = cKws
                            )
                        }

                        parsed.add(SeedRagBookBundle(book = book, folders = folders, chunks = chunks))
                    }
                    cachedSeedFolderIndex = parsed.flatMap { it.folders }
                        .associate { it.folderId to indexFolder(it) }
                    cachedSeedChunkIndexByFolder = parsed.flatMap { it.chunks }
                        .groupBy({ it.folderId }, { indexChunk(it) })
                    cachedSeedBundles = parsed
                    parsed
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read compressed 5-book RAG seed pack", e)
                    emptyList()
                }
            }
        }

        fun normalizeSearchText(input: String): String {
            if (input.isBlank()) return ""
            return input
                .lowercase()
                .replace(DIACRITICS_REGEX, "")
                .replace(ALEF_REGEX, "ا")
                .replace('ة', 'ه')
                .replace('ى', 'ي')
                .replace('ؤ', 'و')
                .replace('ئ', 'ي')
                .replace(NON_ALNUM_REGEX, " ")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
        }

        fun stemArabicToken(rawToken: String): String {
            var t = rawToken
            when {
                t.length >= 5 && (t.startsWith("وال") || t.startsWith("بال") || t.startsWith("كال") || t.startsWith("فال")) ->
                    t = t.drop(3)
                t.length >= 4 && (t.startsWith("ال") || t.startsWith("لل")) ->
                    t = t.drop(2)
                t.length >= 5 && (t.startsWith("و") || t.startsWith("ف") || t.startsWith("ب") || t.startsWith("ل")) ->
                    t = t.drop(1)
            }
            when {
                t.length >= 6 && (t.endsWith("ات") || t.endsWith("ون") || t.endsWith("ين") || t.endsWith("ها") || t.endsWith("هم") || t.endsWith("كم") || t.endsWith("هن") || t.endsWith("ان")) ->
                    t = t.dropLast(2)
                t.length >= 5 && (t.endsWith("يه") || t.endsWith("ها")) ->
                    t = t.dropLast(2)
                t.length >= 5 && (t.endsWith("ه") || t.endsWith("ي")) ->
                    t = t.dropLast(1)
            }
            return t
        }

        fun extractSearchTokens(input: String): List<String> {
            val normalized = normalizeSearchText(input)
            if (normalized.isBlank()) return emptyList()
            return normalized.split(" ")
                .map { token ->
                    when {
                        token.length >= 5 && (token.startsWith("وال") || token.startsWith("بال") || token.startsWith("كال") || token.startsWith("فال")) ->
                            token.drop(3)
                        token.length >= 4 && (token.startsWith("ال") || token.startsWith("لل")) ->
                            token.drop(2)
                        else -> token
                    }
                }
                .filter { it.length >= 2 && it !in ARABIC_STOPWORDS }
                .distinct()
        }

        fun extractSearchStems(input: String): Set<String> {
            return extractSearchTokens(input)
                .map { stemArabicToken(it) }
                .filter { it.length >= 2 && it !in ARABIC_STOPWORDS }
                .toSet()
        }

        private fun buildCharTrigrams(normalized: String): Set<String> {
            val compact = normalized.replace(" ", "")
            if (compact.length < 3) return if (compact.isEmpty()) emptySet() else setOf(compact)
            val result = HashSet<String>(compact.length)
            for (i in 0..compact.length - 3) {
                result.add(compact.substring(i, i + 3))
            }
            return result
        }

        private fun diceSimilarity(a: Set<String>, b: Set<String>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            var overlap = 0
            val smaller = if (a.size <= b.size) a else b
            val larger = if (a.size <= b.size) b else a
            for (gram in smaller) {
                if (gram in larger) overlap++
            }
            return (2.0 * overlap) / (a.size + b.size)
        }

        private fun countOccurrences(haystack: String, needle: String): Int {
            if (haystack.isEmpty() || needle.isEmpty()) return 0
            var count = 0
            var idx = haystack.indexOf(needle, 0)
            while (idx >= 0) {
                count++
                idx = haystack.indexOf(needle, idx + needle.length)
            }
            return count
        }
    }

    private fun requireAuthenticatedUid(): String {
        return authProvider()
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً للوصول إلى سحابة Firebase.")
    }

    /**
     * Loads the 5 pre-indexed books (39 small folders, 278 cited chunks) from the compressed 236 KB asset.
     * Raw PDFs are never bundled inside the APK.
     */
    fun loadBundledFiveBooks(): List<SeedRagBookBundle> = loadBundledFiveBooks(appContext)

    /**
     * Real-time stream of shared Cloud Firebase books (`/rag_books` where `isShared == true`).
     * Call ONLY when authenticated.
     */
    fun observeSharedBooks(): Flow<List<CloudRagBook>> =
        db.collection("rag_books")
            .whereEqualTo("isShared", true)
            .snapshots()
            .map { snapshot -> snapshot.documents.map { CloudRagBook.fromSnapshot(it) } }

    /**
     * Real-time stream of shared small folders (`/rag_folders` where `isShared == true`).
     * Call ONLY when authenticated.
     */
    fun observeSharedFolders(): Flow<List<CloudRagFolder>> =
        db.collection("rag_folders")
            .whereEqualTo("isShared", true)
            .snapshots()
            .map { snapshot -> snapshot.documents.map { CloudRagFolder.fromSnapshot(it) } }

    suspend fun getSharedBooks(): Result<List<CloudRagBook>> {
        return try {
            val sharedSnap = db.collection("rag_books")
                .whereEqualTo("isShared", true)
                .get()
                .await()
            val sharedList = sharedSnap.documents.map { CloudRagBook.fromSnapshot(it) }
            val uid = authProvider()
            val ownedList = if (!uid.isNullOrBlank()) {
                try {
                    db.collection("rag_books")
                        .whereEqualTo("ownerId", uid)
                        .get()
                        .await()
                        .documents
                        .map { CloudRagBook.fromSnapshot(it) }
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            Result.success((sharedList + ownedList).distinctBy { it.bookId })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "rag_books")
            Result.failure(e)
        }
    }

    suspend fun getSharedFolders(): Result<List<CloudRagFolder>> {
        return try {
            val accessibleBookIds = getSharedBooks().getOrNull()?.map { it.bookId }?.toSet()
            val sharedSnap = db.collection("rag_folders")
                .whereEqualTo("isShared", true)
                .get()
                .await()
            val uid = authProvider()
            val sharedList = sharedSnap.documents
                .map { CloudRagFolder.fromSnapshot(it) }
                .filter { folder ->
                    folder.ownerId == uid || accessibleBookIds == null || folder.bookId in accessibleBookIds
                }
            val ownedList = if (!uid.isNullOrBlank()) {
                try {
                    db.collection("rag_folders")
                        .whereEqualTo("ownerId", uid)
                        .get()
                        .await()
                        .documents
                        .map { CloudRagFolder.fromSnapshot(it) }
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            Result.success((ownedList + sharedList).distinctBy { it.folderId })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "rag_folders")
            Result.failure(e)
        }
    }

    suspend fun getBookById(bookId: String): Result<CloudRagBook?> {
        return try {
            val snap = db.collection("rag_books").document(bookId).get().await()
            Result.success(if (snap.exists()) CloudRagBook.fromSnapshot(snap) else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "rag_books/$bookId")
            Result.failure(e)
        }
    }

    suspend fun getChunksForFolder(folderId: String): Result<List<CloudRagChunk>> {
        return try {
            val sharedSnap = db.collection("rag_chunks")
                .whereEqualTo("isShared", true)
                .whereEqualTo("folderId", folderId)
                .get()
                .await()
            val uid = authProvider()
            val rawSharedChunks = sharedSnap.documents.map { CloudRagChunk.fromSnapshot(it) }
            val nonOwnerBookIds = rawSharedChunks
                .filter { it.ownerId != uid }
                .map { it.bookId }
                .distinct()
            val verifiedSharedBookIds = if (nonOwnerBookIds.isEmpty()) {
                emptySet()
            } else {
                nonOwnerBookIds.filter { bid ->
                    val book = getBookById(bid).getOrNull()
                    book != null && book.isShared
                }.toSet()
            }
            val sharedChunks = rawSharedChunks.filter { chunk ->
                chunk.ownerId == uid || chunk.bookId in verifiedSharedBookIds
            }
            val ownedChunks = if (!uid.isNullOrBlank()) {
                try {
                    db.collection("rag_chunks")
                        .whereEqualTo("ownerId", uid)
                        .whereEqualTo("folderId", folderId)
                        .get()
                        .await()
                        .documents
                        .map { CloudRagChunk.fromSnapshot(it) }
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            val sorted = (ownedChunks + sharedChunks)
                .distinctBy { it.chunkId }
                .sortedBy { it.chunkIndex }
            Result.success(sorted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "rag_chunks?folderId=$folderId")
            Result.failure(e)
        }
    }

    /**
     * Uploads a structured book (metadata + small folders + cited chunks) to Cloud Firestore
     * in parent-first batches sized within Firestore's 20-getAfter-calls-per-batch rule ceiling.
     */
    suspend fun uploadBookBundle(bundle: SeedRagBookBundle): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = requireAuthenticatedUid()
            val normalizedShared = bundle.book.isShared
            val bookPayload = bundle.book.copy(isShared = normalizedShared).toCreateMap(uid)
            val folderEntries = bundle.folders.map { folder ->
                folder.folderId to folder.copy(
                    bookId = bundle.book.bookId,
                    isShared = normalizedShared
                ).toCreateMap(uid)
            }
            val chunkEntries = bundle.chunks.map { chunk ->
                chunk.chunkId to chunk.copy(
                    bookId = bundle.book.bookId,
                    isShared = normalizedShared
                ).toCreateMap(uid)
            }

            // Stage 1: Commit parent book + first slice of folders (<= 15 getAfter calls per batch)
            val folderSlices = folderEntries.chunked(MAX_FOLDER_BATCH_OPS)
            if (folderSlices.isEmpty()) {
                val bookBatch = db.batch()
                bookBatch.set(db.collection("rag_books").document(bundle.book.bookId), bookPayload)
                bookBatch.commit().await()
            } else {
                for ((idx, slice) in folderSlices.withIndex()) {
                    val batch = db.batch()
                    if (idx == 0) {
                        batch.set(db.collection("rag_books").document(bundle.book.bookId), bookPayload)
                    }
                    for ((docId, payload) in slice) {
                        batch.set(db.collection("rag_folders").document(docId), payload)
                    }
                    batch.commit().await()
                }
            }

            // Stage 2: Commit chunks in slices of <= 9 (2 getAfter calls/chunk = <= 18 calls/batch)
            for (slice in chunkEntries.chunked(MAX_CHUNK_BATCH_OPS)) {
                val batch = db.batch()
                for ((docId, payload) in slice) {
                    batch.set(db.collection("rag_chunks").document(docId), payload)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "rag_books/${bundle.book.bookId}")
            Result.failure(e)
        }
    }

    /**
     * Updates sharing state (`isShared`) consistently across a book, all its folders, and all its chunks
     * owned by the currently authenticated user.
     */
    suspend fun setBookShared(bookId: String, isShared: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = requireAuthenticatedUid()
            val bookRef = db.collection("rag_books").document(bookId)
            val bookSnap = bookRef.get().await()
            if (!bookSnap.exists()) {
                return@withContext Result.failure(NoSuchElementException("Book '$bookId' does not exist."))
            }
            val ownerId = bookSnap.getString("ownerId").orEmpty()
            if (ownerId != uid) {
                return@withContext Result.failure(SecurityException("Only the book owner can change sharing permissions."))
            }

            val folderRefs = db.collection("rag_folders")
                .whereEqualTo("ownerId", uid)
                .whereEqualTo("bookId", bookId)
                .get()
                .await()
                .documents
                .map { it.reference }

            val chunkRefs = db.collection("rag_chunks")
                .whereEqualTo("ownerId", uid)
                .whereEqualTo("bookId", bookId)
                .get()
                .await()
                .documents
                .map { it.reference }

            val updatePayload = mapOf(
                "isShared" to isShared,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            // Update book + folders first so getAfter(book) & getAfter(folder) match incoming isShared
            val folderSlices = folderRefs.chunked(MAX_FOLDER_BATCH_OPS)
            if (folderSlices.isEmpty()) {
                val batch = db.batch()
                batch.update(bookRef, updatePayload)
                batch.commit().await()
            } else {
                for ((idx, slice) in folderSlices.withIndex()) {
                    val batch = db.batch()
                    if (idx == 0) {
                        batch.update(bookRef, updatePayload)
                    }
                    for (ref in slice) {
                        batch.update(ref, updatePayload)
                    }
                    batch.commit().await()
                }
            }

            for (slice in chunkRefs.chunked(MAX_CHUNK_BATCH_OPS)) {
                val batch = db.batch()
                for (ref in slice) {
                    batch.update(ref, updatePayload)
                }
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "rag_books/$bookId")
            Result.failure(e)
        }
    }

    /**
     * Deletes a book and cascades deletion across all child chunks and folders so no orphaned records remain.
     */
    suspend fun deleteBookBundle(bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = requireAuthenticatedUid()
            val bookRef = db.collection("rag_books").document(bookId)
            val bookSnap = bookRef.get().await()
            if (!bookSnap.exists()) {
                return@withContext Result.success(Unit)
            }
            if (bookSnap.getString("ownerId") != uid) {
                return@withContext Result.failure(SecurityException("Only the book owner can delete this book."))
            }

            val chunkRefs = db.collection("rag_chunks")
                .whereEqualTo("ownerId", uid)
                .whereEqualTo("bookId", bookId)
                .get()
                .await()
                .documents
                .map { it.reference }

            val folderRefs = db.collection("rag_folders")
                .whereEqualTo("ownerId", uid)
                .whereEqualTo("bookId", bookId)
                .get()
                .await()
                .documents
                .map { it.reference }

            // Delete leaves first (chunks -> folders -> book) so non-owners never see dangling children
            val allDeletes = chunkRefs + folderRefs + bookRef
            for (slice in allDeletes.chunked(MAX_DELETE_BATCH_OPS)) {
                val batch = db.batch()
                for (ref in slice) {
                    batch.delete(ref)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "rag_books/$bookId")
            Result.failure(e)
        }
    }

    /**
     * Idempotently ensures all 5 bundled books (39 small folders, 278 chunks) exist in Cloud Firestore.
     * Skips any book whose `bookId` is already present in `rag_books`.
     */
    suspend fun ensureFiveSeedBooksSynced(
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            requireAuthenticatedUid()
            val bundles = loadBundledFiveBooks()
            if (bundles.isEmpty()) return@withContext Result.success(0)

            val existingBooks = getSharedBooks().getOrThrow().map { it.bookId }.toSet()
            val missingBundles = bundles.filter { it.book.bookId !in existingBooks }

            for ((idx, bundle) in missingBundles.withIndex()) {
                onProgress(
                    "جارٍ مزامنة «${bundle.book.title}» إلى سحابة Firebase (${idx + 1}/${missingBundles.size})...",
                    idx + 1,
                    missingBundles.size
                )
                uploadBookBundle(bundle).getOrThrow()
            }
            Result.success(bundles.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cloud RAG seed sync error", e)
            Result.failure(e)
        }
    }

    /**
     * Two-stage Small-Folder -> Cited-Chunk hybrid lexical + Arabic morphological stem + IDF + 3-gram RAG retrieval.
     * Queries live Cloud Firestore when signed in (giving live Cloud folders priority over bundled seed data
     * and including both shared and owner-private books), falling back seamlessly to the pre-indexed 5-book seed.
     */
    suspend fun retrieveRelevantPassages(
        query: String,
        cachedCloudFolders: List<CloudRagFolder> = emptyList(),
        topFoldersLimit: Int = 4,
        topChunksLimit: Int = 5
    ): List<RetrievedRagPassage> = withContext(Dispatchers.IO) {
        val tokens = extractSearchTokens(query)
        val queryStems = extractSearchStems(query)
        val normQuery = normalizeSearchText(query)
        if (tokens.isEmpty() && normQuery.length < 3) return@withContext emptyList()
        val queryTrigrams = buildCharTrigrams(normQuery)

        loadBundledFiveBooks()
        val seedFolderIdxMap = cachedSeedFolderIndex.orEmpty()
        val seedChunksIdxByFolder = cachedSeedChunkIndexByFolder.orEmpty()

        val isSignedIn = authProvider() != null
        val cloudFolders = if (cachedCloudFolders.isNotEmpty()) {
            cachedCloudFolders
        } else if (isSignedIn) {
            getSharedFolders().getOrNull().orEmpty()
        } else {
            emptyList()
        }

        // Priority fix: live Cloud folders override static seed folders with matching folderId
        val mergedIndexedFolders: List<IndexedFolder> = buildMap {
            putAll(seedFolderIdxMap)
            for (cf in cloudFolders) {
                val cachedSeed = seedFolderIdxMap[cf.folderId]
                if (cachedSeed != null &&
                    cachedSeed.folder.folderTitle == cf.folderTitle &&
                    cachedSeed.folder.topicSummary == cf.topicSummary
                ) {
                    put(cf.folderId, cachedSeed.copy(folder = cf))
                } else {
                    put(cf.folderId, indexFolder(cf))
                }
            }
        }.values.toList()

        if (mergedIndexedFolders.isEmpty()) return@withContext emptyList()

        // Compute inverse document frequency (IDF) weights per query token across folders
        val totalFolders = mergedIndexedFolders.size.coerceAtLeast(1)
        val tokenIdf = tokens.associateWith { tok ->
            val df = mergedIndexedFolders.count { idxF ->
                idxF.titleNorm.contains(tok) ||
                    idxF.summaryNorm.contains(tok) ||
                    idxF.kwNorm.any { it.contains(tok) }
            }
            1.0 + kotlin.math.ln((totalFolders + 1.0) / (df + 1.0))
        }

        // Stage 1: Score & rank small thematic folders
        val rankedFolders = mergedIndexedFolders
            .map { idxF ->
                var score = 0.0
                if (normQuery.length >= 4 && (idxF.titleNorm.contains(normQuery) || idxF.summaryNorm.contains(normQuery))) {
                    score += 8.5
                }
                for (tok in tokens) {
                    val idf = tokenIdf[tok] ?: 1.0
                    if (idxF.titleNorm.contains(tok)) score += 3.2 * idf
                    if (idxF.kwNorm.any { it.contains(tok) || tok.contains(it) }) score += 2.9 * idf
                    if (idxF.summaryNorm.contains(tok)) score += 1.6 * idf
                    if (idxF.bookNorm.contains(tok)) score += 1.0 * idf
                }
                // Arabic light-stem morphological overlap (catches plurals/possessives/clitics)
                val stemHits = queryStems.count { it in idxF.stems }
                if (stemHits > 0) {
                    score += stemHits * 2.1
                }
                // Character 3-gram similarity fallback for paraphrased/spelling variants
                val trigramSim = diceSimilarity(queryTrigrams, idxF.trigrams)
                if (trigramSim >= 0.18) {
                    score += trigramSim * 4.5
                }
                idxF.folder to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topFoldersLimit)

        if (rankedFolders.isEmpty()) return@withContext emptyList()

        // Stage 2: Fetch & score chunks only from the winning small folders
        val candidateChunks = mutableListOf<Pair<CloudRagChunk, Double>>()
        for ((folder, folderScore) in rankedFolders) {
            val indexedChunks: List<IndexedChunk> = if (isSignedIn) {
                val cloudChunks = getChunksForFolder(folder.folderId).getOrNull()
                if (!cloudChunks.isNullOrEmpty()) {
                    val seedIndexed = seedChunksIdxByFolder[folder.folderId]?.associateBy { it.chunk.chunkId }.orEmpty()
                    cloudChunks.map { cc ->
                        val cached = seedIndexed[cc.chunkId]
                        if (cached != null && cached.chunk.content == cc.content && cached.chunk.title == cc.title) {
                            cached.copy(chunk = cc)
                        } else {
                            indexChunk(cc)
                        }
                    }
                } else {
                    seedChunksIdxByFolder[folder.folderId].orEmpty()
                }
            } else {
                seedChunksIdxByFolder[folder.folderId].orEmpty()
            }

            for (idxC in indexedChunks) {
                var cScore = folderScore * 0.35
                if (normQuery.length >= 4 && idxC.contentNorm.contains(normQuery)) {
                    cScore += 9.5
                }
                for (tok in tokens) {
                    val idf = tokenIdf[tok] ?: 1.0
                    if (idxC.titleNorm.contains(tok)) cScore += 3.0 * idf
                    if (idxC.kwNorm.any { it.contains(tok) }) cScore += 2.5 * idf
                    val occurrences = countOccurrences(idxC.contentNorm, tok)
                    if (occurrences > 0) {
                        cScore += minOf(occurrences * 1.6 * idf, 7.5)
                    }
                }
                val chunkStemHits = queryStems.count { it in idxC.stems }
                if (chunkStemHits > 0) {
                    cScore += chunkStemHits * 1.8
                }
                if (cScore > 1.2) {
                    candidateChunks.add(idxC.chunk to cScore)
                }
            }
        }

        candidateChunks
            .sortedByDescending { it.second }
            .distinctBy { it.first.chunkId }
            .take(topChunksLimit)
            .map { (chunk, score) -> RetrievedRagPassage(chunk = chunk, score = score) }
    }
}
