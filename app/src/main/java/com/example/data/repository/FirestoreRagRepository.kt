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
        private const val MAX_BATCH_OPS = 380
        private val SEED_LOCK = Any()

        private val ARABIC_STOPWORDS = setOf(
            "من", "في", "فى", "على", "علي", "الى", "إلى", "عن", "مع", "هو", "هي",
            "هم", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي", "الذين", "ما", "ماذا",
            "كيف", "متى", "أين", "اين", "هل", "كان", "كانت", "بين", "كل", "بعد",
            "قبل", "عند", "او", "أو", "ثم", "يا", "حدثني", "اشرح", "اذكر", "قولي",
            "عايز", "اعرف", "the", "of", "and", "in", "to", "what", "who", "how", "tell", "me", "about"
        )

        @Volatile
        private var cachedSeedBundles: List<SeedRagBookBundle>? = null

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
                    cachedSeedBundles = parsed
                    parsed
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read compressed 5-book RAG seed pack", e)
                    emptyList()
                }
            }
        }

        fun normalizeSearchText(input: String): String {
            return input
                .lowercase()
                .replace(Regex("[\u064B-\u065F\u0670\u0640]"), "")
                .replace(Regex("[أإآٱ]"), "ا")
                .replace('ة', 'ه')
                .replace('ى', 'ي')
                .replace('ؤ', 'و')
                .replace('ئ', 'ي')
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
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
            val snap = db.collection("rag_books")
                .whereEqualTo("isShared", true)
                .get()
                .await()
            Result.success(snap.documents.map { CloudRagBook.fromSnapshot(it) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "rag_books")
            Result.failure(e)
        }
    }

    suspend fun getSharedFolders(): Result<List<CloudRagFolder>> {
        return try {
            val snap = db.collection("rag_folders")
                .whereEqualTo("isShared", true)
                .get()
                .await()
            Result.success(snap.documents.map { CloudRagFolder.fromSnapshot(it) })
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
            val snap = db.collection("rag_chunks")
                .whereEqualTo("isShared", true)
                .whereEqualTo("folderId", folderId)
                .get()
                .await()
            val sorted = snap.documents
                .map { CloudRagChunk.fromSnapshot(it) }
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
     * in atomic chunks of <= 380 operations per WriteBatch.
     */
    suspend fun uploadBookBundle(bundle: SeedRagBookBundle): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = requireAuthenticatedUid()
            data class PendingWrite(val collection: String, val docId: String, val payload: Map<String, Any>)

            val writes = buildList {
                add(PendingWrite("rag_books", bundle.book.bookId, bundle.book.toCreateMap(uid)))
                for (folder in bundle.folders) {
                    add(PendingWrite("rag_folders", folder.folderId, folder.toCreateMap(uid)))
                }
                for (chunk in bundle.chunks) {
                    add(PendingWrite("rag_chunks", chunk.chunkId, chunk.toCreateMap(uid)))
                }
            }

            for (slice in writes.chunked(MAX_BATCH_OPS)) {
                val batch = db.batch()
                for (op in slice) {
                    val ref = db.collection(op.collection).document(op.docId)
                    batch.set(ref, op.payload)
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
     * Two-stage Small-Folder -> Cited-Chunk RAG retrieval.
     * Queries live Cloud Firestore when signed in (covering all 5 books + up to 1,000+ uploaded PDF books),
     * and falls back seamlessly to the compressed 5-book seed index if offline or unauthenticated.
     */
    suspend fun retrieveRelevantPassages(
        query: String,
        cachedCloudFolders: List<CloudRagFolder> = emptyList(),
        topFoldersLimit: Int = 4,
        topChunksLimit: Int = 5
    ): List<RetrievedRagPassage> = withContext(Dispatchers.IO) {
        val tokens = extractSearchTokens(query)
        if (tokens.isEmpty()) return@withContext emptyList()
        val normQuery = normalizeSearchText(query)

        val seedBundles = loadBundledFiveBooks()
        val seedFoldersMap = seedBundles.flatMap { it.folders }.associateBy { it.folderId }
        val seedChunksByFolder = seedBundles.flatMap { it.chunks }.groupBy { it.folderId }

        val isSignedIn = authProvider() != null
        val cloudFolders = if (cachedCloudFolders.isNotEmpty()) {
            cachedCloudFolders
        } else if (isSignedIn) {
            getSharedFolders().getOrNull().orEmpty()
        } else {
            emptyList()
        }

        // Merge cloud folders (including newly uploaded PDF books) with seed folders
        val allFolders = (cloudFolders.associateBy { it.folderId } + seedFoldersMap).values.toList()
        if (allFolders.isEmpty()) return@withContext emptyList()

        // Stage 1: Score & rank small thematic folders
        val rankedFolders = allFolders
            .map { folder ->
                val titleNorm = normalizeSearchText(folder.folderTitle)
                val summaryNorm = normalizeSearchText(folder.topicSummary)
                val bookNorm = normalizeSearchText(folder.bookTitle + " " + folder.category)
                val kwNorm = folder.keywords.map { normalizeSearchText(it) }

                var score = 0.0
                if (normQuery.length >= 4 && (titleNorm.contains(normQuery) || summaryNorm.contains(normQuery))) {
                    score += 8.0
                }
                for (tok in tokens) {
                    if (titleNorm.contains(tok)) score += 3.5
                    if (kwNorm.any { it.contains(tok) || tok.contains(it) }) score += 3.2
                    if (summaryNorm.contains(tok)) score += 1.8
                    if (bookNorm.contains(tok)) score += 1.2
                }
                folder to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topFoldersLimit)

        if (rankedFolders.isEmpty()) return@withContext emptyList()

        // Stage 2: Fetch & score chunks only from the winning small folders
        val candidateChunks = mutableListOf<Pair<CloudRagChunk, Double>>()
        for ((folder, folderScore) in rankedFolders) {
            val chunksInFolder = if (isSignedIn) {
                getChunksForFolder(folder.folderId).getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: seedChunksByFolder[folder.folderId].orEmpty()
            } else {
                seedChunksByFolder[folder.folderId].orEmpty()
            }

            for (chunk in chunksInFolder) {
                val cTitleNorm = normalizeSearchText(chunk.title)
                val cContentNorm = normalizeSearchText(chunk.content)
                val cKwNorm = chunk.keywords.map { normalizeSearchText(it) }

                var cScore = folderScore * 0.35
                if (normQuery.length >= 4 && cContentNorm.contains(normQuery)) {
                    cScore += 9.0
                }
                for (tok in tokens) {
                    if (cTitleNorm.contains(tok)) cScore += 3.0
                    if (cKwNorm.any { it.contains(tok) }) cScore += 2.5
                    val occurrences = Regex(Regex.escape(tok)).findAll(cContentNorm).count()
                    if (occurrences > 0) {
                        cScore += minOf(occurrences * 1.6, 6.4)
                    }
                }
                if (cScore > 1.2) {
                    candidateChunks.add(chunk to cScore)
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
