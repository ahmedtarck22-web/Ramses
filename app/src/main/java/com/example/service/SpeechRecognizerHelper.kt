package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechRecognizerHelper(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    var onSpeechCompleted: ((String) -> Unit)? = null
    var onPartialSpeech: ((String) -> Unit)? = null
    var onErrorOccurred: ((String, Int) -> Unit)? = null
    var onEndOfSpeechDetected: (() -> Unit)? = null

    fun startListening(languageCode: String = "ar-EG") {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListening(languageCode) }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("SpeechHelper", "SpeechRecognizer not available on this device")
            onErrorOccurred?.invoke("التعرف على الصوت غير مدعوم على هذا الجهاز / Speech recognition unavailable", -1)
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsDb.value = rmsdB
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        mainHandler.post { onEndOfSpeechDetected?.invoke() }
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error in speech service"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out"
                            else -> "Speech recognition error code $error"
                        }
                        Log.d("SpeechHelper", "SpeechRecognizer error: $errorMsg ($error)")
                        mainHandler.post { onErrorOccurred?.invoke(errorMsg, error) }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull()?.trim() ?: ""
                        if (spokenText.isNotBlank()) {
                            _recognizedText.value = spokenText
                            mainHandler.post { onSpeechCompleted?.invoke(spokenText) }
                        } else {
                            mainHandler.post {
                                onErrorOccurred?.invoke("No speech recognized", SpeechRecognizer.ERROR_NO_MATCH)
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            _recognizedText.value = text
                            mainHandler.post { onPartialSpeech?.invoke(text) }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val langTag = if (languageCode.startsWith("en", ignoreCase = true)) "en-US" else "ar-EG"
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            Log.e("SpeechHelper", "Failed to start speech recognition", e)
            mainHandler.post {
                onErrorOccurred?.invoke(e.localizedMessage ?: "Failed to start speech recognition", -2)
            }
        }
    }

    fun stopListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stopListening() }
            return
        }

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignored safely
        } finally {
            speechRecognizer = null
            _isListening.value = false
        }
    }
}
