package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "محادثة جديدة",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val sender: String, // "USER" or "RAMSES"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isVoiceResponse: Boolean = false,
    val isError: Boolean = false,
    val groundingSourcesJson: String = "",
    val imageBase64: String? = null,
    val responderPersona: String = ""
)
