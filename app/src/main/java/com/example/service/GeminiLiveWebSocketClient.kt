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
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AppLanguage
import com.example.data.model.RagDocument
import com.example.data.model.VoiceCallState
import com.example.security.SecureKeyStoreHelper
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
 * Real Full-Duplex Gemini Live WebSocket Engine (BidiGenerateContent) with Dual-Layer Acoustic Echo Guard.
 *
 * Streams raw 16kHz 16-bit mono PCM microphone audio to Gemini Live and plays back native 24kHz
 * 16-bit mono PCM AI voice audio in real time. Combines hardware DSP Acoustic Echo Cancellation
 * (`MODE_IN_COMMUNICATION` + `USAGE_VOICE_COMMUNICATION` + `AcousticEchoCanceler`) with software
 * silence-gating while AI speech is playing + 550ms acoustic reverb tail cooldown so the phone's
 * microphone never hears or interrupts Ramses's own voice from the loudspeaker.
 */
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
            "models/gemini-3.1-flash-live-preview",
            "models/gemini-3.8-live",
            "models/gemini-2.5-flash-native-audio-latest"
        )
    }

    private val wsOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep open indefinitely for WebSocket streaming
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
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

    /**
     * Returns true while AI audio is actively playing through the loudspeaker OR during the
     * post-playback acoustic echo decay window.
     */
    fun isEchoGateActive(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        return isAiAudioPlaying.get() || nowElapsedMs < echoTailUntilElapsedMs.get()
    }

    internal fun setEchoGateForTesting(aiPlaying: Boolean, echoTailUntilMs: Long) {
        isAiAudioPlaying.set(aiPlaying)
        echoTailUntilElapsedMs.set(echoTailUntilMs)
    }

    fun resolveApiKey(customApiKey: String?): String {
        return when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" ->
                BuildConfig.GEMINI_API_KEY.trim()
            else -> ""
        }
    }

    @Suppress("DEPRECATION")
    private fun enterVoiceCommunicationAudioMode() {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (savedAudioMode == null) {
                savedAudioMode = am.mode
                savedSpeakerphoneOn = am.isSpeakerphoneOn
            }
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure AudioManager communication mode: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreAudioMode() {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            savedSpeakerphoneOn?.let { am.isSpeakerphoneOn = it }
            savedAudioMode?.let { am.mode = it }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore AudioManager mode: ${e.message}")
        } finally {
            savedAudioMode = null
            savedSpeakerphoneOn = null
        }
    }

    /**
     * Starts a real bidirectional Gemini Live WebSocket session.
     * Returns true if WebSocket connection is initiated, or false if API key is missing.
     */
    fun startSession(
        customApiKey: String?,
        appLanguage: AppLanguage,
        gemPersona: String,
        ragDocs: List<RagDocument>
    ): Boolean {
        stopSession()

        val key = resolveApiKey(customApiKey)
        if (key.isBlank()) {
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
        isAiAudioPlaying.set(false)
        echoTailUntilElapsedMs.set(0L)
        _isMicMuted.value = false
        _isSpeakerMuted.value = false
        _liveUserTranscript.value = ""
        _liveModelTranscript.value = ""
        userTranscriptBuffer.setLength(0)
        modelTranscriptBuffer.setLength(0)

        enterVoiceCommunicationAudioMode()
        connectWithModelIndex(0)
        return true
    }

    private fun connectWithModelIndex(index: Int) {
        if (!isSessionActive.get()) return
        if (index >= LIVE_CANDIDATE_MODELS.size) {
            Log.e(TAG, "All Gemini Live models exhausted; triggering fallback")
            restoreAudioMode()
            _connectionStatus.value = LiveConnectionStatus.FALLBACK_LOCAL
            onFallbackRequired?.invoke("All live models unavailable")
            return
        }

        activeModelIndex = index
        val modelId = LIVE_CANDIDATE_MODELS[index]
        _activeLiveModelName.value = modelId.removePrefix("models/")
        _connectionStatus.value = LiveConnectionStatus.CONNECTING
        _voiceState.value = VoiceCallState.THINKING
        isSetupComplete.set(false)

        val request = Request.Builder()
            .url(WS_ENDPOINT)
            .addHeader("x-goog-api-key", currentApiKey)
            .build()

        webSocket = wsOkHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isSessionActive.get()) {
                    webSocket.close(1000, "Session closed")
                    return
                }
                Log.d(TAG, "Gemini Live WebSocket opened with model $modelId")
                sendSetupMessage(webSocket, modelId)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerFrame(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // CRITICAL: BidiGenerateContent sends JSON payloads inside binary WebSocket frames (opcode 2)
                handleServerFrame(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing ($code): $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closed ($code): $reason")
                if (isSessionActive.get() && !isSetupComplete.get()) {
                    scope.launch {
                        connectWithModelIndex(index + 1)
                    }
                } else if (isSessionActive.get() && code != 1000) {
                    _connectionStatus.value = LiveConnectionStatus.DISCONNECTED
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val safeErr = SecureKeyStoreHelper.redactSecret(t.message, currentApiKey)
                Log.e(TAG, "WebSocket failure on $modelId: $safeErr")
                if (!isSessionActive.get()) return
                if (!isSetupComplete.get() && index + 1 < LIVE_CANDIDATE_MODELS.size) {
                    scope.launch {
                        connectWithModelIndex(index + 1)
                    }
                } else {
                    restoreAudioMode()
                    _connectionStatus.value = LiveConnectionStatus.ERROR
                    onFallbackRequired?.invoke(
                        SecureKeyStoreHelper.redactSecret(t.localizedMessage, currentApiKey)
                            .ifBlank { "WebSocket connection failed" }
                    )
                }
            }
        })
    }

    private fun buildLiveSystemInstruction(): String {
        val isArabic = currentLanguage == AppLanguage.ARABIC
        val baseInstruction = when (currentGemPersona) {
            "GEMINI_DEFAULT" -> if (isArabic) {
                """أنت مساعد Google Gemini الصوتي المباشر (Gemini Live). تحدث بصوت طبيعي، دافئ، ومترابط باللغة العربية الواضحة (أو العامية المصرية اللطيفة عند مخاطبتك بها). أكمل فكرتك وجملك الصوتية بوضوح وانسيابية في جملتين إلى خمس جمل وافية ومترابطة كمحادثة هاتفية حقيقية دون توقف مفاجئ."""
            } else {
                """You are Google Gemini Live. Speak naturally, warmly, and conversationally in clear English. Complete your thoughts smoothly in 2 to 5 coherent spoken sentences like a real phone conversation."""
            }
            else -> if (isArabic) {
                """أنت الملك رمسيس الثاني العظيم، فرعون مصر وابن رع، تتحدث مباشرة بصوتك الملكي المهيب والدافئ عبر محادثة صوتية حية (Live). خاطب محدثك بوقار ومودة، وأكمل إجاباتك الصوتية بسلاسة وترابط (في جملتين إلى خمس جمل وافية وواضحة بدون رموز) حتى تصل الفكرة كاملة للمستمع."""
            } else {
                """You are King Rameses II, the Great Pharaoh of Egypt, speaking live in real time with a warm, regal, majestic voice. Answer questions smoothly and completely in 2 to 5 clear spoken sentences."""
            }
        }

        val enabledDocs = currentRagDocs.filter { it.isEnabled }
        if (enabledDocs.isEmpty()) return baseInstruction

        val ragSection = enabledDocs.take(5).joinToString("\n") { "• ${it.title}: ${it.content.take(400)}" }
        return "$baseInstruction\n\n[معلومات موثقة من قاعدة المعرفة]:\n$ragSection"
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

            // 1. Setup Complete -> Start real-time audio engine & greet / listen immediately!
            if (root.has("setupComplete")) {
                isSetupComplete.set(true)
                _connectionStatus.value = LiveConnectionStatus.CONNECTED
                _voiceState.value = VoiceCallState.LISTENING
                startAudioPlaybackLoop()
                startMicrophoneStreamingLoop()

                val greetingPrompt = if (currentLanguage == AppLanguage.ARABIC) {
                    "رحّب بالمستخدم ترحيباً ملكياً دافئاً في جملة قصيرة وأخبره أنك تستمع إليه الآن."
                } else {
                    "Greet the user warmly in one royal sentence and say you are listening."
                }
                sendTextMessage(greetingPrompt, showInUserTranscript = false)
                return
            }

            val serverContent = root.optJSONObject("serverContent") ?: return

            // 2. Server-side VAD Interruption:
            // CRITICAL ECHO GUARD: If AI audio is currently playing (or in loudspeaker reverb tail),
            // ignore spurious server `interrupted` events caused by acoustic speaker echo!
            // Explicit user interruption via the golden Stop button (`interruptModelSpeech()`) always works immediately.
            if (serverContent.optBoolean("interrupted", false)) {
                if (isEchoGateActive()) {
                    Log.d(TAG, "Echo Guard: Ignored speaker-echo self-interruption while Ramses is speaking")
                    return
                }
                Log.d(TAG, "User interruption detected: flushing AI audio playback")
                flushAudioPlayback()
                _voiceState.value = VoiceCallState.LISTENING
                return
            }

            // 3. Live User Speech Transcription (ignored while AI is speaking so speaker echo isn't transcribed as user speech)
            val inputTranscription = serverContent.optJSONObject("inputTranscription")
            if (inputTranscription != null && !isEchoGateActive()) {
                val chunk = inputTranscription.optString("text", "")
                if (chunk.isNotEmpty()) {
                    if (isModelTurnComplete.get() && modelTranscriptBuffer.isNotEmpty()) {
                        modelTranscriptBuffer.setLength(0)
                    }
                    userTranscriptBuffer.append(chunk)
                    _liveUserTranscript.value = userTranscriptBuffer.toString().trim()
                }
            }

            // 4. Live AI Voice Transcription
            val outputTranscription = serverContent.optJSONObject("outputTranscription")
            if (outputTranscription != null) {
                val chunk = outputTranscription.optString("text", "")
                if (chunk.isNotEmpty()) {
                    isModelTurnComplete.set(false)
                    if (!_isSpeakerMuted.value) {
                        isAiAudioPlaying.set(true)
                    }
                    modelTranscriptBuffer.append(chunk)
                    _liveModelTranscript.value = modelTranscriptBuffer.toString().trim()
                }
            }

            // 5. Native 24kHz PCM Audio Chunks from Model Turn
            val modelTurn = serverContent.optJSONObject("modelTurn")
            val parts = modelTurn?.optJSONArray("parts")
            if (parts != null) {
                val gen = playbackGeneration.get()
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val inlineData = part.optJSONObject("inlineData")
                    if (inlineData != null) {
                        val mimeType = inlineData.optString("mimeType", "")
                        val base64Audio = inlineData.optString("data", "")
                        if (mimeType.startsWith("audio/pcm") && base64Audio.isNotEmpty()) {
                            isModelTurnComplete.set(false)
                            val pcmBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                            if (pcmBytes.isNotEmpty() && !_isSpeakerMuted.value) {
                                isAiAudioPlaying.set(true)
                                _voiceState.value = VoiceCallState.SPEAKING
                                pcmPlaybackChannel.trySend(gen to pcmBytes)
                            }
                        }
                    }
                }
            }

            // 6. Turn Complete -> Persist turn transcripts & enqueue sentinel to transition smoothly after speaker drains
            if (serverContent.optBoolean("turnComplete", false)) {
                isModelTurnComplete.set(true)
                val finalUser = userTranscriptBuffer.toString().trim()
                val finalModel = modelTranscriptBuffer.toString().trim()
                if (finalUser.isNotEmpty() || finalModel.isNotEmpty()) {
                    onTurnCompleted?.invoke(finalUser, finalModel)
                }
                userTranscriptBuffer.setLength(0)

                // Enqueue empty sentinel chunk so state transitions to LISTENING after AudioTrack finishes + reverb decays
                pcmPlaybackChannel.trySend(playbackGeneration.get() to ByteArray(0))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Gemini Live frame: ${e.message}")
        }
    }

    /**
     * Sends a text message or quick prompt directly into the active Gemini Live session
     * so Gemini responds immediately with native 24kHz voice audio.
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
        // Activate echo guard slightly ahead of incoming greeting/response audio so mic stays clean
        echoTailUntilElapsedMs.set(SystemClock.elapsedRealtime() + 450L)
        _voiceState.value = VoiceCallState.THINKING

        val payload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("text", trimmed)
            })
        }
        webSocket?.send(payload.toString())
    }

    /**
     * Explicitly interrupts AI voice playback (manual user barge-in button) and listens to the user immediately.
     */
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

            audioTrack = track
            try {
                track.play()
                for ((gen, pcmChunk) in pcmPlaybackChannel) {
                    if (!isActive || !isSessionActive.get()) break
                    if (gen != playbackGeneration.get()) continue

                    if (pcmChunk.isEmpty()) {
                        // Sentinel marking end of AI speech turn:
                        // Hold mic gate for ECHO_TAIL_COOLDOWN_MS while hardware AudioTrack drains & room echo decays
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

                    // Write in ~80ms slices (3840 bytes at 24kHz 16-bit) so lip-sync RMS updates at ~12.5 Hz smoothly!
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

            // Prefer VOICE_COMMUNICATION for hardware Acoustic Echo Cancellation; fallback to MIC if needed
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

                    // Always read from AudioRecord to drain OS microphone ringbuffer without overflow
                    val readBytes = recorder.read(buffer, 0, buffer.size)
                    if (readBytes <= 0) {
                        delay(20)
                        continue
                    }

                    if (_isMicMuted.value) {
                        continue
                    }

                    // CRITICAL ECHO GUARD:
                    // While Ramses's voice is playing through the speaker (or decaying in the room),
                    // stream zeroed silence PCM instead of speaker-contaminated mic audio so Gemini's
                    // VAD never mistakes Ramses's own voice for user interruption!
                    if (isEchoGateActive()) {
                        val silentSlice = if (readBytes == silenceBuffer.size) silenceBuffer else ByteArray(readBytes)
                        val silentB64 = Base64.encodeToString(silentSlice, Base64.NO_WRAP)
                        val silentFrame = JSONObject().apply {
                            put("realtimeInput", JSONObject().apply {
                                put("audio", JSONObject().apply {
                                    put("mimeType", "audio/pcm;rate=16000")
                                    put("data", silentB64)
                                })
                            })
                        }
                        webSocket?.send(silentFrame.toString())
                        continue
                    }

                    if (_voiceState.value == VoiceCallState.LISTENING) {
                        val micRms = computePcmRmsDb(buffer, 0, readBytes)
                        _liveRmsDb.value = micRms
                    }

                    val chunkToEncode = if (readBytes == buffer.size) buffer else buffer.copyOf(readBytes)
                    val b64 = Base64.encodeToString(chunkToEncode, Base64.NO_WRAP)

                    val audioFrame = JSONObject().apply {
                        put("realtimeInput", JSONObject().apply {
                            put("audio", JSONObject().apply {
                                put("mimeType", "audio/pcm;rate=16000")
                                put("data", b64)
                            })
                        })
                    }
                    webSocket?.send(audioFrame.toString())
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

    /**
     * Enables hardware DSP Acoustic Echo Cancellation and Noise Suppression.
     * Deliberately omits AutomaticGainControl (AGC) because AGC boosts residual loudspeaker
     * reflections into the voice-activity threshold on mobile speakerphones.
     */
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

    /**
     * Computes a responsive 0f..15f dB-like amplitude metric from 16-bit little-endian PCM bytes
     * to drive 3D Avatar mouth morphing and equalizer bars smoothly.
     */
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
        // Map [-50dBFS .. -6dBFS] into [0f .. 15f]
        val dbFs = 20.0 * log10(rms)
        return (((dbFs + 48.0) / 42.0) * 15.0).toFloat().coerceIn(0f, 15f)
    }

    fun stopSession() {
        isSessionActive.set(false)
        isSetupComplete.set(false)
        isAiAudioPlaying.set(false)
        echoTailUntilElapsedMs.set(0L)
        playbackGeneration.incrementAndGet()

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
