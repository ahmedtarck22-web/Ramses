package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * UI State representing the current status of a Gemini text prompt request.
 */
sealed interface GeminiPromptUiState {
    data object Idle : GeminiPromptUiState
    data object Loading : GeminiPromptUiState
    data class Success(
        val prompt: String,
        val responseText: String,
        val modelUsed: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : GeminiPromptUiState
    data class Error(
        val message: String,
        val isApiKeyMissing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : GeminiPromptUiState
}

/**
 * Record of previous prompt and response exchanges.
 */
data class PromptHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val response: String,
    val model: String = "gemini-2.0-flash",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ViewModel that handles sending text prompts to the Gemini API using OkHttp,
 * ensuring the API key is retrieved from BuildConfig.
 */
open class GeminiPromptViewModel(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    companion object {
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
        const val FALLBACK_MODEL = "gemini-3-flash-preview"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        private fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    private var activeRequestJob: Job? = null
    private var lastSentPrompt: String = ""

    // UI State
    private val _uiState = MutableStateFlow<GeminiPromptUiState>(GeminiPromptUiState.Idle)
    val uiState: StateFlow<GeminiPromptUiState> = _uiState.asStateFlow()

    // Current Prompt Input text
    private val _promptInput = MutableStateFlow("")
    val promptInput: StateFlow<String> = _promptInput.asStateFlow()

    // Selected Gemini Model
    private val _selectedModel = MutableStateFlow(DEFAULT_MODEL)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // History of sent prompts and responses
    private val _promptHistory = MutableStateFlow<List<PromptHistoryItem>>(emptyList())
    val promptHistory: StateFlow<List<PromptHistoryItem>> = _promptHistory.asStateFlow()

    /**
     * Retrieves the Gemini API key securely from BuildConfig.
     * Returns an empty string if not configured or if set to placeholder.
     */
    open fun getApiKeyFromBuildConfig(): String {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") {
                key.trim()
            } else {
                ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Checks if a valid API key is present in BuildConfig.
     */
    fun isApiKeyConfigured(): Boolean {
        return getApiKeyFromBuildConfig().isNotEmpty()
    }

    /**
     * Updates the current prompt input text.
     */
    fun updatePromptInput(text: String) {
        _promptInput.value = text
    }

    /**
     * Selects which Gemini model to use for subsequent prompts.
     */
    fun selectModel(modelName: String) {
        if (modelName.isNotBlank()) {
            _selectedModel.value = modelName
        }
    }

    /**
     * Sends a text prompt to the Gemini API using OkHttp and BuildConfig's API key.
     *
     * @param prompt The prompt to send. Defaults to [promptInput.value].
     */
    fun sendPrompt(prompt: String = _promptInput.value) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty()) {
            _uiState.value = GeminiPromptUiState.Error("Prompt cannot be empty.")
            return
        }

        // Retrieve and validate the API key from BuildConfig
        val apiKey = getApiKeyFromBuildConfig()
        if (apiKey.isEmpty()) {
            _uiState.value = GeminiPromptUiState.Error(
                message = "Gemini API key is not configured. Please add your GEMINI_API_KEY in the Secrets panel in AI Studio.",
                isApiKeyMissing = true
            )
            return
        }

        lastSentPrompt = trimmedPrompt
        activeRequestJob?.cancel()
        _uiState.value = GeminiPromptUiState.Loading

        activeRequestJob = viewModelScope.launch {
            try {
                val responseText = executePromptRequest(
                    prompt = trimmedPrompt,
                    apiKey = apiKey,
                    model = _selectedModel.value
                )

                _uiState.value = GeminiPromptUiState.Success(
                    prompt = trimmedPrompt,
                    responseText = responseText,
                    modelUsed = _selectedModel.value
                )

                // Add to history
                _promptHistory.value = _promptHistory.value + PromptHistoryItem(
                    prompt = trimmedPrompt,
                    response = responseText,
                    model = _selectedModel.value
                )

                // Clear input after successful dispatch
                _promptInput.value = ""
            } catch (e: Exception) {
                _uiState.value = GeminiPromptUiState.Error(
                    message = e.localizedMessage ?: "Failed to receive a response from Gemini API."
                )
            }
        }
    }

    /**
     * Retries the last sent prompt.
     */
    fun retryLastPrompt() {
        if (lastSentPrompt.isNotBlank()) {
            sendPrompt(lastSentPrompt)
        }
    }

    /**
     * Cancels any currently ongoing network request.
     */
    fun cancelCurrentRequest() {
        activeRequestJob?.cancel()
        if (_uiState.value is GeminiPromptUiState.Loading) {
            _uiState.value = GeminiPromptUiState.Idle
        }
    }

    /**
     * Resets the UI state back to Idle.
     */
    fun clearState() {
        _uiState.value = GeminiPromptUiState.Idle
    }

    /**
     * Clears all recorded prompt history.
     */
    fun clearHistory() {
        _promptHistory.value = emptyList()
    }

    /**
     * Executes the HTTP POST request to the Gemini generateContent API via OkHttp.
     */
    private suspend fun executePromptRequest(
        prompt: String,
        apiKey: String,
        model: String
    ): String = withContext(ioDispatcher) {
        val targetModel = when (model) {
            "gemini-1.5-pro" -> "gemini-3.5-flash"
            "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-2.0-flash-lite", "gemini-2.5-flash" -> "gemini-3.1-flash-lite"
            else -> if (model.isNotBlank()) model else "gemini-3.1-flash-lite"
        }
        val endpointUrl = "$BASE_URL/$targetModel:generateContent?key=$apiKey"

        // Construct Gemini JSON payload:
        // {
        //   "contents": [
        //     {
        //       "role": "user",
        //       "parts": [{ "text": "..." }]
        //     }
        //   ],
        //   "generationConfig": {
        //     "temperature": 0.7,
        //     "maxOutputTokens": 2048
        //   }
        // }
        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val userContent = JSONObject().apply {
                    put("role", "user")
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    }
                    put("parts", partsArray)
                }
                put(userContent)
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            }
            put("generationConfig", generationConfig)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpointUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMessage = parseApiErrorMessage(responseBody, response.code)
            throw IOException(errorMessage)
        }

        parseGeneratedText(responseBody)
    }

    /**
     * Parses the text response from the Gemini API JSON structure.
     */
    private fun parseGeneratedText(jsonString: String): String {
        val json = JSONObject(jsonString)
        val candidates = json.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                val textBuilder = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val partText = part.optString("text", "")
                    textBuilder.append(partText)
                }
                val result = textBuilder.toString().trim()
                if (result.isNotEmpty()) {
                    return result
                }
            }
        }
        return "No text content generated."
    }

    /**
     * Parses error messages returned by the Gemini API into user-friendly error strings.
     */
    private fun parseApiErrorMessage(responseBody: String, statusCode: Int): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message")
            if (!message.isNullOrBlank()) {
                "Gemini API Error ($statusCode): $message"
            } else {
                "Gemini API request failed with HTTP $statusCode."
            }
        } catch (_: Exception) {
            "Gemini API request failed with HTTP $statusCode: ${responseBody.take(120)}"
        }
    }
}
