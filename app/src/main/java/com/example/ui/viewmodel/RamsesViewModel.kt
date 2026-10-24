package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiClient
import com.example.data.db.AppDatabase
import android.net.Uri
import com.example.data.model.AppLanguage
import com.example.data.model.AuthProvider
import com.example.data.model.AvatarRigConfig
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.example.data.model.CloudRagBook
import com.example.data.model.CloudRagChunk
import com.example.data.model.CloudRagFolder
import com.example.data.model.CloudRagSyncState
import com.example.data.model.FileSearchPipelineState
import com.example.data.model.GroundingSource
import com.example.data.model.LinkedSearchInstance
import com.example.data.model.MessageSender
import com.example.data.model.PdfBookUploadRequest
import com.example.data.model.RagDocument
import com.example.data.model.SearchInstanceConfig
import com.example.data.model.SearchLinkType
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCallState
import com.example.data.repository.FirestoreChatRepository
import com.example.data.repository.FirestoreRagRepository
import com.example.firebase.FirebaseAuthManager
import com.example.service.GeminiLiveWebSocketClient
import com.example.service.PdfToCloudRagConverter
import com.example.service.RamsesFileSearchOrchestrator
import com.example.service.SpeechRecognizerHelper
import com.example.service.TextToSpeechHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class RamsesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("ramses_ai_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getInstance(application)
    private val chatDao = db.chatDao()
    private val ragDao = db.ragDao()
    private val geminiClient = GeminiApiClient()
    val fileSearchOrchestrator = RamsesFileSearchOrchestrator(
        appContext = application.applicationContext,
        ragDao = ragDao,
        prefs = prefs
    )

    private val authManager by lazy { FirebaseAuthManager.getInstance(application) }
    private val firestoreRepo by lazy { FirestoreChatRepository.getInstance(application) }
    private val cloudRagRepo by lazy { FirestoreRagRepository(application) }
    private val pdfCloudConverter by lazy {
        PdfToCloudRagConverter(application.applicationContext, cloudRagRepo)
    }

    val ttsHelper = TextToSpeechHelper(application)
    val speechHelper = SpeechRecognizerHelper(application)
    val geminiLiveClient = GeminiLiveWebSocketClient()

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

    // RAG Documents List (Eagerly loaded so sendMessage & Gemini Live have 0ms access)
    val ragDocuments: StateFlow<List<RagDocument>> = ragDao.getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Vertex AI / Gemini File Search & Google Cloud Storage 5-PDF Pipeline State
    val fileSearchPipelineState: StateFlow<FileSearchPipelineState> = fileSearchOrchestrator.pipelineState
    val linkedSearchInstance: StateFlow<LinkedSearchInstance?> = fileSearchOrchestrator.linkedInstance
    val searchInstanceConfig: StateFlow<SearchInstanceConfig> = fileSearchOrchestrator.searchConfig

    // Cloud Firebase Small-Folder RAG State (5 Pre-Indexed Books + Up to 1,000+ Cloud PDF Books)
    private val _cloudRagBooks = MutableStateFlow<List<CloudRagBook>>(emptyList())
    val cloudRagBooks: StateFlow<List<CloudRagBook>> = _cloudRagBooks.asStateFlow()

    private val _cloudRagFolders = MutableStateFlow<List<CloudRagFolder>>(emptyList())
    val cloudRagFolders: StateFlow<List<CloudRagFolder>> = _cloudRagFolders.asStateFlow()

    private val _cloudRagSyncState = MutableStateFlow<CloudRagSyncState>(CloudRagSyncState.Idle)
    val cloudRagSyncState: StateFlow<CloudRagSyncState> = _cloudRagSyncState.asStateFlow()

    // 3D Avatar Rig & Custom .GLB Model Config
    private val _avatarRigConfig = MutableStateFlow(
        AvatarRigConfig.fromJsonString(prefs.getString("avatar_rig_config_json", null))
    )
    val avatarRigConfig: StateFlow<AvatarRigConfig> = _avatarRigConfig.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSearchGroundingEnabled = MutableStateFlow(false)
    val isSearchGroundingEnabled: StateFlow<Boolean> = _isSearchGroundingEnabled.asStateFlow()

    private val _selectedImageBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedImageBitmap: StateFlow<Bitmap?> = _selectedImageBitmap.asStateFlow()

    private val _customApiKey = MutableStateFlow("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    // Voice Call (Real Gemini Live WebSocket + Fallback) State
    private val _isGeminiLiveActive = MutableStateFlow(false)
    val isGeminiLiveActive: StateFlow<Boolean> = _isGeminiLiveActive.asStateFlow()

    private val _usingFallbackLocalVoice = MutableStateFlow(false)

    private val _voiceCallState = MutableStateFlow(VoiceCallState.IDLE)
    val voiceCallState: StateFlow<VoiceCallState> = _voiceCallState.asStateFlow()

    private val _currentVoiceTranscript = MutableStateFlow("")
    val currentVoiceTranscript: StateFlow<String> = _currentVoiceTranscript.asStateFlow()

    private val _liveModelSpeechTranscript = MutableStateFlow("")
    val liveModelSpeechTranscript: StateFlow<String> = _liveModelSpeechTranscript.asStateFlow()

    val liveConnectionStatus: StateFlow<GeminiLiveWebSocketClient.LiveConnectionStatus> =
        geminiLiveClient.connectionStatus
    val isLiveMicMuted: StateFlow<Boolean> = geminiLiveClient.isMicMuted
    val isLiveSpeakerMuted: StateFlow<Boolean> = geminiLiveClient.isSpeakerMuted

    // Unified RMS dB combining real Gemini Live 24kHz PCM audio RMS + SpeechRecognizer RMS
    val combinedRmsDb: StateFlow<Float> = combine(
        geminiLiveClient.liveRmsDb,
        speechHelper.rmsDb,
        _isGeminiLiveActive,
        _usingFallbackLocalVoice
    ) { wsRms, localRms, liveActive, isLocalFallback ->
        if (liveActive && !isLocalFallback) wsRms else localRms
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // Voice typing mode (Mic button in input bar)
    private val _isVoiceTyping = MutableStateFlow(false)
    val isVoiceTyping: StateFlow<Boolean> = _isVoiceTyping.asStateFlow()

    // 3D Avatar Split View mode (Top half 3D avatar, Bottom half text conversation)
    private val _is3DViewMode = MutableStateFlow(true)
    val is3DViewMode: StateFlow<Boolean> = _is3DViewMode.asStateFlow()

    // Auto-speak responses in 3D Avatar mode so Ramses speaks with lipsync and body movement
    private val _isAutoSpeakEnabled = MutableStateFlow(true)
    val isAutoSpeakEnabled: StateFlow<Boolean> = _isAutoSpeakEnabled.asStateFlow()

    val isSpeaking: StateFlow<Boolean> = combine(
        ttsHelper.isSpeaking,
        _voiceCallState,
        _isGeminiLiveActive
    ) { ttsSpeaking, callState, liveActive ->
        ttsSpeaking || (liveActive && callState == VoiceCallState.SPEAKING)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    // Selected Gemini Model (preserve supported Gemini 3.x models; upgrade legacy 1.5/2.0 prefs)
    private val _selectedModel = MutableStateFlow(
        prefs.getString("selected_gemini_model", "gemini-3.1-flash-lite").let { saved ->
            if (saved.isNullOrBlank() || !saved.startsWith("gemini-3")) {
                "gemini-3.1-flash-lite"
            } else {
                saved
            }
        }
    )
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // Selected Gem Persona: "RAMSES", "GEMINI_DEFAULT", "CODE", "WRITER"
    private val _selectedGem = MutableStateFlow(
        prefs.getString("selected_gem", "GEMINI_DEFAULT") ?: "GEMINI_DEFAULT"
    )
    val selectedGem: StateFlow<String> = _selectedGem.asStateFlow()

    private var messageCollectorJob: Job? = null
    private var currentMessageJob: Job? = null
    private var cloudBooksListenerJob: Job? = null
    private var cloudFoldersListenerJob: Job? = null

    init {
        _customApiKey.value = prefs.getString("custom_gemini_key", "") ?: ""
        _isSearchGroundingEnabled.value = prefs.getBoolean("search_grounding", false)
        GeminiApiClient.preferredModel = _selectedModel.value

        viewModelScope.launch(Dispatchers.IO) {
            val bundled = cloudRagRepo.loadBundledFiveBooks()
            if (_cloudRagBooks.value.isEmpty() && bundled.isNotEmpty()) {
                _cloudRagBooks.value = bundled.map { it.book }
                _cloudRagFolders.value = bundled.flatMap { it.folders }
            }
            seedDefaultRagDocuments()
            ensureCurrentSessionExists(_currentSessionId.value)
            observeMessagesForSession(_currentSessionId.value)
        }

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
                    startCloudRagListeners()
                    syncFiveBooksToCloudFirebase()
                } else {
                    stopCloudRagListenersAndUseBundledSeed()
                    if (_userProfile.value.isSignedIn) {
                        _userProfile.value = _userProfile.value.copy(
                            provider = AuthProvider.GUEST,
                            isSignedIn = false
                        )
                    }
                }
            }
        }

        // Observe Real Gemini Live WebSocket states
        viewModelScope.launch {
            geminiLiveClient.voiceState.collect { wsVoiceState ->
                if (_isGeminiLiveActive.value && !_usingFallbackLocalVoice.value) {
                    _voiceCallState.value = wsVoiceState
                }
            }
        }

        viewModelScope.launch {
            geminiLiveClient.liveUserTranscript.collect { transcript ->
                if (_isGeminiLiveActive.value && !_usingFallbackLocalVoice.value && transcript.isNotBlank()) {
                    _currentVoiceTranscript.value = transcript
                }
            }
        }

        viewModelScope.launch {
            geminiLiveClient.liveModelTranscript.collect { modelSpeech ->
                if (_isGeminiLiveActive.value && !_usingFallbackLocalVoice.value) {
                    _liveModelSpeechTranscript.value = modelSpeech
                }
            }
        }

        geminiLiveClient.onTurnCompleted = { userSpoken, modelSpoken ->
            val sid = _currentSessionId.value
            viewModelScope.launch(Dispatchers.IO) {
                val uid = authManager.currentUser.value?.uid
                if (userSpoken.isNotBlank()) {
                    val uEntity = ChatMessageEntity(
                        sessionId = sid,
                        sender = "USER",
                        text = userSpoken,
                        timestamp = System.currentTimeMillis(),
                        isVoiceResponse = true
                    )
                    chatDao.insertMessage(uEntity)
                    if (uid != null) firestoreRepo.saveMessage(uEntity, uid)
                }
                if (modelSpoken.isNotBlank()) {
                    val mEntity = ChatMessageEntity(
                        sessionId = sid,
                        sender = "RAMSES",
                        text = modelSpoken,
                        timestamp = System.currentTimeMillis() + 5L,
                        isVoiceResponse = true
                    )
                    chatDao.insertMessage(mEntity)
                    if (uid != null) firestoreRepo.saveMessage(mEntity, uid)
                }
            }
        }

        geminiLiveClient.onFallbackRequired = { reason ->
            Log.w("RamsesVM", "Switching to local voice fallback: $reason")
            if (_isGeminiLiveActive.value) {
                viewModelScope.launch {
                    startLocalFallbackVoiceLoop()
                }
            }
        }

        // Wire TTS state to local fallback Voice Call state
        viewModelScope.launch {
            ttsHelper.isSpeaking.collect { speaking ->
                if (_isGeminiLiveActive.value && _usingFallbackLocalVoice.value && speaking) {
                    _voiceCallState.value = VoiceCallState.SPEAKING
                }
            }
        }

        // Wire Speech recognition callbacks (used for chat Mic button + offline fallback)
        speechHelper.onSpeechCompleted = { text ->
            if (_isVoiceTyping.value) {
                _isVoiceTyping.value = false
                if (text.isNotBlank()) {
                    sendMessage(text)
                }
            } else if (_isGeminiLiveActive.value && _usingFallbackLocalVoice.value) {
                _currentVoiceTranscript.value = text
                sendMessage(text, isVoice = true)
            }
        }

        speechHelper.onEndOfSpeechDetected = {
            if (_isGeminiLiveActive.value && _usingFallbackLocalVoice.value && _voiceCallState.value == VoiceCallState.LISTENING) {
                _voiceCallState.value = VoiceCallState.THINKING
            }
        }

        speechHelper.onErrorOccurred = { errorMsg, errorCode ->
            Log.d("RamsesVM", "Speech error: $errorMsg (code $errorCode)")
            if (_isVoiceTyping.value) {
                _isVoiceTyping.value = false
            }
            if (_isGeminiLiveActive.value && _usingFallbackLocalVoice.value) {
                viewModelScope.launch {
                    delay(350)
                    if (_isGeminiLiveActive.value && _usingFallbackLocalVoice.value && _voiceCallState.value != VoiceCallState.SPEAKING) {
                        _voiceCallState.value = VoiceCallState.LISTENING
                        val langTag = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
                        speechHelper.startListening(langTag)
                    }
                }
            }
        }
    }

    fun saveAvatarRigConfig(config: AvatarRigConfig) {
        _avatarRigConfig.value = config
        prefs.edit().putString("avatar_rig_config_json", config.toJsonString()).apply()
    }

    fun setLanguage(lang: AppLanguage) {
        _appLanguage.value = lang
        prefs.edit().putString("app_language", if (lang == AppLanguage.ENGLISH) "EN" else "AR").apply()
        if (_isGeminiLiveActive.value) {
            if (!_usingFallbackLocalVoice.value) {
                // Reconnect live session seamlessly with new language system instruction
                startGeminiLiveCall()
            } else if (_voiceCallState.value == VoiceCallState.LISTENING) {
                val langCode = if (lang == AppLanguage.ENGLISH) "en-US" else "ar-EG"
                speechHelper.startListening(langCode)
            }
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
                        groundingSources = GroundingSource.fromJsonString(entity.groundingSourcesJson),
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            ragDao.updateDocument(document.copy(isEnabled = !document.isEnabled))
        }
    }

    fun deleteRagDocument(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ragDao.deleteDocument(id)
        }
    }

    /**
     * Uploads up to 5 PDF books to Google Cloud Storage (`gs://...`) and links them
     * to the configured Vertex AI / Gemini File Search instance.
     */
    fun uploadPdfBooksToCloudAndLink(
        uris: List<Uri>,
        enforceExactFive: Boolean = false,
        onFinished: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val result = fileSearchOrchestrator.uploadUrisAndLinkToSearch(
                uris = uris,
                customApiKey = _customApiKey.value,
                enforceExactFive = enforceExactFive
            )
            result.fold(
                onSuccess = { instance ->
                    val msg = if (_appLanguage.value == AppLanguage.ARABIC) {
                        "تم رفع وربط ${instance.books.size} كتب PDF بنجاح بـ ${instance.storeResourceName}"
                    } else {
                        "Uploaded & linked ${instance.books.size} PDF books to ${instance.storeResourceName}"
                    }
                    onFinished(true, msg)
                },
                onFailure = { err ->
                    onFinished(false, err.localizedMessage ?: "PDF Cloud Storage / File Search error")
                }
            )
        }
    }

    /**
     * Programmatic entry point to upload the 5 PDF book requests to Google Cloud Storage
     * and link them to the Search instance.
     */
    fun uploadFivePdfBooksAndLink(
        requests: List<PdfBookUploadRequest>,
        gcpAccessToken: String? = null,
        onFinished: (Result<LinkedSearchInstance>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = fileSearchOrchestrator.uploadRequestsAndLinkToSearch(
                requests = requests,
                customApiKey = _customApiKey.value,
                gcpAccessToken = gcpAccessToken,
                enforceExactFive = true
            )
            onFinished(res)
        }
    }

    fun updateSearchInstanceConfig(config: SearchInstanceConfig) {
        fileSearchOrchestrator.updateSearchInstanceConfig(config)
    }

    // --- Cloud Firebase Small-Folder RAG (5 Books + 1,000+ Books Converter) ---
    private fun startCloudRagListeners() {
        cloudBooksListenerJob?.cancel()
        cloudFoldersListenerJob?.cancel()

        cloudBooksListenerJob = viewModelScope.launch(Dispatchers.IO) {
            val seedBooksById = cloudRagRepo.loadBundledFiveBooks().associate { it.book.bookId to it.book }
            cloudRagRepo.observeSharedBooks()
                .catch { e ->
                    Log.w("RamsesVM", "Cloud RAG books listener error: ${e.message}", e)
                }
                .collect { cloudBooks ->
                    val merged = (seedBooksById + cloudBooks.associateBy { it.bookId }).values.toList()
                    _cloudRagBooks.value = merged
                }
        }

        cloudFoldersListenerJob = viewModelScope.launch(Dispatchers.IO) {
            val seedFoldersById = cloudRagRepo.loadBundledFiveBooks()
                .flatMap { it.folders }
                .associateBy { it.folderId }
            cloudRagRepo.observeSharedFolders()
                .catch { e ->
                    Log.w("RamsesVM", "Cloud RAG folders listener error: ${e.message}", e)
                }
                .collect { cloudFolders ->
                    val merged = (seedFoldersById + cloudFolders.associateBy { it.folderId }).values.toList()
                    _cloudRagFolders.value = merged
                }
        }
    }

    private fun stopCloudRagListenersAndUseBundledSeed() {
        cloudBooksListenerJob?.cancel()
        cloudBooksListenerJob = null
        cloudFoldersListenerJob?.cancel()
        cloudFoldersListenerJob = null

        viewModelScope.launch(Dispatchers.IO) {
            val bundled = cloudRagRepo.loadBundledFiveBooks()
            val books = bundled.map { it.book }
            val folders = bundled.flatMap { it.folders }
            val totalChunks = bundled.sumOf { it.chunks.size }
            _cloudRagBooks.value = books
            _cloudRagFolders.value = folders
            _cloudRagSyncState.value = CloudRagSyncState.Ready(
                syncedBooksCount = books.size,
                syncedFoldersCount = folders.size,
                syncedChunksCount = totalChunks,
                isCloudConnected = false,
                statusMessage = "الكتب الـ 5 مفهرسة وجاهزة (${folders.size} مجلداً صغيراً) • سجّل الدخول بـ Google للمزامنة السحابية ورفع المزيد"
            )
        }
    }

    fun syncFiveBooksToCloudFirebase() {
        if (authManager.currentUser.value == null) {
            _cloudRagSyncState.value = CloudRagSyncState.Error(
                "يرجى تسجيل الدخول بحساب Google أولاً لمزامنة الكتب مع سحابة Firebase."
            )
            return
        }
        viewModelScope.launch {
            _cloudRagSyncState.value = CloudRagSyncState.Syncing(
                progressMessage = "جارٍ التحقق ومزامنة الكتب الـ 5 ومجلداتها الصغيرة مع سحابة Firebase...",
                currentStep = 1,
                totalSteps = 5
            )
            val result = cloudRagRepo.ensureFiveSeedBooksSynced { msg, step, total ->
                _cloudRagSyncState.value = CloudRagSyncState.Syncing(
                    progressMessage = msg,
                    currentStep = step,
                    totalSteps = total
                )
            }
            result.fold(
                onSuccess = {
                    val books = _cloudRagBooks.value
                    val folders = _cloudRagFolders.value
                    _cloudRagSyncState.value = CloudRagSyncState.Ready(
                        syncedBooksCount = books.size,
                        syncedFoldersCount = folders.size,
                        syncedChunksCount = books.sumOf { it.chunkCount },
                        isCloudConnected = true,
                        statusMessage = "متصل بسحابة Firebase • ${books.size} كتب و${folders.size} مجلداً صغيراً متزامنة وجاهزة (0 ميجابايت زيادة بالـ APK)"
                    )
                },
                onFailure = { err ->
                    _cloudRagSyncState.value = CloudRagSyncState.Error(
                        err.localizedMessage ?: "تعذرت المزامنة مع سحابة Firebase"
                    )
                }
            )
        }
    }

    fun convertAndUploadPdfsToCloudFirebase(
        uris: List<Uri>,
        onFinished: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (uris.isEmpty()) return
        if (authManager.currentUser.value == null) {
            val msg = "يرجى تسجيل الدخول بحساب Google أولاً لتحويل الكتب ورفعها إلى سحابة Firebase."
            _cloudRagSyncState.value = CloudRagSyncState.Error(msg)
            onFinished(false, msg)
            return
        }
        viewModelScope.launch {
            val result = pdfCloudConverter.convertAndUploadPdfUris(uris) { msg, step, total ->
                _cloudRagSyncState.value = CloudRagSyncState.Syncing(
                    progressMessage = msg,
                    currentStep = step,
                    totalSteps = total
                )
            }
            result.fold(
                onSuccess = { convertedCount ->
                    val books = _cloudRagBooks.value
                    val folders = _cloudRagFolders.value
                    val successMsg = "تم تحويل ورفع $convertedCount كتاب PDF إلى مجلدات صغيرة ونسخ RAG على سحابة Firebase بنجاح!"
                    _cloudRagSyncState.value = CloudRagSyncState.Ready(
                        syncedBooksCount = books.size,
                        syncedFoldersCount = folders.size,
                        syncedChunksCount = books.sumOf { it.chunkCount },
                        isCloudConnected = true,
                        statusMessage = successMsg
                    )
                    onFinished(true, successMsg)
                },
                onFailure = { err ->
                    val errMsg = err.localizedMessage ?: "تعذر تحويل ملف الـ PDF إلى نسخ RAG سحابية"
                    _cloudRagSyncState.value = CloudRagSyncState.Error(errMsg)
                    onFinished(false, errMsg)
                }
            )
        }
    }

    fun loadChunksForFolderPreview(
        folderId: String,
        onResult: (List<CloudRagChunk>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cloudChunks = if (authManager.currentUser.value != null) {
                cloudRagRepo.getChunksForFolder(folderId).getOrNull()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val finalChunks = cloudChunks ?: cloudRagRepo.loadBundledFiveBooks()
                .flatMap { it.chunks }
                .filter { it.folderId == folderId }
                .sortedBy { it.chunkIndex }
            withContext(Dispatchers.Main) {
                onResult(finalChunks)
            }
        }
    }

    // --- Session Management ---
    fun startNewSession() {
        currentMessageJob?.cancel()
        _streamingMessage.value = null
        _isLoading.value = false
        val newId = UUID.randomUUID().toString()
        _currentSessionId.value = newId
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.clearAllMessages()
            chatDao.clearAllSessions()
            startNewSession()
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
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
        _is3DViewMode.value = (gemId == "RAMSES")
        startNewSession()
    }

    // --- Ultra-Fast Sub-Second Message Streaming ---
    fun sendMessage(promptText: String, isVoice: Boolean = false) {
        val trimmed = promptText.trim()
        val image = _selectedImageBitmap.value
        if (trimmed.isEmpty() && image == null) return

        // If real Gemini Live WebSocket is active and this is a voice/live prompt, route directly over WebSocket!
        if (isVoice && _isGeminiLiveActive.value && !_usingFallbackLocalVoice.value) {
            geminiLiveClient.sendTextMessage(trimmed)
            return
        }

        currentMessageJob?.cancel()

        val sessionId = _currentSessionId.value
        val userText = trimmed.ifEmpty {
            if (_appLanguage.value == AppLanguage.ARABIC) "حلل هذه الصورة الملكية" else "Analyze this royal image"
        }

        val now = System.currentTimeMillis()
        val historySnapshot = _messages.value
        val activeRagDocs = ragDocuments.value.filter { it.isEnabled }
        val uid = authManager.currentUser.value?.uid

        _selectedImageBitmap.value = null
        _isLoading.value = true
        if (isVoice) {
            _voiceCallState.value = VoiceCallState.THINKING
        }

        // Optimistically display user message immediately + prepare streaming bubble at t = 0ms!
        val userEntity = ChatMessageEntity(
            sessionId = sessionId,
            sender = "USER",
            text = userText,
            timestamp = now
        )
        _messages.value = historySnapshot + ChatMessage(
            id = userEntity.id,
            sender = MessageSender.USER,
            text = userText,
            timestamp = now
        )

        val tempRamsesId = UUID.randomUUID().toString()
        _streamingMessage.value = ChatMessage(
            id = tempRamsesId,
            sender = MessageSender.RAMSES,
            text = "",
            timestamp = now + 1L,
            isVoiceResponse = isVoice,
            isError = false
        )

        // Persist user message & session metadata in background without delaying HTTP SSE stream!
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatDao.insertMessage(userEntity)
                chatDao.updateSessionTitleAndTimestamp(
                    sessionId = sessionId,
                    title = userText.take(40),
                    updatedAt = now
                )
                if (uid != null) {
                    firestoreRepo.saveMessage(userEntity, uid)
                }
            } catch (e: Exception) {
                Log.w("RamsesVM", "Background user message persistence notice: ${e.message}")
            }
        }

        currentMessageJob = viewModelScope.launch {
            try {
                var currentLiveChunk = ""
                var lastUiEmitMs = 0L

                val onStreamChunk: suspend (String) -> Unit = { chunk ->
                    currentLiveChunk = chunk
                    val elapsed = SystemClock.uptimeMillis() - lastUiEmitMs
                    // Smooth ~24ms frame pacing avoids SurfaceView layout thrashing while feeling instantaneous
                    if (elapsed >= 24L || lastUiEmitMs == 0L) {
                        lastUiEmitMs = SystemClock.uptimeMillis()
                        _streamingMessage.value = _streamingMessage.value?.copy(text = chunk)
                    }
                }

                // Stage 1 & 2 Small-Folder -> Cited-Chunk Retrieval from Cloud Firebase (or compressed 5-book seed)
                val retrievedPassages = cloudRagRepo.retrieveRelevantPassages(
                    query = userText,
                    cachedCloudFolders = _cloudRagFolders.value,
                    topFoldersLimit = 4,
                    topChunksLimit = if (isVoice) 2 else 5
                )
                val cloudRetrievedDocs = retrievedPassages.map { passage ->
                    RagDocument(
                        id = "cloud_chunk_${passage.chunk.chunkId}",
                        title = "${passage.chunk.bookTitle} — ${passage.chunk.folderTitle} (${passage.chunk.pageRange})",
                        content = passage.chunk.content,
                        category = "سحابة Firebase • ${passage.chunk.pageRange}",
                        isEnabled = true
                    )
                }
                val combinedRagDocs = cloudRetrievedDocs + activeRagDocs
                val cloudCitations = retrievedPassages.map { passage ->
                    GroundingSource(
                        title = passage.toCitationTitle(),
                        uri = passage.toCitationUri()
                    )
                }

                // If a server-side RAG proxy is active and Cloud PDFs are enabled, query through FileSearchOrchestrator first
                val proxyConfigured = searchInstanceConfig.value.serverProxyBaseUrl.isNotBlank() &&
                    linkedSearchInstance.value?.linkType == SearchLinkType.SERVER_PROXY_INDEX &&
                    combinedRagDocs.any { it.isCloudPdfBook } &&
                    !isVoice && image == null

                val result = if (proxyConfigured) {
                    val sysInstruction = if (_selectedGem.value == "GEMINI_DEFAULT") {
                        if (_appLanguage.value == AppLanguage.ARABIC) GeminiApiClient.GEMINI_SYSTEM_INSTRUCTION_AR else GeminiApiClient.GEMINI_SYSTEM_INSTRUCTION_EN
                    } else {
                        if (_appLanguage.value == AppLanguage.ARABIC) GeminiApiClient.RAMSES_SYSTEM_INSTRUCTION_AR else GeminiApiClient.RAMSES_SYSTEM_INSTRUCTION_EN
                    }
                    val proxyResp = fileSearchOrchestrator.queryLinkedKnowledgeBase(
                        prompt = userText,
                        systemInstruction = sysInstruction,
                        activeRagDocs = combinedRagDocs,
                        customApiKey = _customApiKey.value,
                        enableGoogleSearch = _isSearchGroundingEnabled.value,
                        onChunk = onStreamChunk
                    )
                    if (proxyResp != null && proxyResp.answerText.isNotBlank()) {
                        GeminiApiClient.GeminiResult(
                            text = proxyResp.answerText,
                            groundingSources = proxyResp.groundingSources,
                            isError = false
                        )
                    } else {
                        geminiClient.streamRamsesResponse(
                            prompt = userText,
                            history = historySnapshot,
                            imageBitmap = image,
                            enableSearchGrounding = _isSearchGroundingEnabled.value,
                            isVoiceCall = isVoice,
                            customApiKey = _customApiKey.value,
                            ragContext = combinedRagDocs,
                            appLanguage = _appLanguage.value,
                            gemPersona = _selectedGem.value,
                            onChunk = onStreamChunk
                        )
                    }
                } else {
                    geminiClient.streamRamsesResponse(
                        prompt = userText,
                        history = historySnapshot,
                        imageBitmap = image,
                        enableSearchGrounding = _isSearchGroundingEnabled.value,
                        isVoiceCall = isVoice,
                        customApiKey = _customApiKey.value,
                        ragContext = combinedRagDocs,
                        appLanguage = _appLanguage.value,
                        gemPersona = _selectedGem.value,
                        onChunk = onStreamChunk
                    )
                }

                val mergedGroundingSources = (cloudCitations + result.groundingSources)
                    .distinctBy { "${it.title}|${it.uri}" }

                val finalText = if (result.text.isNotBlank()) result.text else currentLiveChunk
                val finalEntity = ChatMessageEntity(
                    id = tempRamsesId,
                    sessionId = sessionId,
                    sender = "RAMSES",
                    text = finalText,
                    timestamp = System.currentTimeMillis(),
                    isVoiceResponse = isVoice,
                    isError = result.isError,
                    groundingSourcesJson = GroundingSource.toJsonString(mergedGroundingSources)
                )

                _streamingMessage.value = null
                _isLoading.value = false

                // Persist final AI message in background
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        chatDao.insertMessage(finalEntity)
                        if (uid != null) {
                            firestoreRepo.saveMessage(finalEntity, uid)
                        }
                    } catch (e: Exception) {
                        Log.w("RamsesVM", "Background AI message persistence notice: ${e.message}")
                    }
                }

                if (isVoice) {
                    _voiceCallState.value = VoiceCallState.SPEAKING
                    val langTag = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
                    val safetyTimeoutMs = minOf(12_000L, maxOf(2500L, (finalText.length * 80L)))
                    viewModelScope.launch {
                        delay(safetyTimeoutMs)
                        if (_isGeminiLiveActive.value && _usingFallbackLocalVoice.value && _voiceCallState.value == VoiceCallState.SPEAKING) {
                            _voiceCallState.value = VoiceCallState.LISTENING
                            speechHelper.startListening(langTag)
                        }
                    }

                    ttsHelper.speak(finalText, languageCode = langTag) {
                        if (_isGeminiLiveActive.value && _usingFallbackLocalVoice.value) {
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
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        chatDao.insertMessage(errorEntity)
                    } catch (_: Exception) {}
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

    // --- Real Full-Duplex Gemini Live Voice Call ---
    fun startGeminiLiveCall() {
        _isGeminiLiveActive.value = true
        _currentVoiceTranscript.value = ""
        _liveModelSpeechTranscript.value = ""
        ttsHelper.stop()
        speechHelper.stopListening()

        val wsStarted = geminiLiveClient.startSession(
            customApiKey = _customApiKey.value,
            appLanguage = _appLanguage.value,
            gemPersona = _selectedGem.value,
            ragDocs = ragDocuments.value
        )

        if (wsStarted) {
            _usingFallbackLocalVoice.value = false
            _voiceCallState.value = VoiceCallState.THINKING
        } else {
            startLocalFallbackVoiceLoop()
        }
    }

    private fun startLocalFallbackVoiceLoop() {
        _usingFallbackLocalVoice.value = true
        _voiceCallState.value = VoiceCallState.SPEAKING
        val isEnglish = _appLanguage.value == AppLanguage.ENGLISH
        val greeting = if (isEnglish) "Greetings, I am listening." else "مرحباً بك، أنا أستمع إليك."
        val langTag = if (isEnglish) "en-US" else "ar-EG"

        viewModelScope.launch {
            delay(2200)
            if (_isGeminiLiveActive.value && _usingFallbackLocalVoice.value && _voiceCallState.value == VoiceCallState.SPEAKING) {
                _voiceCallState.value = VoiceCallState.LISTENING
                speechHelper.startListening(langTag)
            }
        }

        ttsHelper.speak(greeting, languageCode = langTag) {
            if (_isGeminiLiveActive.value && _usingFallbackLocalVoice.value) {
                _voiceCallState.value = VoiceCallState.LISTENING
                speechHelper.startListening(langTag)
            }
        }
    }

    fun endGeminiLiveCall() {
        _isGeminiLiveActive.value = false
        _usingFallbackLocalVoice.value = false
        _voiceCallState.value = VoiceCallState.IDLE
        _liveModelSpeechTranscript.value = ""
        geminiLiveClient.stopSession()
        ttsHelper.stop()
        speechHelper.stopListening()
    }

    fun toggleLiveMicMute() {
        if (!_usingFallbackLocalVoice.value) {
            geminiLiveClient.toggleMicMuted()
        }
    }

    fun toggleLiveSpeakerMute() {
        if (!_usingFallbackLocalVoice.value) {
            geminiLiveClient.toggleSpeakerMuted()
        } else {
            ttsHelper.stop()
        }
    }

    fun startListeningForGeminiLive() {
        if (!_usingFallbackLocalVoice.value) {
            geminiLiveClient.setMicMuted(false)
            geminiLiveClient.interruptModelSpeech()
        } else {
            _voiceCallState.value = VoiceCallState.LISTENING
            ttsHelper.stop()
            val langTag = if (_appLanguage.value == AppLanguage.ENGLISH) "en-US" else "ar-EG"
            speechHelper.startListening(langTag)
        }
    }

    fun stopListeningForGeminiLive() {
        if (!_usingFallbackLocalVoice.value) {
            geminiLiveClient.setMicMuted(true)
        } else {
            speechHelper.stopListening()
            if (_voiceCallState.value == VoiceCallState.LISTENING) {
                _voiceCallState.value = VoiceCallState.IDLE
            }
        }
    }

    fun sendLiveVoicePrompt(promptText: String) {
        if (_isGeminiLiveActive.value && !_usingFallbackLocalVoice.value) {
            geminiLiveClient.sendTextMessage(promptText)
        } else {
            _currentVoiceTranscript.value = promptText
            sendMessage(promptText, isVoice = true)
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
        if (_isGeminiLiveActive.value && !_usingFallbackLocalVoice.value) {
            geminiLiveClient.interruptModelSpeech()
        }
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
        cloudBooksListenerJob?.cancel()
        cloudFoldersListenerJob?.cancel()
        geminiLiveClient.stopSession()
        ttsHelper.shutdown()
        speechHelper.stopListening()
    }
}
