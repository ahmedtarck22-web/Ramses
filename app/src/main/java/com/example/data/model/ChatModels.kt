package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject
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
) {
    companion object {
        fun toJsonString(sources: List<GroundingSource>): String {
            if (sources.isEmpty()) return ""
            val arr = JSONArray()
            for (src in sources) {
                arr.put(
                    JSONObject().apply {
                        put("title", src.title)
                        put("uri", src.uri)
                    }
                )
            }
            return arr.toString()
        }

        fun fromJsonString(raw: String?): List<GroundingSource> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    val title = obj.optString("title", "")
                    val uri = obj.optString("uri", "")
                    if (title.isBlank() && uri.isBlank()) null else GroundingSource(title, uri)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

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
