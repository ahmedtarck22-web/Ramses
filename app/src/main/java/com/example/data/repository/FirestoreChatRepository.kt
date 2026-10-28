package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirestoreChatRepository(
    private val db: FirebaseFirestore
) {
    companion object {
        private const val TAG = "FirestoreChatRepo"

        @Volatile
        private var instance: FirestoreChatRepository? = null

        fun getInstance(context: Context): FirestoreChatRepository {
            return instance ?: synchronized(this) {
                val appCtx = context.applicationContext ?: context
                com.example.firebase.ensureFirebaseInitialized(appCtx)
                val dbId = appCtx.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(dbId)
                instance ?: FirestoreChatRepository(firestore).also { instance = it }
            }
        }
    }

    suspend fun syncUserProfile(user: FirebaseUser) {
        val path = "users/${user.uid}"
        try {
            val userDoc = db.collection("users").document(user.uid)
            val existingSnap = userDoc.get().await()
            val data = mutableMapOf<String, Any>(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: "زائر ملكي").take(100),
                "lastActiveAt" to FieldValue.serverTimestamp()
            )
            if (!existingSnap.exists() || existingSnap.getTimestamp("createdAt") == null) {
                data["createdAt"] = FieldValue.serverTimestamp()
            }
            user.email?.takeIf { it.isNotBlank() }?.let { data["email"] = it.take(254) }
            user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let { data["photoUrl"] = it.take(1024) }

            userDoc.set(data, SetOptions.merge()).await()
            Log.d(TAG, "User profile synced to Firestore: ${user.uid}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun saveSession(session: ChatSession, userId: String) {
        val path = "users/$userId/sessions/${session.id}"
        try {
            val sessionDoc = db.collection("users").document(userId)
                .collection("sessions").document(session.id)

            val existingSnap = sessionDoc.get().await()
            val data = mutableMapOf<String, Any>(
                "id" to session.id,
                "userId" to userId,
                "title" to session.title.ifBlank { "محادثة مع رمسيس" }.take(200),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existingSnap.exists() || existingSnap.getTimestamp("createdAt") == null) {
                data["createdAt"] = FieldValue.serverTimestamp()
            }

            sessionDoc.set(data, SetOptions.merge()).await()
            Log.d(TAG, "Session synced to Firestore: ${session.id}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    /**
     * Upserts session title and updatedAt without overwriting existing createdAt.
     * Safe even if the user signed in mid-session before the session document existed in Firestore.
     */
    suspend fun upsertSessionMetadata(sessionId: String, title: String, userId: String) {
        val path = "users/$userId/sessions/$sessionId"
        try {
            val sessionDoc = db.collection("users").document(userId)
                .collection("sessions").document(sessionId)

            val existingSnap = sessionDoc.get().await()
            val data = mutableMapOf<String, Any>(
                "id" to sessionId,
                "userId" to userId,
                "title" to title.ifBlank { "محادثة مع رمسيس" }.take(200),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existingSnap.exists() || existingSnap.getTimestamp("createdAt") == null) {
                data["createdAt"] = FieldValue.serverTimestamp()
            }
            sessionDoc.set(data, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun renameSession(sessionId: String, newTitle: String, userId: String) {
        upsertSessionMetadata(sessionId = sessionId, title = newTitle, userId = userId)
    }

    suspend fun deleteSession(sessionId: String, userId: String) {
        val path = "users/$userId/sessions/$sessionId"
        try {
            val sessionDoc = db.collection("users").document(userId)
                .collection("sessions").document(sessionId)

            // Delete subcollection messages first so no orphaned message documents remain
            val messagesSnap = sessionDoc.collection("messages").get().await()
            for (slice in messagesSnap.documents.chunked(400)) {
                val batch = db.batch()
                slice.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            sessionDoc.delete().await()
            Log.d(TAG, "Session and messages deleted from Firestore: $sessionId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    suspend fun clearAllSessions(userId: String) {
        val path = "users/$userId/sessions"
        try {
            val sessionsSnap = db.collection("users").document(userId)
                .collection("sessions")
                .get()
                .await()
            for (doc in sessionsSnap.documents) {
                deleteSession(doc.id, userId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    suspend fun deleteMessage(sessionId: String, messageId: String, userId: String) {
        val path = "users/$userId/sessions/$sessionId/messages/$messageId"
        try {
            db.collection("users").document(userId)
                .collection("sessions").document(sessionId)
                .collection("messages").document(messageId)
                .delete()
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    suspend fun saveMessage(message: ChatMessageEntity, userId: String) {
        val path = "users/$userId/sessions/${message.sessionId}/messages/${message.id}"
        try {
            val messageDoc = db.collection("users").document(userId)
                .collection("sessions").document(message.sessionId)
                .collection("messages").document(message.id)

            val data = mapOf(
                "id" to message.id,
                "sessionId" to message.sessionId,
                "userId" to userId,
                "sender" to message.sender,
                "text" to message.text.take(20000),
                "timestamp" to FieldValue.serverTimestamp(),
                "isVoiceResponse" to message.isVoiceResponse,
                "isError" to message.isError
            )

            messageDoc.set(data).await()
            Log.d(TAG, "Message synced to Firestore: ${message.id}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }
}
