package kz.lvk.languagelearning.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.lvk.languagelearning.core.ai.LanguageModelEngine
import kz.lvk.languagelearning.core.ai.LanguageModelRequest
import kz.lvk.languagelearning.core.ai.LocalModelDescriptor

class ConversationViewModel(
    private val engine: LanguageModelEngine,
    private val model: LocalModelDescriptor?,
    nativeLanguageTag: String,
    targetLanguageTag: String,
    learningLevel: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var nextMessageId = 0L

    private val tutorSystemPrompt = """
        You are a concise, friendly language tutor running fully offline on the learner's phone.
        The learner's native language is $nativeLanguageTag.
        The language being learned is $targetLanguageTag.
        The learner's CEFR level is $learningLevel.

        For each learner message:
        - Always start with exactly one machine-readable TTS block in this format:
          [[SPEAK]]one short natural reply or question in the target language[[/SPEAK]]
        - The text inside [[SPEAK]] must contain only the phrase that should be spoken aloud in the target language.
          Do not put explanations, translations, labels or quotation marks inside the TTS block.
        - After the TTS block, if there is a mistake, show a corrected natural version in the target language.
        - Briefly explain the important correction in the learner's native language.
        - If useful, give one short translation or hint in the native language.
        - Continue the visible feedback with the same short natural reply or question in the target language.
        - If the learner writes in the native language, translate the intended phrase into the target language and continue.
        - If the learner's phrase is already correct, say so briefly and continue.
        - Keep the complete visible answer under about 80 words.
        - Do not output hidden reasoning or a thinking section.
    """.trimIndent()

    init {
        loadEngine()
    }

    fun loadEngine() {
        _state.update { it.copy(isEngineReady = false, errorMessage = null) }

        val installedModel = model
        if (installedModel == null) {
            _state.update {
                it.copy(
                    errorMessage = "Локальная AI-модель не установлена. Откройте Настройки → Локальные AI-модели.",
                )
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                engine.load(installedModel)
            }.onSuccess {
                _state.update { it.copy(isEngineReady = true) }
            }.onFailure { error ->
                _state.update {
                    it.copy(errorMessage = error.message ?: "Unable to load local AI model")
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val userText = text.trim()
        val currentState = _state.value
        if (userText.isEmpty() || !currentState.isEngineReady || currentState.isGenerating) return

        val userMessage = ConversationMessage(
            id = nextMessageId++,
            text = userText,
            role = ConversationRole.User,
        )
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                isGenerating = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                engine.generate(
                    LanguageModelRequest(
                        systemPrompt = tutorSystemPrompt,
                        userText = userText,
                    ),
                )
            }.onSuccess { response ->
                val parsedResponse = parseTutorResponse(response.text)
                val assistantMessage = ConversationMessage(
                    id = nextMessageId++,
                    text = parsedResponse.visibleText,
                    role = ConversationRole.Assistant,
                    spokenText = parsedResponse.spokenText,
                )
                _state.update {
                    it.copy(
                        messages = it.messages + assistantMessage,
                        isGenerating = false,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = error.message ?: "Local AI generation failed",
                    )
                }
            }
        }
    }

    class Factory(
        private val engine: LanguageModelEngine,
        private val model: LocalModelDescriptor?,
        private val nativeLanguageTag: String,
        private val targetLanguageTag: String,
        private val learningLevel: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
                "Unsupported ViewModel class: ${modelClass.name}"
            }
            return ConversationViewModel(
                engine = engine,
                model = model,
                nativeLanguageTag = nativeLanguageTag,
                targetLanguageTag = targetLanguageTag,
                learningLevel = learningLevel,
            ) as T
        }
    }
}

private data class ParsedTutorResponse(
    val visibleText: String,
    val spokenText: String?,
)

private val ttsBlockRegex = Regex(
    pattern = """(?s)\[\[SPEAK\]\](.*?)\[\[/SPEAK\]\]""",
)

private val legacySpokenReplyRegex = Regex(
    pattern = """(?im)^\s*(?:short\s+reply|spoken\s+reply|reply)\s*:\s*[\"“]?(.+?)[\"”]?\s*$""",
)

private fun parseTutorResponse(rawText: String): ParsedTutorResponse {
    val blockMatch = ttsBlockRegex.find(rawText)
    val markerSpokenText = blockMatch
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    val legacySpokenText = legacySpokenReplyRegex
        .find(rawText)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.trim('"', '“', '”')
        ?.takeIf { it.isNotBlank() }

    val visibleText = rawText
        .replace(ttsBlockRegex, "")
        .trim()
        .ifBlank { rawText.trim() }

    return ParsedTutorResponse(
        visibleText = visibleText,
        spokenText = markerSpokenText ?: legacySpokenText,
    )
}
