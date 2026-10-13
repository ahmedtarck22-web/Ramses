package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiClient
import com.example.data.db.AppDatabase
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

    // Voice typing mode (Mic button)
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

    init {
        _customApiKey.value = prefs.getString("custom_gemini_key", "") ?: ""
        _isSearchGroundingEnabled.value = prefs.getBoolean("search_grounding", false)

        // Seed default RAG knowledge documents if empty
        viewModelScope.launch {
            seedDefaultRagDocuments()
            ensureCurrentSessionExists(_currentSessionId.value)
            loadMessagesForSession(_currentSessionId.value)
        }

        // Wire TTS state to Voice Call state
        viewModelScope.launch {
            ttsHelper.isSpeaking.collect { speaking ->
                if (_isGeminiLiveActive.value) {
                    if (speaking) {
                        _voiceCallState.value = VoiceCallState.SPEAKING
                    } else if (_voiceCallState.value == VoiceCallState.SPEAKING) {
                        _voiceCallState.value = VoiceCallState.IDLE
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

        speechHelper.onErrorOccurred = { _ ->
            if (_isVoiceTyping.value) {
                _isVoiceTyping.value = false
            }
            if (_isGeminiLiveActive.value) {
                _voiceCallState.value = VoiceCallState.IDLE
            }
        }
    }

    private suspend fun ensureCurrentSessionExists(sessionId: String) {
        val session = ChatSession(
            id = sessionId,
            title = "محادثة مع رمسيس",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        chatDao.insertSession(session)
        prefs.edit().putString("current_session_id", sessionId).apply()
    }

    private fun loadMessagesForSession(sessionId: String) {
        viewModelScope.launch {
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
                content = "أُبرمت في عهد الملك رمسيس مع الملك الحيثي خاتوسيلي الثالث بعد معركة قادش عام 1259 ق.م، ونصت على إنهاء الحرب وإقامة حلف دفاعي مشترك وتبادل الأسرى ونُقشت باللغتين المصرية الهيروغليفية والأكادية المسمارية.",
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

    fun toggleRagDocument(doc: RagDocument) {
        viewModelScope.launch {
            ragDao.updateDocument(doc.copy(isEnabled = !doc.isEnabled))
        }
    }

    fun deleteRagDocument(id: String) {
        viewModelScope.launch {
            ragDao.deleteDocument(id)
        }
    }

    // --- Chat Session Management ---
    fun startNewSession() {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            _currentSessionId.value = newId
            ensureCurrentSessionExists(newId)
            loadMessagesForSession(newId)
            ttsHelper.stop()
        }
    }

    fun switchSession(sessionId: String) {
        _currentSessionId.value = sessionId
        prefs.edit().putString("current_session_id", sessionId).apply()
        loadMessagesForSession(sessionId)
        ttsHelper.stop()
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatDao.deleteSession(sessionId)
            chatDao.deleteMessagesForSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                startNewSession()
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatDao.deleteMessage(messageId)
        }
    }

    // --- Message Sending ---
    fun sendMessage(promptText: String, isVoice: Boolean = false) {
        val trimmed = promptText.trim()
        val image = _selectedImageBitmap.value
        if (trimmed.isEmpty() && image == null) return

        val sessionId = _currentSessionId.value
        val userText = trimmed.ifEmpty { "حلل هذه الصورة الملكية" }

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
            // Fetch active RAG context documents
            val activeRagDocs = ragDao.getActiveDocuments()

            val result = geminiClient.generateRamsesResponse(
                prompt = userText,
                history = _messages.value,
                imageBitmap = image,
                enableSearchGrounding = _isSearchGroundingEnabled.value,
                isVoiceCall = isVoice,
                customApiKey = _customApiKey.value,
                ragContext = activeRagDocs
            )

            // Save Ramses Message to Database
            val ramsesEntity = ChatMessageEntity(
                sessionId = sessionId,
                sender = "RAMSES",
                text = result.text,
                isVoiceResponse = isVoice,
                isError = result.isError
            )
            chatDao.insertMessage(ramsesEntity)

            _isLoading.value = false

            if (isVoice) {
                _voiceCallState.value = VoiceCallState.SPEAKING
                ttsHelper.speak(result.text)
            }
        }
    }

    // --- Voice Typing (Mic Button) ---
    fun startVoiceTyping() {
        if (!_isVoiceTyping.value) {
            _isVoiceTyping.value = true
            ttsHelper.stop()
            speechHelper.startListening()
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
        _voiceCallState.value = VoiceCallState.IDLE
        ttsHelper.stop()
        speechHelper.stopListening()
    }

    fun endGeminiLiveCall() {
        _isGeminiLiveActive.value = false
        _voiceCallState.value = VoiceCallState.IDLE
        ttsHelper.stop()
        speechHelper.stopListening()
    }

    fun startListeningForGeminiLive() {
        if (_voiceCallState.value != VoiceCallState.LISTENING) {
            _voiceCallState.value = VoiceCallState.LISTENING
            ttsHelper.stop()
            speechHelper.startListening()
        }
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
        ttsHelper.speak(text)
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

    fun signInWithApple(email: String = "ramses.user@icloud.com", name: String = "مستخدم Apple") {
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
            displayName = "زائر ملكي",
            provider = AuthProvider.GUEST,
            isSignedIn = false
        )
        _userProfile.value = guest
        prefs.edit()
            .remove("user_email")
            .putString("user_display_name", "زائر ملكي")
            .putString("user_provider", "GUEST")
            .putBoolean("user_is_signed_in", false)
            .apply()
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
        speechHelper.stopListening()
    }
}
