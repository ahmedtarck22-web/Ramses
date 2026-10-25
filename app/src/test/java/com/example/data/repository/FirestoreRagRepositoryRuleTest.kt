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

    private companion object {
        const val ALICE_EMAIL = "alice.ramses@test.com"
        const val BOB_EMAIL = "bob.ramses@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 8000L
    }
}
