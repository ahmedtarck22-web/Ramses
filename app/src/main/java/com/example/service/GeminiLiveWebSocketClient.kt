package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AppLanguage
import com.example.data.model.RagDocument
import com.example.data.model.VoiceCallState
import com.example.firebase.ensureFirebaseInitialized
import com.example.security.SecureKeyStoreHelper
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Real Full-Duplex Gemini Live Voice Engine with 3-Tier Connectivity & Acoustic Echo Guard:
 * 1. Direct `BidiGenerateContent` WebSocket when a personal Gemini API key is configured.
 * 2. Provisioned Firebase AI Logic `LiveGenerativeModel` 24kHz native audio when no personal API key is set.
 * 3. Automatic Main-thread fallback to local `SpeechRecognizer` + `GeminiApiClient` + `TextToSpeech` if live streaming is unavailable.
 */
@OptIn(PublicPreviewAPI::class)
class GeminiLiveWebSocketClient(
    private val appContext: Context? = null
) {

    enum class LiveConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FALLBACK_LOCAL,
        ERROR
    }

    companion object {
        private const val TAG = "GeminiLiveWS"
        private const val WS_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val PCM_CHUNK_BYTES = 3200 // 100ms of 16kHz 16-bit mono PCM
        const val ECHO_TAIL_COOLDOWN_MS = 550L

        val LIVE_CANDIDATE_MODELS = listOf(
            "models/gemini-2.5-flash-native-audio-latest",
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "models/gemini-2.5-flash-native-audio-preview-09-2025",
            "models/gemini-3.1-flash-live-preview",
            "models/gemini-2.0-flash-live-001"
        )

        val FIREBASE_LIVE_MODELS = listOf(
            "gemini-2.5-flash-native-audio-preview-12-2025",
            "gemini-2.5-flash-native-audio-preview-09-2025",
            "gemini-2.0-flash-live-preview-04-09"
        )

        fun sanitizeApiKey(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw.filter { it.code in 0x21..0x7E }
        }

        fun stripHiddenVoiceTokens(raw: String): String {
            if (raw.isEmpty()) return ""
            return raw
                .replace(Regex("(?is)<thought>.*?</thought>"), "")
                .replace(Regex("(?is)<thinking>.*?</thinking>"), "")
                .replace(Regex("(?is)<thought>.*$"), "")
                .replace(Regex("<\\|[^|>]*\\|>"), "")
                .replace(Regex("(?i)\\[(audio_token|audio|thought|thinking|hidden_token)\\]"), "")
        }

        /**
         * Formats a valid `BidiGenerateContent` client text turn (`clientContent`).
         * Sending `{"realtimeInput":{"text":...}}` causes v1beta WebSocket servers to close with 1007.
         */
        fun buildClientTextPayload(trimmedText: String): String {
            return JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put(
                        "turns",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().put(JSONObject().put("text", trimmedText)))
                            }
                        )
                    )
                    put("turnComplete", true)
                })
            }.toString()
        }

        /**
         * Formats a standard 16kHz PCM `realtimeInput.mediaChunks` frame accepted across all v1beta Live models.
         */
        fun buildRealtimeAudioPayload(base64Pcm: String): String {
            return JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put(
                        "mediaChunks",
                        JSONArray().put(
                            JSONObject().apply {
                                put("mimeType", "audio/pcm;rate=16000")
                                put("data", base64Pcm)
                            }
                        )
                    )
                })
            }.toString()
        }

        private fun isRobolectricRuntime(): Boolean {
            return Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
                runCatching {
                    Class.forName("org.robolectric.Robolectric")
                    true
                }.getOrDefault(false)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val wsOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var firebaseLiveSession: LiveSession? = null
    private var firebaseConnectAndReceiveJob: Job? = null
    private var micCaptureJob: Job? = null
    private var audioPlaybackJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private var savedAudioMode: Int? = null
    private var savedSpeakerphoneOn: Boolean? = null

    private var pcmPlaybackChannel = Channel<Pair<Int, ByteArray>>(Channel.UNLIMITED)
    private val playbackGeneration = AtomicInteger(0)
    private val isSetupComplete = AtomicBoolean(false)
    private val hasReceivedModelResponse = AtomicBoolean(false)
    private val isSessionActive = AtomicBoolean(false)
    private val isModelTurnComplete = AtomicBoolean(true)

    // Acoustic Echo Guard state: prevents mic from feeding speaker audio back to Gemini's VAD
    private val isAiAudioPlaying = AtomicBoolean(false)
    private val echoTailUntilElapsedMs = AtomicLong(0L)

    private var activeModelIndex = 0
    private var currentApiKey: String = ""
    private var currentLanguage: AppLanguage = AppLanguage.ARABIC
    private var currentGemPersona: String = "RAMSES"
    private var currentRagDocs: List<RagDocument> = emptyList()

    private val userTranscriptBuffer = StringBuilder()
    private val modelTranscriptBuffer = StringBuilder()

    // Observable state flows for UI & 3D Avatar Lip-Sync
    private val _connectionStatus = MutableStateFlow(LiveConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<LiveConnectionStatus> = _connectionStatus.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceCallState.IDLE)
    val voiceState: StateFlow<VoiceCallState> = _voiceState.asStateFlow()

    private val _liveUserTranscript = MutableStateFlow("")
    val liveUserTranscript: StateFlow<String> = _liveUserTranscript.asStateFlow()

    private val _liveModelTranscript = MutableStateFlow("")
    val liveModelTranscript: StateFlow<String> = _liveModelTranscript.asStateFlow()

    private val _liveRmsDb = MutableStateFlow(0f)
    val liveRmsDb: StateFlow<Float> = _liveRmsDb.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isSpeakerMuted = MutableStateFlow(false)
    val isSpeakerMuted: StateFlow<Boolean> = _isSpeakerMuted.asStateFlow()

    private val _activeLiveModelName = MutableStateFlow("")
    val activeLiveModelName: StateFlow<String> = _activeLiveModelName.asStateFlow()

    var onTurnCompleted: ((userText: String, modelText: String) -> Unit)? = null
    var onFallbackRequired: ((reason: String) -> Unit)? = null

    fun isEchoGateActive(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        return isAiAudioPlaying.get() || nowElapsedMs < echoTailUntilElapsedMs.get()
    }

    internal fun setEchoGateForTesting(aiPlaying: Boolean, echoTailUntilMs: Long) {
        isAiAudioPlaying.set(aiPlaying)
        echoTailUntilElapsedMs.set(echoTailUntilMs)
    }

    fun resolveApiKey(customApiKey: String?): String {
        val cleanedCustom = sanitizeApiKey(customApiKey)
        if (cleanedCustom.isNotBlank()) return cleanedCustom
        val cleanedBuild = sanitizeApiKey(BuildConfig.GEMINI_API_KEY)
        return if (cleanedBuild.isNotBlank() && cleanedBuild != "MY_GEMINI_API_KEY") {
            cleanedBuild
        } else {
            ""
        }
    }

    private fun triggerFallbackOnMain(reason: String) {
        if (!isSessionActive.get()) return
        isSessionActive.set(false)
        isSetupComplete.set(false)
        micCaptureJob?.cancel()
        micCaptureJob = null
        audioPlaybackJob?.cancel()
        audioPlaybackJob = null
        restoreAudioMode()
        _liveRmsDb.value = 0f
        _connectionStatus.value = LiveConnectionStatus.FALLBACK_LOCAL
        mainHandler.post {
            onFallbackRequired?.invoke(reason)
        }
    }

    @Suppress("DEPRECATION")
    private fun enterVoiceCommunicationAudioMode() {
        val ctx = appContext ?: return
        scope.launch {
            val am = runCatching { ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull() ?: return@launch
            try {
                if (savedAudioMode == null) {
                    savedAudioMode = am.mode
                    savedSpeakerphoneOn = am.isSpeakerphoneOn
                }
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isSpeakerphoneOn = true
            } catch (t: Throwable) {
                Log.w(TAG, "Could not configure AudioManager communication mode: ${t.message}")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreAudioMode() {
        val ctx = appContext ?: return
        val prevMode = savedAudioMode
        val prevSpeaker = savedSpeakerphoneOn
        savedAudioMode = null
        savedSpeakerphoneOn = null
        if (prevMode == null && prevSpeaker == null) return
        scope.launch {
            val am = runCatching { ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull() ?: return@launch
            try {
                prevSpeaker?.let { am.isSpeakerphoneOn = it }
                prevMode?.let { am.mode = it }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not restore AudioManager mode: ${t.message}")
            }
        }
    }

    /**
     * Starts a real bidirectional Gemini Live session:
     * - Uses direct WebSocket if an API key is present;
     * - Otherwise uses Firebase AI Logic `LiveGenerativeModel` on real devices;
     * - Returns false immediately in headless JVM/Robolectric tests without an API key.
     */
    fun startSession(
        customApiKey: String?,
        appLanguage: AppLanguage,
        gemPersona: String,
        ragDocs: List<RagDocument>
    ): Boolean {
        stopSession()

        val key = resolveApiKey(customApiKey)
        if (key.isBlank() && (appContext == null || isRobolectricRuntime())) {
            _connectionStatus.value = LiveConnectionStatus.FALLBACK_LOCAL
            return false
        }

        currentApiKey = key
        currentLanguage = appLanguage
        currentGemPersona = gemPersona
        currentRagDocs = ragDocs
        activeModelIndex = 0

        isSessionActive.set(true)
        isSetupComplete.set(false)
        hasReceivedModelResponse.set(false)
        isAiAudioPlaying.set(false)
        echoTailUntilElapsedMs.set(0L)
        _isMicMuted.value = false
        _isSpeakerMuted.value = false
        _liveUserTranscript.value = ""
        _liveModelTranscript.value = ""
        userTranscriptBuffer.setLength(0)
        modelTranscriptBuffer.setLength(0)

        enterVoiceCommunicationAudioMode()

        if (key.isNotBlank()) {
            connectWithModelIndex(0)
        } else {
            startFirebaseAiLiveSession()
        }
        return true
    }

    private fun startFirebaseAiLiveSession() {
        val ctx = appContext
        if (ctx == null || !isSessionActive.get()) {
            triggerFallbackOnMain("Application context unavailable for Firebase AI Live")
            return
        }

        _connectionStatus.value = LiveConnectionStatus.CONNECTING
        _voiceState.value = VoiceCallState.THINKING

        firebaseConnectAndReceiveJob?.cancel()
        firebaseConnectAndReceiveJob = scope.launch {
            runCatching { ensureFirebaseInitialized(ctx) }
            val voiceName = if (currentGemPersona == "GEMINI_DEFAULT") "Puck" else "Charon"
            val sysText = buildLiveSystemInstruction()

            var connectedSession: LiveSession? = null
            var connectedModel = ""

            for (modelName in FIREBASE_LIVE_MODELS) {
                if (!isActive || !isSessionActive.get()) return@launch
                try {
                    _activeLiveModelName.value = modelName
                    val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                        modelName = modelName,
                        generationConfig = liveGenerationConfig {
                            responseModality = ResponseModality.AUDIO
                            speechConfig = SpeechConfig(Voice(voiceName))
                            inputAudioTranscription = AudioTranscriptionConfig()
                            outputAudioTranscription = AudioTranscriptionConfig()
                        },
                        systemInstruction = content { text(sysText) }
                    )
                    connectedSession = withTimeout(7500L) {
                        liveModel.connect()
                    }
                    connectedModel = modelName
                    break
                } catch (e: CancellationException) {
                    if (!isSessionActive.get()) throw e
                    Log.w(TAG, "Firebase Live connect timeout on $modelName")
                } catch (t: Throwable) {
                    Log.w(TAG, "Firebase Live model $modelName unavailable: ${t.message}")
                }
            }

            if (connectedSession == null || !isSessionActive.get()) {
                triggerFallbackOnMain("Firebase AI Live models unavailable; switching to local voice loop")
                return@launch
            }

            Log.d(TAG, "Connected Firebase AI Live session on $connectedModel")
            firebaseLiveSession = connectedSession
            isSetupComplete.set(true)
            _connectionStatus.value = LiveConnectionStatus.CONNECTED
            _voiceState.value = VoiceCallState.LISTENING

            startAudioPlaybackLoop()
            startMicrophoneStreamingLoop()

            // Trigger natural opening greeting
            runCatching {
                echoTailUntilElapsedMs.set(SystemClock.elapsedRealtime() + 450L)
                _voiceState.value = VoiceCallState.THINKING
                connectedSession.send(buildInitialGreetingPrompt())
            }.onFailure { err ->
                Log.w(TAG, "Initial greeting send notice: ${err.message}")
                _voiceState.value = VoiceCallState.LISTENING
            }

            try {
                connectedSession.receive().collect { serverMsg ->
                    if (!isSessionActive.get()) return@collect
                    if (serverMsg is LiveServerContent) {
                        handleFirebaseLiveContent(serverMsg)
                    }
                }
                if (isSessionActive.get() && !hasReceivedModelResponse.get()) {
                    triggerFallbackOnMain("Firebase Live stream ended before first response")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Firebase Live receive stream ended: ${t.message}")
                if (isSessionActive.get() && !hasReceivedModelResponse.get()) {
                    triggerFallbackOnMain(t.localizedMessage ?: "Firebase Live stream interrupted")
                }
            }
        }
    }

    private fun handleFirebaseLiveContent(serverContent: LiveServerContent) {
        if (!isSessionActive.get()) return

        if (serverContent.interrupted) {
            if (isEchoGateActive()) return
            flushAudioPlayback()
            _voiceState.value = VoiceCallState.LISTENING
            return
        }

        val inText = serverContent.inputTranscription?.text.orEmpty()
        if (inText.isNotEmpty() && !isEchoGateActive()) {
            val chunk = stripHiddenVoiceTokens(inText)
            if (chunk.isNotEmpty()) {
                if (isModelTurnComplete.get() && modelTranscriptBuffer.isNotEmpty()) {
                    modelTranscriptBuffer.setLength(0)
                }
                userTranscriptBuffer.append(chunk)
                _liveUserTranscript.value = stripHiddenVoiceTokens(userTranscriptBuffer.toString()).trim()
            }
        }

        val outText = serverContent.outputTranscription?.text.orEmpty()
        if (outText.isNotEmpty()) {
            hasReceivedModelResponse.set(true)
            isModelTurnComplete.set(false)
            if (!_isSpeakerMuted.value) {
                isAiAudioPlaying.set(true)
            }
            modelTranscriptBuffer.append(outText)
            _liveModelTranscript.value = stripHiddenVoiceTokens(modelTranscriptBuffer.toString()).trim()
        }

        val parts = serverContent.content?.parts.orEmpty()
        if (parts.isNotEmpty()) {
            val gen = playbackGeneration.get()
            for (part in parts) {
                if (part is InlineDataPart && !part.isThought) {
                    val pcmBytes = part.inlineData
                    if (part.mimeType.startsWith("audio/pcm") && pcmBytes.isNotEmpty()) {
                        hasReceivedModelResponse.set(true)
                        isModelTurnComplete.set(false)
                        if (!_isSpeakerMuted.value) {
                            isAiAudioPlaying.set(true)
                            _voiceState.value = VoiceCallState.SPEAKING
                            pcmPlaybackChannel.trySend(gen to pcmBytes)
                        }
                    }
                }
            }
        }

        if (serverContent.turnComplete) {
            isModelTurnComplete.set(true)
            val finalUser = stripHiddenVoiceTokens(userTranscriptBuffer.toString()).trim()
            val finalModel = stripHiddenVoiceTokens(modelTranscriptBuffer.toString()).trim()
            if (finalUser.isNotEmpty() || finalModel.isNotEmpty()) {
                onTurnCompleted?.invoke(finalUser, finalModel)
            }
            userTranscriptBuffer.setLength(0)
            pcmPlaybackChannel.trySend(playbackGeneration.get() to ByteArray(0))
        }
    }

    private fun connectWithModelIndex(index: Int) {
        if (!isSessionActive.get()) return
        if (index >= LIVE_CANDIDATE_MODELS.size) {
            Log.w(TAG, "Direct WebSocket Live models exhausted; trying Firebase AI Live or local fallback")
            if (appContext != null && !isRobolectricRuntime()) {
                startFirebaseAiLiveSession()
            } else {
                triggerFallbackOnMain("All live models unavailable")
            }
            return
        }

        activeModelIndex = index
        val modelId = LIVE_CANDIDATE_MODELS[index]
        _activeLiveModelName.value = modelId.removePrefix("models/")
        _connectionStatus.value = LiveConnectionStatus.CONNECTING
        _voiceState.value = VoiceCallState.THINKING
        isSetupComplete.set(false)

        val request = try {
            Request.Builder()
                .url(WS_ENDPOINT)
                .addHeader("x-goog-api-key", currentApiKey)
                .build()
        } catch (t: Throwable) {
            val safeErr = SecureKeyStoreHelper.redactSecret(t.message, currentApiKey)
            Log.e(TAG, "Failed to build Gemini Live WebSocket request: $safeErr")
            triggerFallbackOnMain("Invalid API key format or network configuration")
            return
        }

        try {
            webSocket = wsOkHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!isSessionActive.get()) {
                        runCatching { webSocket.close(1000, "Session closed") }
                        return
                    }
                    Log.d(TAG, "Gemini Live WebSocket opened with model $modelId")
                    runCatching { sendSetupMessage(webSocket, modelId) }.onFailure { err ->
                        Log.e(TAG, "Failed sending setup message: ${err.message}")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleServerFrame(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleServerFrame(bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    val safeReason = SecureKeyStoreHelper.redactSecret(reason, currentApiKey)
                    Log.w(TAG, "WebSocket closing ($code): $safeReason")
                    runCatching { webSocket.close(code, reason) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    val safeReason = SecureKeyStoreHelper.redactSecret(reason, currentApiKey)
                    Log.w(TAG, "WebSocket closed ($code): $safeReason")
                    if (!isSessionActive.get() || code == 1000) return

                    micCaptureJob?.cancel()
                    audioPlaybackJob?.cancel()
                    isSetupComplete.set(false)

                    if (!hasReceivedModelResponse.get() && index + 1 < LIVE_CANDIDATE_MODELS.size) {
                        scope.launch {
                            connectWithModelIndex(index + 1)
                        }
                    } else if (!hasReceivedModelResponse.get() && appContext != null && !isRobolectricRuntime()) {
                        startFirebaseAiLiveSession()
                    } else {
                        triggerFallbackOnMain("Live WebSocket closed ($code)")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val safeErr = SecureKeyStoreHelper.redactSecret(t.message, currentApiKey)
                    Log.e(TAG, "WebSocket failure on $modelId: $safeErr")
                    if (!isSessionActive.get()) return

                    micCaptureJob?.cancel()
                    audioPlaybackJob?.cancel()
                    isSetupComplete.set(false)

                    if (!hasReceivedModelResponse.get() && index + 1 < LIVE_CANDIDATE_MODELS.size) {
                        scope.launch {
                            connectWithModelIndex(index + 1)
                        }
                    } else if (!hasReceivedModelResponse.get() && appContext != null && !isRobolectricRuntime()) {
                        startFirebaseAiLiveSession()
                    } else {
                        triggerFallbackOnMain(
                            SecureKeyStoreHelper.redactSecret(t.localizedMessage, currentApiKey)
                                .ifBlank { "WebSocket connection failed" }
                        )
                    }
                }
            })
        } catch (t: Throwable) {
            val safeErr = SecureKeyStoreHelper.redactSecret(t.message, currentApiKey)
            Log.e(TAG, "Failed to open Gemini Live WebSocket: $safeErr")
            triggerFallbackOnMain("Could not start live WebSocket")
        }
    }

    private fun buildInitialGreetingPrompt(): String {
        val isArabic = currentLanguage == AppLanguage.ARABIC
        return if (currentGemPersona == "GEMINI_DEFAULT") {
            if (isArabic) {
                "رحّب بالمستخدم بودّ واختصار شديد في جملة واحدة كمساعد Gemini الصوتي، وأخبره أنك تستمع إليه."
            } else {
                "Greet the user warmly in one short sentence as Gemini Live and say you are listening."
            }
        } else {
            if (isArabic) {
                "رحّب بالمستخدم ترحيباً ملكياً دافئاً في جملة قصيرة وأخبره أنك تستمع إليه الآن."
            } else {
                "Greet the user warmly in one royal sentence and say you are listening."
            }
        }
    }

    private fun buildLiveSystemInstruction(): String {
        val isArabic = currentLanguage == AppLanguage.ARABIC
        val clockContext = com.example.data.api.EverydayKnowledgeHelper.buildCurrentDateTimeContext(currentLanguage)
        val baseInstruction = when (currentGemPersona) {
            "GEMINI_DEFAULT" -> if (isArabic) {
                """أنت مساعد Google Gemini الصوتي المباشر (Gemini Live). تحدث بصوت طبيعي، دافئ، ومترابط باللغة العربية الواضحة. لديك القدرة الكاملة على الرد على جميع الأسئلة اليومية والطبيعية: التحيات («إزيك»، «عامل إيه»)، التاريخ واليوم والوقت الحالي، حالة الجو والطقس، التكنولوجيا والبرمجة وBlender، أفلام الكرتون والأنيميشن، والعلوم العامة دون أي خلط بالتاريخ الفرعوني.
$clockContext"""
            } else {
                """You are Google Gemini Live. Speak naturally, warmly, and conversationally in clear English. Answer all everyday questions—greetings, today's date/day/time, weather, coding, Blender, technology, animated/cartoon movies, and general topics—directly without any historical roleplay.
$clockContext"""
            }
            else -> if (isArabic) {
                """في الأسئلة التاريخية والأثرية وأسئلة الحضارة المصرية القديمة، تحدث بشخصية الملك رمسيس الثاني بوقار ومودة بدون ذكر أرقام صفحات في كلامك المنطوق. وفي الأسئلة اليومية الطبيعية (إزيك، عامل إيه، التاريخ، اليوم، الوقت، حالة الجو، أفلام الكرتون، الثقافة العامة) أجب بودّ وذكاء ودقة مستنداً لمعلومات الوقت الحالية ومعرفتك الشاملة دون أن تقول إنك لا تعرف. أما إذا سألك المستخدم أي سؤال يخص البرمجة، الأكواد، برنامج Blender، أو التكنولوجيا الحديثة، فأجب فوراً كمساعد تقني طبيعي مباشر بدون خلط بين التكنولوجيا والتاريخ والآثار.
$clockContext"""
            } else {
                """For ancient Egypt, history, and antiquities questions, speak warmly as King Rameses II without reciting page numbers aloud. For everyday questions (how you are, date, day, current time, weather, animated/cartoon movies, general topics), answer warmly, naturally, and accurately using the provided clock context and broad knowledge. For coding, programming, Blender, or modern technology, answer directly and purely technically without mixing history.
$clockContext"""
            }
        }

        val enabledDocs = currentRagDocs.filter { it.isEnabled }
        if (enabledDocs.isEmpty()) return baseInstruction

        val ragSection = enabledDocs.take(4).joinToString("\n") { "• ${it.title}: ${it.content.take(350)}" }
        return "$baseInstruction\n\n[مراجع إضافية عند الحاجة التاريخية فقط]:\n$ragSection"
    }

    private fun sendSetupMessage(ws: WebSocket, modelId: String) {
        val voiceName = if (currentGemPersona == "GEMINI_DEFAULT") "Puck" else "Charon"
        val setupJson = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", modelId)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })
                put("inputAudioTranscription", JSONObject())
                put("outputAudioTranscription", JSONObject())
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", buildLiveSystemInstruction())))
                })
            })
        }
        ws.send(setupJson.toString())
    }

    private fun handleServerFrame(rawJson: String) {
        if (!isSessionActive.get()) return
        try {
            val root = JSONObject(rawJson)

            if (root.has("setupComplete")) {
                isSetupComplete.set(true)
                _connectionStatus.value = LiveConnectionStatus.CONNECTED
                _voiceState.value = VoiceCallState.LISTENING
                startAudioPlaybackLoop()
                startMicrophoneStreamingLoop()
                sendTextMessage(buildInitialGreetingPrompt(), showInUserTranscript = false)
                return
            }

            val serverContent = root.optJSONObject("serverContent") ?: return

            if (serverContent.optBoolean("interrupted", false)) {
                if (isEchoGateActive()) {
                    Log.d(TAG, "Echo Guard: Ignored speaker-echo self-interruption while speaking")
                    return
                }
                flushAudioPlayback()
                _voiceState.value = VoiceCallState.LISTENING
                return
            }

            val inputTranscription = serverContent.optJSONObject("inputTranscription")
            if (inputTranscription != null && !isEchoGateActive()) {
                val chunk = stripHiddenVoiceTokens(inputTranscription.optString("text", ""))
                if (chunk.isNotEmpty()) {
                    if (isModelTurnComplete.get() && modelTranscriptBuffer.isNotEmpty()) {
                        modelTranscriptBuffer.setLength(0)
                    }
                    userTranscriptBuffer.append(chunk)
                    _liveUserTranscript.value = stripHiddenVoiceTokens(userTranscriptBuffer.toString()).trim()
                }
            }

            val outputTranscription = serverContent.optJSONObject("outputTranscription")
            if (outputTranscription != null) {
                val chunk = outputTranscription.optString("text", "")
                if (chunk.isNotEmpty()) {
                    hasReceivedModelResponse.set(true)
                    isModelTurnComplete.set(false)
                    if (!_isSpeakerMuted.value) {
                        isAiAudioPlaying.set(true)
                    }
                    modelTranscriptBuffer.append(chunk)
                    val cleanedModel = stripHiddenVoiceTokens(modelTranscriptBuffer.toString()).trim()
                    _liveModelTranscript.value = cleanedModel
                }
            }

            val modelTurn = serverContent.optJSONObject("modelTurn")
            val parts = modelTurn?.optJSONArray("parts")
            if (parts != null) {
                val gen = playbackGeneration.get()
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.optBoolean("thought", false)) continue
                    val inlineData = part.optJSONObject("inlineData")
                    if (inlineData != null) {
                        val mimeType = inlineData.optString("mimeType", "")
                        val base64Audio = inlineData.optString("data", "")
                        if (mimeType.startsWith("audio/pcm") && base64Audio.isNotEmpty()) {
                            hasReceivedModelResponse.set(true)
                            isModelTurnComplete.set(false)
                            val pcmBytes = runCatching { Base64.decode(base64Audio, Base64.DEFAULT) }.getOrNull()
                            if (pcmBytes != null && pcmBytes.isNotEmpty() && !_isSpeakerMuted.value) {
                                isAiAudioPlaying.set(true)
                                _voiceState.value = VoiceCallState.SPEAKING
                                pcmPlaybackChannel.trySend(gen to pcmBytes)
                            }
                        }
                    }
                }
            }

            if (serverContent.optBoolean("turnComplete", false)) {
                isModelTurnComplete.set(true)
                val finalUser = stripHiddenVoiceTokens(userTranscriptBuffer.toString()).trim()
                val finalModel = stripHiddenVoiceTokens(modelTranscriptBuffer.toString()).trim()
                if (finalUser.isNotEmpty() || finalModel.isNotEmpty()) {
                    onTurnCompleted?.invoke(finalUser, finalModel)
                }
                userTranscriptBuffer.setLength(0)
                pcmPlaybackChannel.trySend(playbackGeneration.get() to ByteArray(0))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Gemini Live frame: ${e.message}")
        }
    }

    /**
     * Sends a text message or quick prompt directly into the active Gemini Live session
     * using valid `clientContent` turns (or Firebase `LiveSession.send`).
     */
    fun sendTextMessage(text: String, showInUserTranscript: Boolean = true) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !isSessionActive.get()) return

        flushAudioPlayback()
        modelTranscriptBuffer.setLength(0)
        _liveModelTranscript.value = ""

        if (showInUserTranscript) {
            userTranscriptBuffer.setLength(0)
            userTranscriptBuffer.append(trimmed)
            _liveUserTranscript.value = trimmed
        }
        echoTailUntilElapsedMs.set(SystemClock.elapsedRealtime() + 450L)
        _voiceState.value = VoiceCallState.THINKING

        val fbSession = firebaseLiveSession
        if (fbSession != null) {
            scope.launch {
                runCatching { fbSession.send(trimmed) }.onFailure { err ->
                    Log.w(TAG, "Failed sending text to Firebase LiveSession: ${err.message}")
                }
            }
        } else {
            webSocket?.send(buildClientTextPayload(trimmed))
        }
    }

    fun interruptModelSpeech() {
        flushAudioPlayback()
        _isMicMuted.value = false
        if (isSessionActive.get() && isSetupComplete.get()) {
            _voiceState.value = VoiceCallState.LISTENING
        }
    }

    fun setMicMuted(muted: Boolean) {
        _isMicMuted.value = muted
        if (muted && _voiceState.value == VoiceCallState.LISTENING) {
            _liveRmsDb.value = 0f
            _voiceState.value = VoiceCallState.IDLE
        } else if (!muted && isSessionActive.get() && isSetupComplete.get() && _voiceState.value == VoiceCallState.IDLE) {
            _voiceState.value = VoiceCallState.LISTENING
        }
    }

    fun toggleMicMuted(): Boolean {
        val next = !_isMicMuted.value
        setMicMuted(next)
        return next
    }

    fun setSpeakerMuted(muted: Boolean) {
        _isSpeakerMuted.value = muted
        if (muted) {
            flushAudioPlayback()
            if (isSessionActive.get()) {
                _voiceState.value = if (_isMicMuted.value) VoiceCallState.IDLE else VoiceCallState.LISTENING
            }
        }
    }

    fun toggleSpeakerMuted(): Boolean {
        val next = !_isSpeakerMuted.value
        setSpeakerMuted(next)
        return next
    }

    private fun flushAudioPlayback() {
        playbackGeneration.incrementAndGet()
        isAiAudioPlaying.set(false)
        echoTailUntilElapsedMs.set(0L)
        while (pcmPlaybackChannel.tryReceive().isSuccess) {
            // Drain stale PCM buffers immediately
        }
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (_: Exception) {}
        _liveRmsDb.value = 0f
    }

    private fun startAudioPlaybackLoop() {
        audioPlaybackJob?.cancel()
        audioPlaybackJob = scope.launch {
            val minBuffer = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4800)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(OUTPUT_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuffer * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create AudioTrack: ${e.message}")
                return@launch
            }

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack not initialized on current audio device; releasing.")
                runCatching { track.release() }
                return@launch
            }

            audioTrack = track
            try {
                track.play()
                for ((gen, pcmChunk) in pcmPlaybackChannel) {
                    if (!isActive || !isSessionActive.get()) break
                    if (gen != playbackGeneration.get()) continue

                    if (pcmChunk.isEmpty()) {
                        _liveRmsDb.value = 0f
                        echoTailUntilElapsedMs.set(SystemClock.elapsedRealtime() + ECHO_TAIL_COOLDOWN_MS)
                        isAiAudioPlaying.set(false)

                        delay(ECHO_TAIL_COOLDOWN_MS)
                        if (gen == playbackGeneration.get() && !isAiAudioPlaying.get()) {
                            if (_voiceState.value == VoiceCallState.SPEAKING || _voiceState.value == VoiceCallState.THINKING) {
                                _voiceState.value = if (_isMicMuted.value) VoiceCallState.IDLE else VoiceCallState.LISTENING
                            }
                        }
                        continue
                    }

                    if (_isSpeakerMuted.value) {
                        isAiAudioPlaying.set(false)
                        continue
                    }

                    isAiAudioPlaying.set(true)
                    _voiceState.value = VoiceCallState.SPEAKING

                    val sliceSize = 3840
                    var offset = 0
                    while (offset < pcmChunk.size && gen == playbackGeneration.get() && !_isSpeakerMuted.value) {
                        val len = minOf(sliceSize, pcmChunk.size - offset)
                        val rms = computePcmRmsDb(pcmChunk, offset, len)
                        _liveRmsDb.value = rms
                        track.write(pcmChunk, offset, len)
                        offset += len
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack loop ended: ${e.message}")
            } finally {
                isAiAudioPlaying.set(false)
                _liveRmsDb.value = 0f
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
                if (audioTrack === track) audioTrack = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneStreamingLoop() {
        micCaptureJob?.cancel()
        micCaptureJob = scope.launch {
            val minBuffer = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(PCM_CHUNK_BYTES * 2)

            var recorder = tryCreateAudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, minBuffer)
            if (recorder == null) {
                recorder = tryCreateAudioRecord(MediaRecorder.AudioSource.MIC, minBuffer)
            }
            if (recorder == null) {
                Log.e(TAG, "Could not initialize AudioRecord")
                return@launch
            }

            audioRecord = recorder
            attachHardwareAudioEffects(recorder.audioSessionId)

            val buffer = ByteArray(PCM_CHUNK_BYTES)
            val silenceBuffer = ByteArray(PCM_CHUNK_BYTES)
            try {
                recorder.startRecording()
                while (isActive && isSessionActive.get()) {
                    if (!isSetupComplete.get()) {
                        delay(50)
                        continue
                    }

                    val readBytes = recorder.read(buffer, 0, buffer.size)
                    if (readBytes <= 0) {
                        delay(20)
                        continue
                    }

                    if (_isMicMuted.value) {
                        continue
                    }

                    val fbSession = firebaseLiveSession

                    if (isEchoGateActive()) {
                        val silentSlice = if (readBytes == silenceBuffer.size) silenceBuffer else ByteArray(readBytes)
                        if (fbSession != null) {
                            runCatching {
                                fbSession.sendAudioRealtime(InlineData(silentSlice, "audio/pcm;rate=16000"))
                            }
                        } else {
                            val silentB64 = Base64.encodeToString(silentSlice, Base64.NO_WRAP)
                            webSocket?.send(buildRealtimeAudioPayload(silentB64))
                        }
                        continue
                    }

                    if (_voiceState.value == VoiceCallState.LISTENING) {
                        val micRms = computePcmRmsDb(buffer, 0, readBytes)
                        _liveRmsDb.value = micRms
                    }

                    val chunkToSend = if (readBytes == buffer.size) buffer.copyOf() else buffer.copyOf(readBytes)
                    if (fbSession != null) {
                        runCatching {
                            fbSession.sendAudioRealtime(InlineData(chunkToSend, "audio/pcm;rate=16000"))
                        }
                    } else {
                        val b64 = Base64.encodeToString(chunkToSend, Base64.NO_WRAP)
                        webSocket?.send(buildRealtimeAudioPayload(b64))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mic capture loop ended: ${e.message}")
            } finally {
                releaseAudioEffects()
                try {
                    recorder.stop()
                    recorder.release()
                } catch (_: Exception) {}
                if (audioRecord === recorder) audioRecord = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryCreateAudioRecord(source: Int, bufferSize: Int): AudioRecord? {
        return try {
            val rec = AudioRecord(
                source,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (rec.state == AudioRecord.STATE_INITIALIZED) rec else {
                rec.release()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun attachHardwareAudioEffects(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (_: Exception) {}
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (_: Exception) {}
    }

    private fun releaseAudioEffects() {
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        echoCanceler = null
        noiseSuppressor = null
    }

    private fun computePcmRmsDb(bytes: ByteArray, offset: Int, length: Int): Float {
        if (length < 2) return 0f
        val shortCount = length / 2
        var sumSquares = 0.0
        val bb = ByteBuffer.wrap(bytes, offset, shortCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until shortCount) {
            val sample = bb.short.toDouble() / 32768.0
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / shortCount)
        if (rms <= 0.0005) return 0f
        val dbFs = 20.0 * log10(rms)
        return (((dbFs + 48.0) / 42.0) * 15.0).toFloat().coerceIn(0f, 15f)
    }

    fun stopSession() {
        isSessionActive.set(false)
        isSetupComplete.set(false)
        hasReceivedModelResponse.set(false)
        isAiAudioPlaying.set(false)
        echoTailUntilElapsedMs.set(0L)
        playbackGeneration.incrementAndGet()

        firebaseConnectAndReceiveJob?.cancel()
        firebaseConnectAndReceiveJob = null

        val fbSession = firebaseLiveSession
        firebaseLiveSession = null
        if (fbSession != null) {
            scope.launch {
                runCatching { fbSession.stopAudioConversation() }
                runCatching { fbSession.close() }
            }
        }

        micCaptureJob?.cancel()
        micCaptureJob = null

        audioPlaybackJob?.cancel()
        audioPlaybackJob = null

        pcmPlaybackChannel.close()
        pcmPlaybackChannel = Channel(Channel.UNLIMITED)

        try {
            webSocket?.close(1000, "User ended Gemini Live session")
        } catch (_: Exception) {}
        webSocket = null

        restoreAudioMode()

        _liveRmsDb.value = 0f
        _voiceState.value = VoiceCallState.IDLE
        _connectionStatus.value = LiveConnectionStatus.DISCONNECTED
    }
}
