package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.example.data.model.CloudRagBook
import com.example.data.model.CloudRagChunk
import com.example.data.model.CloudRagFolder
import com.example.data.model.SeedRagBookBundle
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreRagRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun buildTestBundle(suffix: String, isShared: Boolean = true): SeedRagBookBundle {
        val bookId = "book_test_$suffix"
        val folderId = "${bookId}_f01"
        val chunkId = "${folderId}_c001"

        val book = CloudRagBook(
            docId = bookId,
            bookId = bookId,
            isShared = isShared,
            title = "تاريخ رمسيس الثاني ومعركة قادش ($suffix)",
            author = "د. داليا محمد سليمان",
            eraCategory = "عصر الدولة الحديثة",
            summary = "ملخص شامل لمعركة قادش ومعاهدة السلام المصرية الحيثية.",
            pageCount = 163,
            folderCount = 1,
            chunkCount = 1,
            keywords = listOf("رمسيس الثاني", "قادش", "معاهدة السلام")
        )
        val folder = CloudRagFolder(
            docId = folderId,
            folderId = folderId,
            bookId = bookId,
            bookTitle = book.title,
            isShared = isShared,
            folderIndex = 1,
            folderTitle = "الأسرة التاسعة عشرة ومعركة قادش",
            category = "عصر الرمسيسيين",
            pageRange = "ص 110 - 125",
            topicSummary = "تفاصيل معركة قادش والبنود الخمسة لمعاهدة السلام مع خاتوسيلي الثالث.",
            chunkCount = 1,
            keywords = listOf("قادش", "خاتوسيلي", "رمسيس الثاني")
        )
        val chunk = CloudRagChunk(
            docId = chunkId,
            chunkId = chunkId,
            bookId = bookId,
            bookTitle = book.title,
            folderId = folderId,
            folderTitle = folder.folderTitle,
            isShared = isShared,
            chunkIndex = 1,
            pageRange = "ص 118 - 122",
            title = "بنود معاهدة السلام المصرية الحيثية",
            content = "أبرم الملك رمسيس الثاني في العام 21 من حكمه معاهدة سلام خالدة مع خاتوسيلي الثالث ملك الحيثيين بعد معركة قادش.",
            keywords = listOf("معاهدة السلام", "قادش", "خاتوسيلي")
        )
        return SeedRagBookBundle(book = book, folders = listOf(folder), chunks = listOf(chunk))
    }

    @Test
    fun uploadBookBundle_authenticatedUser_persistsAndRetrievesPassages() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: uid }
        )
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val bundle = buildTestBundle(suffix, isShared = true)

        val uploadRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.uploadBookBundle(bundle) }
        assertTrue("Upload failed: ${uploadRes.exceptionOrNull()}", uploadRes.isSuccess)

        val sharedBooks = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getSharedBooks().getOrThrow() }
        assertTrue(sharedBooks.any { it.bookId == bundle.book.bookId })

        val passages = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.retrieveRelevantPassages("ما هي بنود معاهدة السلام بعد معركة قادش $suffix؟")
        }
        assertTrue(passages.any { it.chunk.chunkId == bundle.chunks.first().chunkId })
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteBookBundle(bundle.book.bookId).getOrThrow() }
        Unit
    }

    @Test
    fun observeSharedBooks_authenticatedUser_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: uid }
        )
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val bundle = buildTestBundle(suffix, isShared = true)

        withTimeout(DEFAULT_TIMEOUT_MS) { repo.uploadBookBundle(bundle).getOrThrow() }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSharedBooks().first { list -> list.any { it.bookId == bundle.book.bookId } }
        }
        assertTrue(emitted.any { it.bookId == bundle.book.bookId })
    }

    @Test
    fun getBookById_privateBookCrossUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: aliceUid }
        )
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val privateBundle = buildTestBundle(suffix, isShared = false)

        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.uploadBookBundle(privateBundle).getOrThrow() }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: bobUid }
        )

        val readResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getBookById(privateBundle.book.bookId)
        }
        assertTrue(readResult.isFailure)
        val fsEx = readResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got ${readResult.exceptionOrNull()}", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observeSharedBooks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { null }
        )

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeSharedBooks().first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull("Expected unauthenticated listener to fail", caught)
        val fsEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun retrieveRelevantPassages_privateOwnerBook_returnsForOwnerAndHidesFromOtherUser() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: aliceUid }
        )
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val uniqueKeyword = "برديةسرية$suffix"
        val privateBundle = buildTestBundle(suffix, isShared = false).let { base ->
            base.copy(
                folders = base.folders.map { f ->
                    f.copy(
                        folderTitle = "مجلد خاص $uniqueKeyword",
                        keywords = f.keywords + uniqueKeyword
                    )
                },
                chunks = base.chunks.map { c ->
                    c.copy(
                        title = "فقرة خاصة $uniqueKeyword",
                        content = "يحتوي هذا المخطوط السري ($uniqueKeyword) على تفاصيل جنازة رمسيس الثاني.",
                        keywords = c.keywords + uniqueKeyword
                    )
                }
            )
        }

        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.uploadBookBundle(privateBundle).getOrThrow() }

        val alicePassages = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.retrieveRelevantPassages("ما تفاصيل $uniqueKeyword؟")
        }
        assertTrue(
            "Expected owner Alice to retrieve her private RAG chunk",
            alicePassages.any { it.chunk.chunkId == privateBundle.chunks.first().chunkId }
        )

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: bobUid }
        )
        val bobPassages = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.retrieveRelevantPassages("ما تفاصيل $uniqueKeyword؟")
        }
        assertFalse(
            "Expected non-owner Bob NOT to retrieve Alice's private RAG chunk",
            bobPassages.any { it.chunk.chunkId == privateBundle.chunks.first().chunkId }
        )
    }

    @Test
    fun chatRepository_sessionUpdatePreservesCreatedAtAndCascadesMessageDelete() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val chatRepo = FirestoreChatRepository(firestore)
        val sessionId = "sess_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val initialSession = ChatSession(
            id = sessionId,
            title = "محادثة أولية",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.saveSession(initialSession, aliceUid)
        }
        val createdSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(aliceUid)
                .collection("sessions").document(sessionId).get().await()
        }
        assertTrue(createdSnap.exists())
        val originalCreatedAt = createdSnap.getTimestamp("createdAt")
        assertNotNull("Expected createdAt server timestamp on create", originalCreatedAt)

        // Update session title via upsertSessionMetadata — must preserve immutable createdAt under firestore.rules
        withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.upsertSessionMetadata(sessionId, "محادثة محدثة عن أبو سمبل", aliceUid)
        }
        val updatedSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(aliceUid)
                .collection("sessions").document(sessionId).get().await()
        }
        assertEquals("محادثة محدثة عن أبو سمبل", updatedSnap.getString("title"))
        assertEquals(originalCreatedAt, updatedSnap.getTimestamp("createdAt"))

        // Save a message and verify deleteSession cascades deletion to subcollection messages
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val message = ChatMessageEntity(
            id = msgId,
            sessionId = sessionId,
            sender = "USER",
            text = "متى تتعامد الشمس على معبد أبو سمبل؟",
            timestamp = System.currentTimeMillis()
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.saveMessage(message, aliceUid)
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.deleteSession(sessionId, aliceUid)
        }

        val deletedMsgSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(aliceUid)
                .collection("sessions").document(sessionId)
                .collection("messages").document(msgId).get().await()
        }
        assertFalse("Expected message document to be deleted with parent session", deletedMsgSnap.exists())
    }

    @Test
    fun setBookShared_cascadesRevocationAndReSharingAcrossMultiBatchChunks() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: aliceUid }
        )
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val bookId = "book_share_$suffix"
        val uniqueToken = "مسلةملكية$suffix"

        val folders = (1..2).map { fIdx ->
            val fid = "${bookId}_f0$fIdx"
            CloudRagFolder(
                docId = fid,
                folderId = fid,
                bookId = bookId,
                bookTitle = "كتاب المشاركة $suffix",
                isShared = true,
                folderIndex = fIdx,
                folderTitle = "مجلد $fIdx $uniqueToken",
                category = "عصر الرمسيسيين",
                pageRange = "ص ${fIdx * 10} - ${fIdx * 10 + 9}",
                topicSummary = "يتناول نقوش $uniqueToken في معابد طيبة.",
                chunkCount = 6,
                keywords = listOf(uniqueToken, "رمسيس الثاني")
            )
        }
        // 12 chunks exercises multi-batch chunk writes/updates (MAX_CHUNK_BATCH_OPS = 9)
        val chunks = (1..12).map { cIdx ->
            val parentFolder = folders[(cIdx - 1) / 6]
            val cid = "${parentFolder.folderId}_c${cIdx.toString().padStart(3, '0')}"
            CloudRagChunk(
                docId = cid,
                chunkId = cid,
                bookId = bookId,
                bookTitle = "كتاب المشاركة $suffix",
                folderId = parentFolder.folderId,
                folderTitle = parentFolder.folderTitle,
                isShared = true,
                chunkIndex = cIdx,
                pageRange = "ص $cIdx",
                title = "فقرة رقم $cIdx حول $uniqueToken",
                content = "تفصيل تاريخي موثق رقم $cIdx عن $uniqueToken في عهد الملك رمسيس الثاني.",
                keywords = listOf(uniqueToken, "طيبة")
            )
        }
        val bundle = SeedRagBookBundle(
            book = CloudRagBook(
                docId = bookId,
                bookId = bookId,
                isShared = true,
                title = "كتاب المشاركة $suffix",
                author = "د. سليم حسن",
                eraCategory = "الدولة الحديثة",
                summary = "دراسة موثقة حول $uniqueToken.",
                pageCount = 60,
                folderCount = folders.size,
                chunkCount = chunks.size,
                keywords = listOf(uniqueToken, "رمسيس الثاني")
            ),
            folders = folders,
            chunks = chunks
        )

        withTimeout(DEFAULT_TIMEOUT_MS * 2) { aliceRepo.uploadBookBundle(bundle).getOrThrow() }

        // Switch to Bob: verify Bob can read shared book, folders, chunks, and RAG passages
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: bobUid }
        )
        assertNotNull(withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getBookById(bookId).getOrThrow() })
        assertEquals(12, withTimeout(DEFAULT_TIMEOUT_MS) {
            folders.sumOf { f -> bobRepo.getChunksForFolder(f.folderId).getOrThrow().size }
        })
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.retrieveRelevantPassages("ما هي نقوش $uniqueToken؟").any { it.chunk.bookId == bookId }
        })

        // Non-owner Bob cannot revoke sharing on Alice's book
        val bobRevokeAttempt = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.setBookShared(bookId, false) }
        assertTrue("Non-owner must not be able to change sharing", bobRevokeAttempt.isFailure)

        // Owner Alice revokes sharing across book, folders, and all 12 chunks
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS * 2) { aliceRepo.setBookShared(bookId, false).getOrThrow() }

        // Switch back to Bob: verify complete revocation across book, folders, chunks, and RAG retrieval
        signInTestUser(BOB_EMAIL)
        val bobBookAfterRevoke = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getBookById(bookId) }
        assertTrue(bobBookAfterRevoke.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            bobBookAfterRevoke.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
        assertFalse(withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getSharedBooks().getOrThrow().any { it.bookId == bookId }
        })
        assertFalse(withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getSharedFolders().getOrThrow().any { it.bookId == bookId }
        })
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getChunksForFolder(folders.first().folderId).getOrThrow().isEmpty()
        })
        assertFalse(withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.retrieveRelevantPassages("ما هي نقوش $uniqueToken؟").any { it.chunk.bookId == bookId }
        })

        // Owner Alice still retrieves her private book and passages, and can re-share
        signInTestUser(ALICE_EMAIL)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.retrieveRelevantPassages("ما هي نقوش $uniqueToken؟").any { it.chunk.bookId == bookId }
        })
        withTimeout(DEFAULT_TIMEOUT_MS * 2) { aliceRepo.setBookShared(bookId, true).getOrThrow() }

        signInTestUser(BOB_EMAIL)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.retrieveRelevantPassages("ما هي نقوش $uniqueToken؟").any { it.chunk.bookId == bookId }
        })
    }

    @Test
    fun deleteBookBundle_cascadesDeletionAcrossChunksFoldersAndBook() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: aliceUid }
        )
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val bundle = buildTestBundle(suffix, isShared = true)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.uploadBookBundle(bundle).getOrThrow() }

        // Non-owner Bob cannot delete Alice's book
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: bobUid }
        )
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.deleteBookBundle(bundle.book.bookId) }.isFailure)

        // Owner Alice deletes the bundle cleanly
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.deleteBookBundle(bundle.book.bookId).getOrThrow() }

        val bookSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("rag_books").document(bundle.book.bookId).get().await()
        }
        val folderSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("rag_folders").document(bundle.folders.first().folderId).get().await()
        }
        val chunkSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("rag_chunks").document(bundle.chunks.first().chunkId).get().await()
        }
        assertFalse(bookSnap.exists())
        assertFalse(folderSnap.exists())
        assertFalse(chunkSnap.exists())
    }

    @Test
    fun observeSharedFolders_emitsBookScopedFoldersAndDropsImmediatelyOnRevocation() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: aliceUid }
        )
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val bundle = buildTestBundle(suffix, isShared = true)
        val folderId = bundle.folders.first().folderId

        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.uploadBookBundle(bundle).getOrThrow() }

        // Bob observes shared folders (aligned via bookId whereIn slices) and sees Alice's shared folder
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: bobUid }
        )
        val emittedForBob = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeSharedFolders().first { list -> list.any { it.folderId == folderId } }
        }
        assertTrue(emittedForBob.any { it.folderId == folderId })

        // Alice revokes sharing on the book
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.setBookShared(bundle.book.bookId, false).getOrThrow() }

        // Owner Alice's observeAccessibleFolders STILL includes her own private folder
        val aliceAccessible = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeAccessibleFolders().first { list -> list.any { it.folderId == folderId && !it.isShared } }
        }
        assertTrue(aliceAccessible.any { it.folderId == folderId })

        // Bob's observeSharedFolders excludes Alice's revoked folder
        signInTestUser(BOB_EMAIL)
        val bobAfterRevoke = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeSharedFolders().first { list -> list.none { it.folderId == folderId } }
        }
        assertFalse(bobAfterRevoke.any { it.folderId == folderId })
    }

    @Test
    fun privateParentAndRevokedSharing_blocksRawGetAndListOnChildFoldersAndChunks() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: aliceUid }
        )
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)

        // 1. Alice uploads a private book bundle (isShared = false)
        val privBundle = buildTestBundle("priv_$suffix", isShared = false)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.uploadBookBundle(privBundle).getOrThrow() }

        // 2. Alice uploads a shared book bundle, then revokes ONLY the parent rag_books doc (mid-cascade state)
        val midRevokeBundle = buildTestBundle("mid_$suffix", isShared = true)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.uploadBookBundle(midRevokeBundle).getOrThrow() }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("rag_books").document(midRevokeBundle.book.bookId)
                .update(
                    mapOf(
                        "isShared" to false,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
        }

        // Switch to Bob and verify EVERY raw get and list against private-parent and mid-revoked children fails with PERMISSION_DENIED
        signInTestUser(BOB_EMAIL)

        suspend fun assertPermissionDenied(label: String, block: suspend () -> Any?) {
            var caught: Throwable? = null
            try {
                withTimeout(DEFAULT_TIMEOUT_MS) { block() }
            } catch (t: Throwable) {
                caught = t
            }
            assertNotNull("Expected PERMISSION_DENIED for $label", caught)
            val fsEx = caught?.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException for $label, got $caught", fsEx)
            assertEquals("Unexpected code for $label", FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }

        // Unscoped rag_folders list (without bookId) must be denied
        assertPermissionDenied("unscoped rag_folders list") {
            firestore.collection("rag_folders").whereEqualTo("isShared", true).get().await()
        }

        // Private parent book -> child folder & chunk get + list denied
        val privBookId = privBundle.book.bookId
        val privFolderId = privBundle.folders.first().folderId
        val privChunkId = privBundle.chunks.first().chunkId
        assertPermissionDenied("private folder get") {
            firestore.collection("rag_folders").document(privFolderId).get().await()
        }
        assertPermissionDenied("private folder list by bookId") {
            firestore.collection("rag_folders")
                .whereEqualTo("isShared", true)
                .whereEqualTo("bookId", privBookId)
                .get().await()
        }
        assertPermissionDenied("private chunk get") {
            firestore.collection("rag_chunks").document(privChunkId).get().await()
        }
        assertPermissionDenied("private chunk list by folderId") {
            firestore.collection("rag_chunks")
                .whereEqualTo("isShared", true)
                .whereEqualTo("folderId", privFolderId)
                .get().await()
        }

        // Mid-revocation parent book (book.isShared == false while child folder/chunk still have isShared == true) -> BOTH get AND list denied!
        val midBookId = midRevokeBundle.book.bookId
        val midFolderId = midRevokeBundle.folders.first().folderId
        val midChunkId = midRevokeBundle.chunks.first().chunkId
        assertPermissionDenied("mid-revoke book get") {
            firestore.collection("rag_books").document(midBookId).get().await()
        }
        assertPermissionDenied("mid-revoke child folder get") {
            firestore.collection("rag_folders").document(midFolderId).get().await()
        }
        assertPermissionDenied("mid-revoke child folder list") {
            firestore.collection("rag_folders")
                .whereEqualTo("isShared", true)
                .whereEqualTo("bookId", midBookId)
                .get().await()
        }
        assertPermissionDenied("mid-revoke child chunk get") {
            firestore.collection("rag_chunks").document(midChunkId).get().await()
        }
        assertPermissionDenied("mid-revoke child chunk list") {
            firestore.collection("rag_chunks")
                .whereEqualTo("isShared", true)
                .whereEqualTo("folderId", midFolderId)
                .get().await()
        }
    }

    @Test
    fun crossUserAccess_blocksChatSessionsMessagesAndForeignChildAttachment() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val chatRepo = FirestoreChatRepository(firestore)
        val aliceRagRepo = FirestoreRagRepository(
            db = firestore,
            appContext = null,
            authProvider = { auth.currentUser?.uid ?: aliceUid }
        )
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val sessionId = "sess_iso_$suffix"
        val messageId = "msg_iso_$suffix"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.saveSession(
                ChatSession(id = sessionId, title = "جلسة خاصة", createdAt = 1000L, updatedAt = 1000L),
                aliceUid
            )
            chatRepo.saveMessage(
                ChatMessageEntity(id = messageId, sessionId = sessionId, sender = "USER", text = "سؤال خاص", timestamp = 1000L),
                aliceUid
            )
        }

        val sharedBundle = buildTestBundle("shared_foreign_$suffix", isShared = true)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRagRepo.uploadBookBundle(sharedBundle).getOrThrow() }

        // Sign in as Bob
        val bobUid = signInTestUser(BOB_EMAIL)

        suspend fun assertDenied(label: String, block: suspend () -> Any?) {
            var caught: Throwable? = null
            try {
                withTimeout(DEFAULT_TIMEOUT_MS) { block() }
            } catch (t: Throwable) {
                caught = t
            }
            assertNotNull("Expected PERMISSION_DENIED for $label", caught)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                caught?.unwrapFirestoreException()?.code
            )
        }

        // Bob cannot get or list Alice's sessions or messages
        assertDenied("cross-user session get") {
            firestore.collection("users").document(aliceUid)
                .collection("sessions").document(sessionId).get().await()
        }
        assertDenied("cross-user sessions list") {
            firestore.collection("users").document(aliceUid)
                .collection("sessions").get().await()
        }
        assertDenied("cross-user message get") {
            firestore.collection("users").document(aliceUid)
                .collection("sessions").document(sessionId)
                .collection("messages").document(messageId).get().await()
        }
        assertDenied("cross-user messages list") {
            firestore.collection("users").document(aliceUid)
                .collection("sessions").document(sessionId)
                .collection("messages").get().await()
        }

        // Bob cannot attach his own folder or chunk under Alice's shared book/folder
        val injectedFolderId = "bob_inj_f_$suffix"
        val injectedFolderPayload = CloudRagFolder(
            docId = injectedFolderId,
            folderId = injectedFolderId,
            bookId = sharedBundle.book.bookId,
            bookTitle = sharedBundle.book.title,
            isShared = true,
            folderIndex = 99,
            folderTitle = "مجلد دخيل",
            category = "عام",
            pageRange = "ص 1",
            topicSummary = "محاولة ربط مجلد بكتاب مستخدم آخر",
            chunkCount = 1,
            keywords = listOf("رمسيس")
        ).toCreateMap(bobUid)
        assertDenied("foreign child folder attachment") {
            firestore.collection("rag_folders").document(injectedFolderId).set(injectedFolderPayload).await()
        }

        val injectedChunkId = "bob_inj_c_$suffix"
        val injectedChunkPayload = CloudRagChunk(
            docId = injectedChunkId,
            chunkId = injectedChunkId,
            bookId = sharedBundle.book.bookId,
            bookTitle = sharedBundle.book.title,
            folderId = sharedBundle.folders.first().folderId,
            folderTitle = sharedBundle.folders.first().folderTitle,
            isShared = true,
            chunkIndex = 99,
            pageRange = "ص 1",
            title = "فقرة دخيلة",
            content = "محاولة ربط فقرة بمجلد مستخدم آخر",
            keywords = listOf("رمسيس")
        ).toCreateMap(bobUid)
        assertDenied("foreign child chunk attachment") {
            firestore.collection("rag_chunks").document(injectedChunkId).set(injectedChunkPayload).await()
        }
    }

    @Test
    fun invalidKeywordLists_rejectedByFirestoreRulesAcrossCreateAndUpdate() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)

        suspend fun assertWriteDenied(label: String, block: suspend () -> Any?) {
            var caught: Throwable? = null
            try {
                withTimeout(DEFAULT_TIMEOUT_MS) { block() }
            } catch (t: Throwable) {
                caught = t
            }
            assertNotNull("Expected PERMISSION_DENIED for $label", caught)
            assertEquals(
                "Expected PERMISSION_DENIED for $label",
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                caught?.unwrapFirestoreException()?.code
            )
        }

        fun rawBookPayload(bookId: String, rawKeywords: Any): Map<String, Any> = mapOf(
            "bookId" to bookId,
            "ownerId" to aliceUid,
            "isShared" to true,
            "title" to "كتاب اختبار الكلمات المفتاحية",
            "author" to "مؤلف",
            "eraCategory" to "عصر الدولة الحديثة",
            "summary" to "ملخص اختباري للتحقق من قواعد الكلمات المفتاحية.",
            "pageCount" to 10,
            "folderCount" to 1,
            "chunkCount" to 1,
            "keywords" to rawKeywords,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // 1. Empty string element rejected
        assertWriteDenied("empty string keyword") {
            val bid = "kw_empty_$suffix"
            firestore.collection("rag_books").document(bid)
                .set(rawBookPayload(bid, listOf("رمسيس", ""))).await()
        }

        // 2. Non-string element at non-zero index rejected
        assertWriteDenied("non-string keyword at index 2") {
            val bid = "kw_nonstr_$suffix"
            firestore.collection("rag_books").document(bid)
                .set(rawBookPayload(bid, listOf("رمسيس", "قادش", 12345))).await()
        }

        // 3. Overlong >100-char string element rejected
        assertWriteDenied("overlong 101-char keyword") {
            val bid = "kw_long_$suffix"
            firestore.collection("rag_books").document(bid)
                .set(rawBookPayload(bid, listOf("رمسيس", "أ".repeat(101)))).await()
        }

        // 4. Oversized >40-element list on rag_books rejected
        assertWriteDenied("41 keywords on rag_books") {
            val bid = "kw_41_$suffix"
            val fortyOne = (1..41).map { "كلمة_$it" }
            firestore.collection("rag_books").document(bid)
                .set(rawBookPayload(bid, fortyOne)).await()
        }

        // 5. Delimiter injection rejected
        assertWriteDenied("unit-separator delimiter injection") {
            val bid = "kw_delim_$suffix"
            val injected = "أ".repeat(60) + "\u001F" + "ب".repeat(60)
            firestore.collection("rag_books").document(bid)
                .set(rawBookPayload(bid, listOf(injected))).await()
        }

        // 6. Client model sanitization (toCreateMap) cleans dirty inputs so legitimate upload succeeds,
        //    and subsequent raw update with invalid keywords is rejected!
        val cleanId = "kw_clean_$suffix"
        val dirtyBook = CloudRagBook(
            docId = cleanId,
            bookId = cleanId,
            isShared = true,
            title = "كتاب منقّح",
            author = "مؤلف",
            eraCategory = "تاريخ",
            summary = "ملخص صالح",
            pageCount = 12,
            folderCount = 0,
            chunkCount = 0,
            keywords = listOf("  رمسيس\u001Fالثاني  ", "", "   ", "م".repeat(140)) + (1..45).map { "وسم_$it" }
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("rag_books").document(cleanId)
                .set(dirtyBook.toCreateMap(aliceUid)).await()
        }
        assertWriteDenied("invalid keyword list on update") {
            firestore.collection("rag_books").document(cleanId)
                .update(
                    mapOf(
                        "keywords" to listOf("صحيح", ""),
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("rag_books").document(cleanId).delete().await()
        }
        Unit
    }

    private companion object {
        const val ALICE_EMAIL = "alice.ramses@test.com"
        const val BOB_EMAIL = "bob.ramses@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 8000L
    }
}
