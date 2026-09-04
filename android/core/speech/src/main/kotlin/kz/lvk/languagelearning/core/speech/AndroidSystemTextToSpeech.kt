package kz.lvk.languagelearning.core.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
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
    val activeUtteranceId: String? = null,
    val lastFinishedUtteranceId: String? = null,
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
    private val activeUtteranceId = AtomicReference<String?>(null)

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

    fun speak(
        text: String,
        language: SpeechLanguage = currentLanguage,
        voiceId: String? = null,
    ): String? {
        if (text.isBlank()) return null

        val requestedVoiceId = voiceId ?: preferredVoiceId.takeIf {
            language.tag == currentLanguage.tag
        }
        if (
            language.tag != currentLanguage.tag ||
            _state.value.languageTag != language.tag ||
            (requestedVoiceId != null && _state.value.selectedVoiceId != requestedVoiceId)
        ) {
            prepare(language, requestedVoiceId)
        }

        val activeEngine = engine
        if (!initialized || activeEngine == null || !_state.value.isReady) {
            _state.update {
                it.copy(errorMessage = "System Text-to-Speech is not ready")
            }
            return null
        }

        val utteranceId = "language-learning-${System.nanoTime()}"
        activeUtteranceId.set(utteranceId)
        // Publish the queued state before calling Android. Very short utterances may finish
        // on another thread immediately after speak() returns.
        _state.update {
            it.copy(
                isSpeaking = true,
                activeUtteranceId = utteranceId,
                errorMessage = null,
            )
        }
        val result = activeEngine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId,
        )

        if (result == TextToSpeech.ERROR) {
            activeUtteranceId.compareAndSet(utteranceId, null)
            _state.update {
                it.copy(
                    isSpeaking = false,
                    activeUtteranceId = null,
                    errorMessage = "Android Text-to-Speech could not start playback",
                )
            }
            return null
        }
        return utteranceId
    }

    fun stop() {
        activeUtteranceId.set(null)
        engine?.stop()
        _state.update { it.copy(isSpeaking = false, activeUtteranceId = null) }
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
                if (utteranceId == activeUtteranceId.get()) {
                    _state.update {
                        it.copy(
                            isSpeaking = true,
                            activeUtteranceId = utteranceId,
                            errorMessage = null,
                        )
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                finishUtterance(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishUtterance(utteranceId, "System Text-to-Speech playback failed")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                finishUtterance(
                    utteranceId,
                    "System Text-to-Speech playback failed: $errorCode",
                )
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                finishUtterance(utteranceId)
            }
        })
    }

    fun isSpeakingNow(): Boolean = engine?.isSpeaking == true

    private fun finishUtterance(utteranceId: String?, errorMessage: String? = null) {
        if (utteranceId == null || !activeUtteranceId.compareAndSet(utteranceId, null)) return

        _state.update {
            it.copy(
                isSpeaking = false,
                activeUtteranceId = null,
                lastFinishedUtteranceId = utteranceId,
                errorMessage = errorMessage,
            )
        }
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
