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
            repo.retrieveRelevantPassages("ما هي بنود معاهدة السلام بعد معركة قادش؟")
        }
        assertTrue(passages.any { it.chunk.chunkId == bundle.chunks.first().chunkId })
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

    private companion object {
        const val ALICE_EMAIL = "alice.ramses@test.com"
        const val BOB_EMAIL = "bob.ramses@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
