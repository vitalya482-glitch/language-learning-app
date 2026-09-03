package kz.lvk.languagelearning.core.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SpeechRecognitionState(
    val isAvailable: Boolean,
    val isListening: Boolean = false,
    val isFinalizing: Boolean = false,
    val rmsDb: Float = 0f,
    val partialText: String = "",
    val finalText: String = "",
    val errorMessage: String? = null,
)

class AndroidOnDeviceSpeechRecognizer(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    private val recognizer: SpeechRecognizer? = if (available) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
    } else {
        null
    }

    private val _state = MutableStateFlow(SpeechRecognitionState(isAvailable = available))
    val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onRmsChanged(rmsdB: Float) {
                _state.update { it.copy(rmsDb = rmsdB) }
            }

            override fun onEndOfSpeech() {
                _state.update { it.copy(isListening = false, isFinalizing = true) }
            }

            override fun onError(error: Int) {
                _state.update {
                    it.copy(
                        isListening = false,
                        isFinalizing = false,
                        rmsDb = 0f,
                        errorMessage = errorMessage(error),
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                _state.update {
                    it.copy(
                        isListening = false,
                        isFinalizing = false,
                        rmsDb = 0f,
                        partialText = "",
                        finalText = text,
                        errorMessage = null,
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                _state.update { it.copy(partialText = firstResult(partialResults)) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    fun startListening(languageTag: String) {
        val activeRecognizer = recognizer
        if (activeRecognizer == null) {
            _state.update {
                it.copy(errorMessage = "On-device speech recognition is not available on this device")
            }
            return
        }

        _state.update {
            it.copy(
                isListening = true,
                isFinalizing = false,
                rmsDb = 0f,
                partialText = "",
                finalText = "",
                errorMessage = null,
            )
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        activeRecognizer.startListening(intent)
    }

    fun stopListening() {
        if (_state.value.isListening) {
            _state.update { it.copy(isListening = false, isFinalizing = true) }
            recognizer?.stopListening()
        }
    }

    override fun close() {
        recognizer?.destroy()
    }

    private fun firstResult(bundle: Bundle?): String =
        bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Offline speech recognizer requested network access"
        SpeechRecognizer.ERROR_NO_MATCH -> "Speech was not recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
        SpeechRecognizer.ERROR_SERVER -> "Speech recognizer service error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Speech recognition error: $error"
    }
}
