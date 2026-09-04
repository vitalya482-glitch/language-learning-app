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
        You are a friendly language tutor. The learner speaks $nativeLanguageTag and studies
        $targetLanguageTag at CEFR level $learningLevel. First answer the learner directly in one
        short, natural sentence in $targetLanguageTag. Never repeat the learner's question as your
        answer. On the next line, briefly praise or correct the learner in $nativeLanguageTag.
        If there is a mistake, show the correct $targetLanguageTag phrase. Use no headings, tags,
        quotes, Markdown, or hidden reasoning. Keep everything under 45 words.
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
                        userText = buildModelInput(currentState.messages, userText),
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

internal data class ParsedTutorResponse(
    val visibleText: String,
    val spokenText: String?,
)

private val speakOpenMarkerRegex = Regex(
    pattern = """(?i)\[\s*\[\s*SPEAK\s*\](?:\s*[\"”']?\s*\])?""",
)

private val speakCloseMarkerRegex = Regex(
    pattern = """(?i)\[\s*\[\s*/\s*SPEAK\s*\](?:\s*[\"”']?\s*\])?""",
)

private val anySpeakMarkerRegex = Regex(
    pattern = """(?i)\[\s*\[\s*/?\s*SPEAK\s*\](?:\s*[\"”']?\s*\])?""",
)

private val spokenLineRegex = Regex(
    pattern = """(?im)^\s*(?:say|speak|short\s+reply|spoken\s+reply|reply)\s*:\s*[\"“]?(.+?)[\"”]?\s*$""",
)

private val feedbackLabelRegex = Regex(
    pattern = """(?im)^\s*(?:feedback|correction|explanation)\s*:\s*""",
)

internal fun parseTutorResponse(rawText: String): ParsedTutorResponse {
    val openMarker = speakOpenMarkerRegex.find(rawText)
    val closeMarker = openMarker?.let {
        speakCloseMarkerRegex.find(rawText, it.range.last + 1)
    }
    val taggedSpokenText = openMarker?.let { open ->
        val contentStart = open.range.last + 1
        val contentEnd = closeMarker?.range?.first
            ?: rawText.indexOf('\n', contentStart).takeIf { it >= 0 }
            ?: rawText.length
        rawText.substring(contentStart, contentEnd)
            .trimForTutorOutput()
            .takeIf { it.isNotBlank() }
    }

    val labelledSpokenText = spokenLineRegex
        .find(rawText)
        ?.groupValues
        ?.getOrNull(1)
        ?.trimForTutorOutput()
        ?.takeIf { it.isNotBlank() }

    val withoutTaggedBlock = if (openMarker != null && closeMarker != null) {
        rawText.removeRange(openMarker.range.first, closeMarker.range.last + 1)
    } else {
        rawText.replace(anySpeakMarkerRegex, "")
    }
    val withoutProtocolLabels = withoutTaggedBlock
        .replace(spokenLineRegex, "")
        .replace(feedbackLabelRegex, "")
        .replace(anySpeakMarkerRegex, "")
        .trimForTutorOutput()

    val explicitSpokenText = taggedSpokenText ?: labelledSpokenText
    val spokenText = explicitSpokenText
        ?: withoutProtocolLabels.lineSequence().firstOrNull { it.isNotBlank() }?.trimForTutorOutput()
    val visibleText = buildList {
        if (withoutProtocolLabels.isNotBlank()) add(withoutProtocolLabels)
        if (!explicitSpokenText.isNullOrBlank() && none { it == explicitSpokenText }) {
            add(explicitSpokenText)
        }
    }.joinToString("\n\n").ifBlank {
        spokenText ?: "The local tutor did not return a readable answer."
    }

    return ParsedTutorResponse(
        visibleText = visibleText,
        spokenText = spokenText,
    )
}

private fun String.trimForTutorOutput(): String =
    trim(' ', '\t', '\r', '\n', '"', '\'', '“', '”')

private fun buildModelInput(
    messages: List<ConversationMessage>,
    userText: String,
): String {
    if (messages.isEmpty()) return userText

    val recentConversation = messages.takeLast(6).joinToString("\n") { message ->
        val role = if (message.role == ConversationRole.User) "Learner" else "Tutor"
        "$role: ${message.text.replace('\n', ' ')}"
    }
    return """
        Recent conversation:
        $recentConversation

        Current learner message:
        $userText
    """.trimIndent()
}
