package kz.lvk.languagelearning.core.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SystemTtsVoice(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val requiresNetwork: Boolean,
)

data class SystemTextToSpeechState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val languageTag: String? = null,
    val voices: List<SystemTtsVoice> = emptyList(),
    val selectedVoiceId: String? = null,
    val errorMessage: String? = null,
)

class AndroidSystemTextToSpeech(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext

    private var engine: TextToSpeech? = null
    private var initialized = false
    private var currentLanguage: SpeechLanguage = SpeechLanguages.English
    private var preferredVoiceId: String? = null

    private val _state = MutableStateFlow(SystemTextToSpeechState())
    val state: StateFlow<SystemTextToSpeechState> = _state.asStateFlow()

    init {
        engine = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initialized = true
                installUtteranceListener()
                prepare(currentLanguage, preferredVoiceId)
            } else {
                _state.update {
                    it.copy(
                        isReady = false,
                        errorMessage = "Android Text-to-Speech initialization failed",
                    )
                }
            }
        }
    }

    fun prepare(language: SpeechLanguage, preferredVoiceId: String? = null) {
        currentLanguage = language
        this.preferredVoiceId = preferredVoiceId
        val activeEngine = engine
        if (!initialized || activeEngine == null) {
            return
        }

        val requestedLocale = Locale.forLanguageTag(language.tag)
        val availability = activeEngine.setLanguage(requestedLocale)
        if (
            availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            _state.value = SystemTextToSpeechState(
                isReady = false,
                languageTag = language.tag,
                errorMessage = "System Text-to-Speech does not have a voice for ${language.displayName()}",
            )
            return
        }

        val matchingVoices = activeEngine.voices
            .orEmpty()
            .filter { voice -> voice.locale.language == requestedLocale.language }
            .sortedWith(
                compareBy<Voice>(
                    { it.locale.toLanguageTag() != language.tag },
                    { it.isNetworkConnectionRequired },
                    { it.name },
                ),
            )

        val selectedVoice = matchingVoices.firstOrNull { it.name == preferredVoiceId }
            ?: matchingVoices.firstOrNull { !it.isNetworkConnectionRequired }
            ?: matchingVoices.firstOrNull()

        if (selectedVoice != null) {
            activeEngine.voice = selectedVoice
            this.preferredVoiceId = selectedVoice.name
        }

        _state.value = SystemTextToSpeechState(
            isReady = matchingVoices.isNotEmpty(),
            languageTag = language.tag,
            voices = matchingVoices.map(::toVoiceOption),
            selectedVoiceId = selectedVoice?.name,
            errorMessage = if (matchingVoices.isEmpty()) {
                "Android did not report any system voices for ${language.displayName()}"
            } else {
                null
            },
        )
    }

    fun selectVoice(language: SpeechLanguage, voiceId: String) {
        currentLanguage = language
        val activeEngine = engine ?: return
        if (!initialized) return

        val voice = activeEngine.voices
            .orEmpty()
            .firstOrNull { it.name == voiceId }
            ?: return

        activeEngine.voice = voice
        preferredVoiceId = voiceId
        _state.update {
            it.copy(
                selectedVoiceId = voiceId,
                errorMessage = null,
            )
        }
    }

    fun speak(text: String, language: SpeechLanguage = currentLanguage) {
        if (text.isBlank()) return

        if (language.tag != currentLanguage.tag || _state.value.languageTag != language.tag) {
            prepare(language, preferredVoiceId)
        }

        val activeEngine = engine
        if (!initialized || activeEngine == null || !_state.value.isReady) {
            _state.update {
                it.copy(errorMessage = "System Text-to-Speech is not ready")
            }
            return
        }

        val utteranceId = "language-learning-${System.nanoTime()}"
        val result = activeEngine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId,
        )

        if (result == TextToSpeech.ERROR) {
            _state.update {
                it.copy(
                    isSpeaking = false,
                    errorMessage = "Android Text-to-Speech could not start playback",
                )
            }
        } else {
            // Reflect queued playback immediately. Some Android voices need a few seconds
            // before onStart(), which otherwise makes a successful tap look ignored.
            _state.update { it.copy(isSpeaking = true, errorMessage = null) }
        }
    }

    fun stop() {
        engine?.stop()
        _state.update { it.copy(isSpeaking = false) }
    }

    override fun close() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        initialized = false
    }

    private fun installUtteranceListener() {
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.update { it.copy(isSpeaking = true, errorMessage = null) }
            }

            override fun onDone(utteranceId: String?) {
                _state.update { it.copy(isSpeaking = false) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _state.update {
                    it.copy(
                        isSpeaking = false,
                        errorMessage = "System Text-to-Speech playback failed",
                    )
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _state.update {
                    it.copy(
                        isSpeaking = false,
                        errorMessage = "System Text-to-Speech playback failed: $errorCode",
                    )
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                _state.update { it.copy(isSpeaking = false) }
            }
        })
    }

    private fun toVoiceOption(voice: Voice): SystemTtsVoice {
        val localeName = voice.locale.getDisplayName(Locale.getDefault())
            .replaceFirstChar { first ->
                if (first.isLowerCase()) first.titlecase() else first.toString()
            }

        return SystemTtsVoice(
            id = voice.name,
            displayName = "$localeName · ${voice.name}",
            languageTag = voice.locale.toLanguageTag(),
            requiresNetwork = voice.isNetworkConnectionRequired,
        )
    }
}
