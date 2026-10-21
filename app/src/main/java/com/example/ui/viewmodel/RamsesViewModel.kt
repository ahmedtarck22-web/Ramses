package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
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
import com.example.data.repository.FirestoreChatRepository
import com.example.firebase.FirebaseAuthManager
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

    private val authManager by lazy { FirebaseAuthManager.getInstance(application) }
    private val firestoreRepo by lazy { FirestoreChatRepository.getInstance(application) }

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

    // 3D Avatar Split View mode (Top half 3D avatar, Bottom half text conversation)
    private val _is3DViewMode = MutableStateFlow(true)
    val is3DViewMode: StateFlow<Boolean> = _is3DViewMode.asStateFlow()

    // Auto-speak responses in 3D Avatar mode so Ramses speaks with lipsync and body movement
    private val _isAutoSpeakEnabled = MutableStateFlow(true)
    val isAutoSpeakEnabled: StateFlow<Boolean> = _isAutoSpeakEnabled.asStateFlow()

    val isSpeaking: StateFlow<Boolean> = ttsHelper.isSpeaking

    // Decoupled in-memory live streaming message for 60/120 FPS buttery smooth rendering
    private val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
    val streamingMessage: StateFlow<ChatMessage?> = _streamingMessage.asStateFlow()

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

    // Selected Gemini Model (gemini-2.0-flash / gemini-1.5-pro)
    private val _selectedModel = MutableStateFlow(
        prefs.getString("selected_gemini_model", "gemini-2.0-flash") ?: "gemini-2.0-flash"
    )
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // Selected Gem Persona: "RAMSES", "GEMINI_DEFAULT", "CODE", "WRITER"
    private val _selectedGem = MutableStateFlow(
        prefs.getString("selected_gem", "GEMINI_DEFAULT") ?: "GEMINI_DEFAULT"
    )
    val selectedGem: StateFlow<String> = _selectedGem.asStateFlow()

    private var messageCollectorJob: Job? = null
    private var currentMessageJob: Job? = null

    init {
        _customApiKey.value = prefs.getString("custom_gemini_key", "") ?: ""
        _isSearchGroundingEnabled.value = prefs.getBoolean("search_grounding", false)
        GeminiApiClient.preferredModel = _selectedModel.value

        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                if (user != null) {
                    _userProfile.value = UserProfile(
                        email = user.email,
                        displayName = user.displayName ?: "مستخدم مسجل",
                        provider = AuthProvider.GOOGLE,
                        isSignedIn = true,
                        avatarUrl = user.photoUrl?.toString()
                    )
                    firestoreRepo.syncUserProfile(user)
                }
            }
        }

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
                // Safely recover state to LISTENING without hammering recognizer in an infinite error loop
                viewModelScope.launch {
                    delay(300)
                    if (_isGeminiLiveActive.value && _voiceCallState.value != VoiceCallState.SPEAKING) {
                        _voiceCallState.value = VoiceCallState.LISTENING
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
        val existing = chatDao.getSessionById(sessionId)
        if (existing == null) {
            val sessionTitle = if (_appLanguage.value == AppLanguage.ARABIC) "محادثة مع رمسيس" else "Chat with Rameses"
            val session = ChatSession(
                id = sessionId,
                title = sessionTitle,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            chatDao.insertSession(session)
            val uid = authManager.currentUser.value?.uid
            if (uid != null) {
                firestoreRepo.saveSession(session, uid)
            }
        }
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
        currentMessageJob?.cancel()
        _streamingMessage.value = null
        _isLoading.value = false
        val newId = UUID.randomUUID().toString()
        _currentSessionId.value = newId
        viewModelScope.launch {
            ensureCurrentSessionExists(newId)
            observeMessagesForSession(newId)
        }
    }

    fun selectSession(sessionId: String) {
        if (_currentSessionId.value == sessionId) return
        currentMessageJob?.cancel()
        _streamingMessage.value = null
        _isLoading.value = false
        _currentSessionId.value = sessionId
        prefs.edit().putString("current_session_id", sessionId).apply()
        observeMessagesForSession(sessionId)
    }

    fun switchSession(sessionId: String) = selectSession(sessionId)

    fun renameSession(sessionId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            try {
                chatDao.renameSession(sessionId, newTitle.trim())
                val uid = authManager.currentUser.value?.uid
                if (uid != null) {
                    firestoreRepo.renameSession(sessionId, newTitle.trim(), uid)
                }
            } catch (e: Exception) {
                Log.e("RamsesVM", "Failed to rename session", e)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        if (_currentSessionId.value == sessionId) {
            currentMessageJob?.cancel()
            _streamingMessage.value = null
            _isLoading.value = false
        }
        viewModelScope.launch {
            chatDao.deleteSession(sessionId)
            chatDao.deleteMessagesForSession(sessionId)
            val uid = authManager.currentUser.value?.uid
            if (uid != null) {
                firestoreRepo.deleteSession(sessionId, uid)
            }
            if (_currentSessionId.value == sessionId) {
                startNewSession()
            }
        }
    }

    fun clearAllConversations() {
        currentMessageJob?.cancel()
        _streamingMessage.value = null
        _isLoading.value = false
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

    fun selectModel(modelName: String) {
        _selectedModel.value = modelName
        GeminiApiClient.preferredModel = modelName
        prefs.edit().putString("selected_gemini_model", modelName).apply()
    }

    fun selectGem(gemId: String) {
        _selectedGem.value = gemId
        prefs.edit().putString("selected_gem", gemId).apply()
        if (gemId == "RAMSES") {
            _is3DViewMode.value = true
        } else {
            _is3DViewMode.value = false
        }
        startNewSession()
    }

    // --- High-Speed Message Sending (with Real-Time Streaming) ---
    fun sendMessage(promptText: String, isVoice: Boolean = false) {
        val trimmed = promptText.trim()
        val image = _selectedImageBitmap.value
        if (trimmed.isEmpty() && image == null) return

        // Cancel any pending/concurrent message generation cleanly to prevent race conditions and duplicate messages
        currentMessageJob?.cancel()

        val sessionId = _currentSessionId.value
        val userText = trimmed.ifEmpty {
            if (_appLanguage.value == AppLanguage.ARABIC) "حلل هذه الصورة الملكية" else "Analyze this royal image"
        }

        // Reset inputs
        _selectedImageBitmap.value = null
        _isLoading.value = true
        if (isVoice) {
            _voiceCallState.value = VoiceCallState.THINKING
        }

        currentMessageJob = viewModelScope.launch {
            try {
                // 1. Save User Message to Database synchronously in order
                val userEntity = ChatMessageEntity(
                    sessionId = sessionId,
                    sender = "USER",
                    text = userText,
                    timestamp = System.currentTimeMillis()
                )
                chatDao.insertMessage(userEntity)
                val uid = authManager.currentUser.value?.uid
                if (uid != null) {
                    firestoreRepo.saveMessage(userEntity, uid)
                }

                // 2. Update session title & timestamp without altering original createdAt
                chatDao.updateSessionTitleAndTimestamp(
                    sessionId = sessionId,
                    title = userText.take(40),
                    updatedAt = System.currentTimeMillis()
                )

                // 3. Prepare streaming placeholder in memory
                val tempRamsesId = UUID.randomUUID().toString()
                _streamingMessage.value = ChatMessage(
                    id = tempRamsesId,
                    sender = MessageSender.RAMSES,
                    text = "",
                    timestamp = System.currentTimeMillis(),
                    isVoiceResponse = isVoice,
                    isError = false
                )

                val activeRagDocs = ragDao.getActiveDocuments()
                var currentLiveChunk = ""

                val result = geminiClient.streamRamsesResponse(
                    prompt = userText,
                    history = _messages.value,
                    imageBitmap = image,
                    enableSearchGrounding = _isSearchGroundingEnabled.value,
                    isVoiceCall = isVoice,
                    customApiKey = _customApiKey.value,
                    ragContext = activeRagDocs,
                    appLanguage = _appLanguage.value,
                    gemPersona = _selectedGem.value,
                    onChunk = { chunk ->
                        currentLiveChunk = chunk
                        _streamingMessage.value = _streamingMessage.value?.copy(text = chunk)
                    }
                )

                val finalText = if (result.text.isNotBlank()) result.text else currentLiveChunk
                val finalEntity = ChatMessageEntity(
                    id = tempRamsesId,
                    sessionId = sessionId,
                    sender = "RAMSES",
                    text = finalText,
                    timestamp = System.currentTimeMillis(),
                    isVoiceResponse = isVoice,
                    isError = result.isError
                )

                // Persist the final response exactly ONCE
                chatDao.insertMessage(finalEntity)
                if (uid != null) {
                    firestoreRepo.saveMessage(finalEntity, uid)
                }
                _streamingMessage.value = null
                _isLoading.value = false

                if (isVoice) {
                    _voiceCallState.value = VoiceCallState.SPEAKING
                    val langTag = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
                    ttsHelper.speak(finalText, languageCode = langTag) {
                        if (_isGeminiLiveActive.value) {
                            _voiceCallState.value = VoiceCallState.LISTENING
                            speechHelper.startListening(langTag)
                        }
                    }
                } else if (_is3DViewMode.value && _isAutoSpeakEnabled.value && finalText.isNotBlank() && !result.isError) {
                    val langTag = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
                    ttsHelper.speak(finalText.take(600), languageCode = langTag)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _streamingMessage.value = null
                    _isLoading.value = false
                    throw e
                }
                Log.e("RamsesVM", "Message generation error", e)
                _streamingMessage.value = null
                _isLoading.value = false

                // Inform the user instead of leaving an empty lost response
                val isArabic = _appLanguage.value == AppLanguage.ARABIC
                val errorMsg = if (isArabic) {
                    "حدث خطأ أثناء معالجة الطلب: ${e.localizedMessage ?: "خطأ غير متوقع في الاتصال"}"
                } else {
                    "An error occurred while processing your request: ${e.localizedMessage ?: "Unexpected connection error"}"
                }
                val errorEntity = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    sender = "RAMSES",
                    text = errorMsg,
                    timestamp = System.currentTimeMillis(),
                    isVoiceResponse = isVoice,
                    isError = true
                )
                try {
                    chatDao.insertMessage(errorEntity)
                } catch (dbEx: Exception) {
                    Log.e("RamsesVM", "Failed to persist error message", dbEx)
                }
                if (isVoice) {
                    _voiceCallState.value = VoiceCallState.IDLE
                }
            }
        }
    }

    fun toggle3DViewMode() {
        _is3DViewMode.value = !_is3DViewMode.value
    }

    fun set3DViewMode(enabled: Boolean) {
        _is3DViewMode.value = enabled
    }

    fun toggleAutoSpeak() {
        _isAutoSpeakEnabled.value = !_isAutoSpeakEnabled.value
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
        _voiceCallState.value = VoiceCallState.SPEAKING
        _currentVoiceTranscript.value = ""
        ttsHelper.stop()
        speechHelper.stopListening()

        val isEnglish = _appLanguage.value == AppLanguage.ENGLISH
        val greeting = if (isEnglish) {
            "Greetings, I am listening."
        } else {
            "مرحباً بك، أنا أستمع إليك."
        }
        val langTag = if (isEnglish) "en-US" else "ar-EG"

        // Speak ultra-short royal greeting then start listening immediately
        ttsHelper.speak(greeting, languageCode = langTag) {
            if (_isGeminiLiveActive.value) {
                _voiceCallState.value = VoiceCallState.LISTENING
                speechHelper.startListening(langTag)
            }
        }
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

    fun sendLiveVoicePrompt(promptText: String) {
        _currentVoiceTranscript.value = promptText
        sendMessage(promptText, isVoice = true)
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

    fun launchGoogleSignIn(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = authManager.signInWithGoogle(activity)
            _isLoading.value = false
            if (result.isSuccess) {
                val user = result.getOrThrow()
                _userProfile.value = UserProfile(
                    email = user.email,
                    displayName = user.displayName ?: "مستخدم مسجل",
                    provider = AuthProvider.GOOGLE,
                    isSignedIn = true,
                    avatarUrl = user.photoUrl?.toString()
                )
                firestoreRepo.syncUserProfile(user)
                onComplete(true, null)
            } else {
                val ex = result.exceptionOrNull()
                val msg = if (ex is GetCredentialCancellationException) {
                    if (_appLanguage.value == AppLanguage.ARABIC) "تم إلغاء تسجيل الدخول" else "Sign-in cancelled"
                } else {
                    ex?.localizedMessage ?: "Failed to sign in"
                }
                onComplete(false, msg)
            }
        }
    }

    fun signInWithGoogle(email: String = "", name: String = "") {
        // Fallback convenience method
        if (email.isNotBlank()) {
            val profile = UserProfile(
                email = email,
                displayName = name.ifBlank { "مستخدم Google" },
                provider = AuthProvider.GOOGLE,
                isSignedIn = true
            )
            _userProfile.value = profile
        }
    }

    fun signInWithApple(email: String = "", name: String = "") {
        // Disabled per Google Play policy - Google Sign-In via Credential Manager is the supported provider
    }

    fun signOut() {
        authManager.signOut()
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
        currentMessageJob?.cancel()
        messageCollectorJob?.cancel()
        ttsHelper.shutdown()
        speechHelper.stopListening()
    }
}
