package com.example.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechHelper(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val arabicLocale = Locale.forLanguageTag("ar-EG")
                val result = tts?.setLanguage(arabicLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to generic Arabic or default
                    tts?.language = Locale.forLanguageTag("ar")
                }
                // Regal Egyptian voice tone: slightly deeper pitch, dignified cadence
                tts?.setPitch(0.92f)
                tts?.setSpeechRate(0.96f)
                isInitialized = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                        Log.w("TTS", "TTS error code: $errorCode")
                    }
                })
            } else {
                Log.e("TTS", "TextToSpeech init failed with status: $status")
            }
        }
    }

    fun speak(text: String, onDoneCallback: (() -> Unit)? = null) {
        if (!isInitialized || tts == null) return
        stop()

        // Clean text of markdown symbols like * or # for smooth natural speech
        val cleaned = text
            .replace(Regex("[*#_`~>\\[\\]()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val utteranceId = "ramses_speech_${System.currentTimeMillis()}"
        _isSpeaking.value = true
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isSpeaking.value = false
    }
}
