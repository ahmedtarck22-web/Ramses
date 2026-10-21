package com.example.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechHelper(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var currentDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val arabicLocale = Locale.forLanguageTag("ar-EG")
                val result = tts?.setLanguage(arabicLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                // Regal Egyptian/Pharaonic voice tone: dignified cadence
                tts?.setPitch(0.94f)
                tts?.setSpeechRate(0.98f)
                isInitialized = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.removeCallbacksAndMessages(null)
                        _isSpeaking.value = false
                        mainHandler.post {
                            val cb = currentDoneCallback
                            currentDoneCallback = null
                            cb?.invoke()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.removeCallbacksAndMessages(null)
                        _isSpeaking.value = false
                        mainHandler.post {
                            val cb = currentDoneCallback
                            currentDoneCallback = null
                            cb?.invoke()
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mainHandler.removeCallbacksAndMessages(null)
                        _isSpeaking.value = false
                        Log.w("TTS", "TTS error code: $errorCode")
                        mainHandler.post {
                            val cb = currentDoneCallback
                            currentDoneCallback = null
                            cb?.invoke()
                        }
                    }
                })
            } else {
                Log.e("TTS", "TextToSpeech init failed with status: $status")
            }
        }
    }

    fun speak(text: String, languageCode: String? = null, onDoneCallback: (() -> Unit)? = null) {
        if (!isInitialized || tts == null) {
            mainHandler.postDelayed({
                if (isInitialized && tts != null) {
                    speak(text, languageCode, onDoneCallback)
                } else {
                    onDoneCallback?.invoke()
                }
            }, 600)
            return
        }
        stop()

        currentDoneCallback = onDoneCallback

        // Clean text of markdown symbols like * or # for smooth natural speech
        val cleaned = text
            .replace(Regex("[*#_`~>\\[\\]()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank()) {
            mainHandler.post { onDoneCallback?.invoke() }
            return
        }

        // Detect if text is mostly English or Arabic
        val isEnglish = when {
            languageCode?.startsWith("en", ignoreCase = true) == true -> true
            languageCode?.startsWith("ar", ignoreCase = true) == true -> false
            else -> {
                val arabicChars = cleaned.count { it in '\u0600'..'\u06FF' }
                val latinChars = cleaned.count { it in 'a'..'z' || it in 'A'..'Z' }
                latinChars > arabicChars
            }
        }

        try {
            if (isEnglish) {
                tts?.language = Locale.ENGLISH
                tts?.setPitch(0.96f)
                tts?.setSpeechRate(1.05f)
            } else {
                val arResult = tts?.setLanguage(Locale.forLanguageTag("ar-EG"))
                if (arResult == TextToSpeech.LANG_MISSING_DATA || arResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.forLanguageTag("ar")
                }
                tts?.setPitch(0.96f)
                tts?.setSpeechRate(1.06f)
            }
        } catch (e: Exception) {
            Log.w("TTS", "Error setting language", e)
        }

        val utteranceId = "ramses_speech_${System.currentTimeMillis()}"
        _isSpeaking.value = true
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        // Safety fallback watchdog in case UtteranceProgressListener does not fire on this device
        val fallbackDurationMs = (cleaned.split(" ").size * 320L + 1500L).coerceIn(2500L, 16000L)
        mainHandler.postDelayed({
            if (_isSpeaking.value) {
                _isSpeaking.value = false
                val cb = currentDoneCallback
                currentDoneCallback = null
                cb?.invoke()
            }
        }, fallbackDurationMs)
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        tts?.stop()
        _isSpeaking.value = false
        currentDoneCallback = null
    }

    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isSpeaking.value = false
        currentDoneCallback = null
    }
}
