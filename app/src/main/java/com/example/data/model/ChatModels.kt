package com.example.data.model

import java.util.UUID

enum class MessageSender {
    USER,
    RAMSES
}

enum class VoiceCallState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class GroundingSource(
    val title: String,
    val uri: String
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBase64: String? = null,
    val groundingSources: List<GroundingSource> = emptyList(),
    val isVoiceResponse: Boolean = false,
    val isError: Boolean = false
)
