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
    val languageModelDownloadRequired: Boolean = false,
    val languageModelDownloadRequested: Boolean = false,
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

    private var lastLanguageTag: String = "en-US"

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
                val languageDownloadRequired =
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

                _state.update {
                    it.copy(
                        isListening = false,
                        isFinalizing = false,
                        rmsDb = 0f,
                        errorMessage = errorMessage(error),
                        languageModelDownloadRequired = languageDownloadRequired,
                        languageModelDownloadRequested = false,
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
                        languageModelDownloadRequired = false,
                        languageModelDownloadRequested = false,
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

        lastLanguageTag = languageTag
        _state.update {
            it.copy(
                isListening = true,
                isFinalizing = false,
                rmsDb = 0f,
                partialText = "",
                finalText = "",
                errorMessage = null,
                languageModelDownloadRequired = false,
                languageModelDownloadRequested = false,
            )
        }

        activeRecognizer.startListening(recognitionIntent(languageTag))
    }

    fun stopListening() {
        if (_state.value.isListening) {
            _state.update { it.copy(isListening = false, isFinalizing = true) }
            recognizer?.stopListening()
        }
    }

    fun requestLanguageModelDownload(languageTag: String = lastLanguageTag) {
        val activeRecognizer = recognizer
        if (activeRecognizer == null) {
            _state.update {
                it.copy(errorMessage = "On-device speech recognition is not available on this device")
            }
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _state.update {
                it.copy(
                    languageModelDownloadRequired = false,
                    languageModelDownloadRequested = false,
                    errorMessage = "Android 13 or newer is required to request an offline speech language model",
                )
            }
            return
        }

        lastLanguageTag = languageTag
        _state.update {
            it.copy(
                isListening = false,
                isFinalizing = false,
                languageModelDownloadRequired = false,
                languageModelDownloadRequested = true,
                errorMessage = null,
            )
        }

        runCatching {
            activeRecognizer.triggerModelDownload(recognitionIntent(languageTag))
        }.onFailure { error ->
            _state.update {
                it.copy(
                    languageModelDownloadRequested = false,
                    languageModelDownloadRequired = true,
                    errorMessage = error.message ?: "Unable to request speech language model download",
                )
            }
        }
    }

    override fun close() {
        recognizer?.destroy()
    }

    private fun recognitionIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
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
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
            "The requested language is not supported by the on-device speech recognizer"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "The requested language is supported but its offline speech model is not downloaded"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Offline speech recognizer requested network access"
        SpeechRecognizer.ERROR_NO_MATCH -> "Speech was not recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
        SpeechRecognizer.ERROR_SERVER -> "Speech recognizer service error"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Speech recognizer service disconnected"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many speech recognition requests"
        else -> "Speech recognition error: $error"
    }
}
