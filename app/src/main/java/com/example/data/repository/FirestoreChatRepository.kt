package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
                val dbId = context.applicationContext.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(dbId)
                instance ?: FirestoreChatRepository(firestore).also { instance = it }
            }
        }
    }

    suspend fun syncUserProfile(user: FirebaseUser) {
        try {
            val userDoc = db.collection("users").document(user.uid)
            val data = mutableMapOf<String, Any>(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: "زائر ملكي"),
                "lastActiveAt" to FieldValue.serverTimestamp()
            )
            user.email?.let { data["email"] = it }
            user.photoUrl?.let { data["photoUrl"] = it.toString() }

            userDoc.set(data, SetOptions.merge()).await()
            Log.d(TAG, "User profile synced to Firestore: ${user.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync user profile to Firestore", e)
        }
    }

    suspend fun saveSession(session: ChatSession, userId: String) {
        try {
            val sessionDoc = db.collection("users").document(userId)
                .collection("sessions").document(session.id)

            val data = mapOf(
                "id" to session.id,
                "userId" to userId,
                "title" to session.title,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            sessionDoc.set(data, SetOptions.merge()).await()
            Log.d(TAG, "Session synced to Firestore: ${session.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync session to Firestore", e)
        }
    }

    suspend fun renameSession(sessionId: String, newTitle: String, userId: String) {
        try {
            val sessionDoc = db.collection("users").document(userId)
                .collection("sessions").document(sessionId)

            val updates = mapOf(
                "title" to newTitle,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            sessionDoc.update(updates).await()
            Log.d(TAG, "Session renamed in Firestore: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename session in Firestore", e)
        }
    }

    suspend fun deleteSession(sessionId: String, userId: String) {
        try {
            val sessionDoc = db.collection("users").document(userId)
                .collection("sessions").document(sessionId)

            sessionDoc.delete().await()
            Log.d(TAG, "Session deleted from Firestore: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session from Firestore", e)
        }
    }

    suspend fun saveMessage(message: ChatMessageEntity, userId: String) {
        try {
            val messageDoc = db.collection("users").document(userId)
                .collection("sessions").document(message.sessionId)
                .collection("messages").document(message.id)

            val data = mapOf(
                "id" to message.id,
                "sessionId" to message.sessionId,
                "userId" to userId,
                "sender" to message.sender,
                "text" to message.text,
                "timestamp" to FieldValue.serverTimestamp(),
                "isVoiceResponse" to message.isVoiceResponse,
                "isError" to message.isError
            )

            messageDoc.set(data).await()
            Log.d(TAG, "Message synced to Firestore: ${message.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync message to Firestore", e)
        }
    }
}
