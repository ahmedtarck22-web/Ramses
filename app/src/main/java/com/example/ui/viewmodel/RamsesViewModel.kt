package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiClient
import com.example.data.db.AppDatabase
import com.example.data.model.AppLanguage
import com.example.data.model.AuthProvider
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.example.data.model.MessageSender
import com.example.data.model.RagDocument
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCallState
import com.example.service.SpeechRecognizerHelper
import com.example.service.TextToSpeechHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class RamsesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("ramses_ai_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getInstance(application)
    private val chatDao = db.chatDao()
    private val ragDao = db.ragDao()
    private val geminiClient = GeminiApiClient()

    val ttsHelper = TextToSpeechHelper(application)
    val speechHelper = SpeechRecognizerHelper(application)

    // Language setting (Arabic / English)
    private val _appLanguage = MutableStateFlow(
        if (prefs.getString("app_language", "AR") == "EN") AppLanguage.ENGLISH else AppLanguage.ARABIC
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    // Current Chat Session
    private val _currentSessionId = MutableStateFlow(
        prefs.getString("current_session_id", null) ?: UUID.randomUUID().toString()
    )
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    // Sessions List
    val sessions: StateFlow<List<ChatSession>> = chatDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Messages for Current Session
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // RAG Documents List
    val ragDocuments: StateFlow<List<RagDocument>> = ragDao.getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSearchGroundingEnabled = MutableStateFlow(false)
    val isSearchGroundingEnabled: StateFlow<Boolean> = _isSearchGroundingEnabled.asStateFlow()

    private val _selectedImageBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedImageBitmap: StateFlow<Bitmap?> = _selectedImageBitmap.asStateFlow()

    private val _customApiKey = MutableStateFlow("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    // Voice Call (Gemini Live) State
    private val _isGeminiLiveActive = MutableStateFlow(false)
    val isGeminiLiveActive: StateFlow<Boolean> = _isGeminiLiveActive.asStateFlow()

    private val _voiceCallState = MutableStateFlow(VoiceCallState.IDLE)
    val voiceCallState: StateFlow<VoiceCallState> = _voiceCallState.asStateFlow()

    private val _currentVoiceTranscript = MutableStateFlow("")
    val currentVoiceTranscript: StateFlow<String> = _currentVoiceTranscript.asStateFlow()

    // Voice typing mode (Mic button in input bar)
    private val _isVoiceTyping = MutableStateFlow(false)
    val isVoiceTyping: StateFlow<Boolean> = _isVoiceTyping.asStateFlow()

    // User authentication profile state
    private val _userProfile = MutableStateFlow(
        UserProfile(
            email = prefs.getString("user_email", null),
            displayName = prefs.getString("user_display_name", "زائر ملكي") ?: "زائر ملكي",
            provider = when (prefs.getString("user_provider", "GUEST")) {
                "GOOGLE" -> AuthProvider.GOOGLE
                "APPLE" -> AuthProvider.APPLE
                else -> AuthProvider.GUEST
            },
            isSignedIn = prefs.getBoolean("user_is_signed_in", false)
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private var messageCollectorJob: Job? = null

    init {
        _customApiKey.value = prefs.getString("custom_gemini_key", "") ?: ""
        _isSearchGroundingEnabled.value = prefs.getBoolean("search_grounding", false)

        viewModelScope.launch {
            seedDefaultRagDocuments()
            ensureCurrentSessionExists(_currentSessionId.value)
            observeMessagesForSession(_currentSessionId.value)
        }

        // Wire TTS state to Voice Call state
        viewModelScope.launch {
            ttsHelper.isSpeaking.collect { speaking ->
                if (_isGeminiLiveActive.value) {
                    if (speaking) {
                        _voiceCallState.value = VoiceCallState.SPEAKING
                    }
                }
            }
        }

        // Wire Speech recognition callbacks
        speechHelper.onSpeechCompleted = { text ->
            if (_isVoiceTyping.value) {
                _isVoiceTyping.value = false
                if (text.isNotBlank()) {
                    sendMessage(text)
                }
            } else if (_isGeminiLiveActive.value) {
                _currentVoiceTranscript.value = text
                sendMessage(text, isVoice = true)
            }
        }

        speechHelper.onEndOfSpeechDetected = {
            if (_isGeminiLiveActive.value && _voiceCallState.value == VoiceCallState.LISTENING) {
                _voiceCallState.value = VoiceCallState.THINKING
            }
        }

        speechHelper.onErrorOccurred = { errorMsg, errorCode ->
            Log.d("RamsesVM", "Speech error: $errorMsg (code $errorCode)")
            if (_isVoiceTyping.value) {
                _isVoiceTyping.value = false
            }
            if (_isGeminiLiveActive.value) {
                // If the error was just no speech or timeout, stay ready and restart listening
                viewModelScope.launch {
                    delay(400)
                    if (_isGeminiLiveActive.value && _voiceCallState.value != VoiceCallState.SPEAKING && _voiceCallState.value != VoiceCallState.THINKING) {
                        _voiceCallState.value = VoiceCallState.LISTENING
                        val langCode = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
                        speechHelper.startListening(langCode)
                    }
                }
            }
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _appLanguage.value = lang
        prefs.edit().putString("app_language", if (lang == AppLanguage.ENGLISH) "EN" else "AR").apply()
        // If voice call is active, restart speech recognizer in chosen language
        if (_isGeminiLiveActive.value && _voiceCallState.value == VoiceCallState.LISTENING) {
            val langCode = if (lang == AppLanguage.ENGLISH) "en-US" else "ar-EG"
            speechHelper.startListening(langCode)
        }
    }

    fun toggleLanguage() {
        val next = if (_appLanguage.value == AppLanguage.ARABIC) AppLanguage.ENGLISH else AppLanguage.ARABIC
        setLanguage(next)
    }

    private suspend fun ensureCurrentSessionExists(sessionId: String) {
        val sessionTitle = if (_appLanguage.value == AppLanguage.ARABIC) "محادثة مع رمسيس" else "Chat with Rameses"
        val session = ChatSession(
            id = sessionId,
            title = sessionTitle,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        chatDao.insertSession(session)
        prefs.edit().putString("current_session_id", sessionId).apply()
    }

    private fun observeMessagesForSession(sessionId: String) {
        messageCollectorJob?.cancel()
        messageCollectorJob = viewModelScope.launch {
            chatDao.getMessagesForSession(sessionId).collect { entities ->
                _messages.value = entities.map { entity ->
                    ChatMessage(
                        id = entity.id,
                        sender = if (entity.sender == "USER") MessageSender.USER else MessageSender.RAMSES,
                        text = entity.text,
                        timestamp = entity.timestamp,
                        isVoiceResponse = entity.isVoiceResponse,
                        isError = entity.isError
                    )
                }
            }
        }
    }

    private suspend fun seedDefaultRagDocuments() {
        val existing = ragDao.getActiveDocuments()
        if (existing.isEmpty()) {
            val doc1 = RagDocument(
                title = "معاهدة السلام المصرية الحيثية (أول معاهدة سلام في التاريخ)",
                content = "أُبرمت في عهد الملك رمسيس الثاني مع الملك الحيثي خاتوسيلي الثالث بعد معركة قادش عام 1259 ق.م، ونصت على إنهاء الحرب وإقامة حلف دفاعي مشترك وتبادل الأسرى ونُقشت باللغتين المصرية الهيروغليفية والأكادية المسمارية.",
                category = "تاريخ ومعاهدات"
            )
            val doc2 = RagDocument(
                title = "معبد أبو سمبل وتعامد الشمس",
                content = "شيده الملك رمسيس في النوبة، ويتميز بظاهرة فلكية وهندسية فريدة حيث تتعامد أشعة الشمس على وجه تمثال الملك داخل قدس الأقداس مرتين كل عام (يوم تتويجه 22 فبراير ويوم ميلاده 22 أكتوبر).",
                category = "عمارة وهندسة"
            )
            val doc3 = RagDocument(
                title = "المدينة العاصمة بر-رعمسيس",
                content = "أنشأها رمسيس في شرق الدلتا وجعلها مقراً للحكم والجيش، وازدهرت بحدائقها وقصورها ومصانع الأسلحة كمركز استراتيجي للتحكم في الطرق التجارية والعسكرية نحو بلاد الشام.",
                category = "مدن وتخطيط"
            )
            ragDao.insertDocument(doc1)
            ragDao.insertDocument(doc2)
            ragDao.insertDocument(doc3)
        }
    }

    // --- RAG Document Management ---
    fun addRagDocument(title: String, content: String, category: String = "عام") {
        viewModelScope.launch {
            val newDoc = RagDocument(
                title = title,
                content = content,
                category = category,
                isEnabled = true
            )
            ragDao.insertDocument(newDoc)
        }
    }

    fun toggleRagDocument(document: RagDocument) {
        viewModelScope.launch {
            ragDao.updateDocument(document.copy(isEnabled = !document.isEnabled))
        }
    }

    fun deleteRagDocument(id: String) {
        viewModelScope.launch {
            ragDao.deleteDocument(id)
        }
    }

    // --- Session Management ---
    fun startNewSession() {
        val newId = UUID.randomUUID().toString()
        _currentSessionId.value = newId
        viewModelScope.launch {
            ensureCurrentSessionExists(newId)
            observeMessagesForSession(newId)
        }
    }

    fun selectSession(sessionId: String) {
        _currentSessionId.value = sessionId
        prefs.edit().putString("current_session_id", sessionId).apply()
        observeMessagesForSession(sessionId)
    }

    fun switchSession(sessionId: String) = selectSession(sessionId)

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatDao.deleteSession(sessionId)
            chatDao.deleteMessagesForSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                startNewSession()
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            chatDao.clearAllMessages()
            chatDao.clearAllSessions()
            startNewSession()
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatDao.deleteMessage(messageId)
        }
    }

    // --- High-Speed Message Sending (with Real-Time Streaming) ---
    fun sendMessage(promptText: String, isVoice: Boolean = false) {
        val trimmed = promptText.trim()
        val image = _selectedImageBitmap.value
        if (trimmed.isEmpty() && image == null) return

        val sessionId = _currentSessionId.value
        val userText = trimmed.ifEmpty {
            if (_appLanguage.value == AppLanguage.ARABIC) "حلل هذه الصورة الملكية" else "Analyze this royal image"
        }

        // Save User Message to Database
        val userEntity = ChatMessageEntity(
            sessionId = sessionId,
            sender = "USER",
            text = userText
        )

        viewModelScope.launch {
            chatDao.insertMessage(userEntity)
            chatDao.updateSession(
                ChatSession(
                    id = sessionId,
                    title = userText.take(30),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // Reset inputs
        _selectedImageBitmap.value = null
        _isLoading.value = true
        if (isVoice) {
            _voiceCallState.value = VoiceCallState.THINKING
        }

        viewModelScope.launch {
            val tempRamsesId = UUID.randomUUID().toString()
            val placeholderEntity = ChatMessageEntity(
                id = tempRamsesId,
                sessionId = sessionId,
                sender = "RAMSES",
                text = "",
                isVoiceResponse = isVoice,
                isError = false
            )

            // Insert placeholder so Compose updates immediately
            chatDao.insertMessage(placeholderEntity)

            val activeRagDocs = ragDao.getActiveDocuments()
            var currentLiveChunk = ""

            val result = geminiClient.streamRamsesResponse(
                prompt = userText,
                history = _messages.value.filter { it.id != tempRamsesId },
                imageBitmap = image,
                enableSearchGrounding = _isSearchGroundingEnabled.value,
                isVoiceCall = isVoice,
                customApiKey = _customApiKey.value,
                ragContext = activeRagDocs,
                appLanguage = _appLanguage.value,
                onChunk = { chunk ->
                    currentLiveChunk = chunk
                    chatDao.updateMessage(
                        placeholderEntity.copy(
                            text = chunk,
                            isError = false
                        )
                    )
                }
            )

            val finalText = if (result.text.isNotBlank()) result.text else currentLiveChunk
            val finalEntity = placeholderEntity.copy(
                text = finalText,
                isError = result.isError
            )
            chatDao.updateMessage(finalEntity)

            _isLoading.value = false

            if (isVoice) {
                _voiceCallState.value = VoiceCallState.SPEAKING
                val langTag = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
                ttsHelper.speak(finalText, languageCode = langTag) {
                    // Continuous Live conversation loop: resume listening once speech concludes
                    if (_isGeminiLiveActive.value) {
                        _voiceCallState.value = VoiceCallState.LISTENING
                        speechHelper.startListening(langTag)
                    }
                }
            }
        }
    }

    // --- Voice Typing (Mic Button in Chat) ---
    fun startVoiceTyping() {
        if (!_isVoiceTyping.value) {
            _isVoiceTyping.value = true
            ttsHelper.stop()
            val langTag = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
            speechHelper.startListening(langTag)
        }
    }

    fun stopVoiceTyping() {
        if (_isVoiceTyping.value) {
            _isVoiceTyping.value = false
            speechHelper.stopListening()
        }
    }

    // --- Voice Call (Gemini Live) ---
    fun startGeminiLiveCall() {
        _isGeminiLiveActive.value = true
        _voiceCallState.value = VoiceCallState.LISTENING
        _currentVoiceTranscript.value = ""
        ttsHelper.stop()
        val langTag = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
        speechHelper.startListening(langTag)
    }

    fun endGeminiLiveCall() {
        _isGeminiLiveActive.value = false
        _voiceCallState.value = VoiceCallState.IDLE
        ttsHelper.stop()
        speechHelper.stopListening()
    }

    fun startListeningForGeminiLive() {
        _voiceCallState.value = VoiceCallState.LISTENING
        ttsHelper.stop()
        val langTag = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
        speechHelper.startListening(langTag)
    }

    fun stopListeningForGeminiLive() {
        speechHelper.stopListening()
        if (_voiceCallState.value == VoiceCallState.LISTENING) {
            _voiceCallState.value = VoiceCallState.IDLE
        }
    }

    // --- Helpers ---
    fun attachImage(bitmap: Bitmap) {
        _selectedImageBitmap.value = bitmap
    }

    fun clearAttachedImage() {
        _selectedImageBitmap.value = null
    }

    fun toggleSearchGrounding() {
        val newVal = !_isSearchGroundingEnabled.value
        _isSearchGroundingEnabled.value = newVal
        prefs.edit().putBoolean("search_grounding", newVal).apply()
    }

    fun speakMessage(text: String) {
        val langTag = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
        ttsHelper.speak(text, languageCode = langTag)
    }

    fun stopSpeaking() {
        ttsHelper.stop()
    }

    fun setCustomApiKey(key: String) {
        _customApiKey.value = key
        prefs.edit().putString("custom_gemini_key", key).apply()
    }

    fun signInWithGoogle(email: String = "ahmedtarck13@gmail.com", name: String = "أحمد") {
        val profile = UserProfile(
            email = email,
            displayName = name,
            provider = AuthProvider.GOOGLE,
            isSignedIn = true
        )
        _userProfile.value = profile
        prefs.edit()
            .putString("user_email", email)
            .putString("user_display_name", name)
            .putString("user_provider", "GOOGLE")
            .putBoolean("user_is_signed_in", true)
            .apply()
    }

    fun signInWithApple(email: String = "ramses.user@icloud.com", name: String = "Apple User") {
        val profile = UserProfile(
            email = email,
            displayName = name,
            provider = AuthProvider.APPLE,
            isSignedIn = true
        )
        _userProfile.value = profile
        prefs.edit()
            .putString("user_email", email)
            .putString("user_display_name", name)
            .putString("user_provider", "APPLE")
            .putBoolean("user_is_signed_in", true)
            .apply()
    }

    fun signOut() {
        val guest = UserProfile(
            email = null,
            displayName = if (_appLanguage.value == AppLanguage.ARABIC) "زائر ملكي" else "Royal Guest",
            provider = AuthProvider.GUEST,
            isSignedIn = false
        )
        _userProfile.value = guest
        prefs.edit()
            .remove("user_email")
            .putString("user_display_name", guest.displayName)
            .putString("user_provider", "GUEST")
            .putBoolean("user_is_signed_in", false)
            .apply()
    }

    override fun onCleared() {
        super.onCleared()
        messageCollectorJob?.cancel()
        ttsHelper.shutdown()
        speechHelper.stopListening()
    }
}
