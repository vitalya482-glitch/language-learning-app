package kz.lvk.languagelearning.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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

    private val analysisSystemPrompt = """
        You are the careful analysis stage of a language tutor. The learner speaks
        $nativeLanguageTag and studies $targetLanguageTag at CEFR level $learningLevel. Focus on
        the CURRENT learner message, while using recent conversation only as context. Identify its
        exact meaning and topic, then explain any grammar or word-choice issue and give a natural
        corrected phrase. If it is already natural, say so. Write 1-3 concise learner-facing
        sentences in simple $targetLanguageTag. Do not answer the learner yet. Do not give generic
        praise, repeat an earlier topic, use headings, or expose hidden reasoning.
    """.trimIndent()

    private val replySystemPrompt = """
        You are the response stage of a friendly language tutor. The learner speaks
        $nativeLanguageTag and studies $targetLanguageTag at CEFR level $learningLevel. Use the
        supplied analysis to answer the CURRENT learner message directly. Write 2-4 natural,
        specific sentences in $targetLanguageTag and finish with one relevant question that keeps
        the same topic moving. Never fall back to generic praise, offers to start, or a topic from
        an earlier turn. Do not repeat the analysis, use headings, tags, or hidden reasoning.
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
                generationPhase = ConversationGenerationPhase.Analyzing,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                val conversationInput = buildModelInput(currentState.messages, userText)
                val analysis = generateStageWithRetry(
                    LanguageModelRequest(
                        systemPrompt = analysisSystemPrompt,
                        userText = conversationInput,
                        thinkingEnabled = true,
                        maxOutputTokens = ANALYSIS_MAX_OUTPUT_TOKENS,
                    ),
                )

                _state.update {
                    it.copy(generationPhase = ConversationGenerationPhase.Composing)
                }

                val reply = generateStageWithRetry(
                    LanguageModelRequest(
                        systemPrompt = replySystemPrompt,
                        userText = """
                            Conversation and current learner message:
                            $conversationInput

                            Completed language analysis:
                            $analysis
                        """.trimIndent(),
                        thinkingEnabled = false,
                        maxOutputTokens = REPLY_MAX_OUTPUT_TOKENS,
                    ),
                    rejectBoilerplate = true,
                )

                composeTutorResponse(analysis, reply)
            }.onSuccess { parsedResponse ->
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
                        generationPhase = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isGenerating = false,
                        generationPhase = null,
                        errorMessage = error.message ?: "Local AI generation failed",
                    )
                }
            }
        }
    }

    private suspend fun generateStageWithRetry(
        request: LanguageModelRequest,
        rejectBoilerplate: Boolean = false,
    ): String {
        var lastFailure: Throwable? = null

        repeat(MAX_GENERATION_ATTEMPTS) {
            try {
                val text = engine.generate(request).text.cleanGenerationStage()
                if (
                    text.length >= MIN_USABLE_STAGE_LENGTH &&
                    (!rejectBoilerplate || !text.isKnownBoilerplateResponse())
                ) {
                    return text
                }
                lastFailure = IllegalStateException("The local model returned an incomplete answer")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val emptyResponse = error.message
                    ?.contains("empty response", ignoreCase = true) == true
                if (!emptyResponse) throw error
                lastFailure = error
            }
        }

        throw IllegalStateException(
            "The local model did not return a usable answer after $MAX_GENERATION_ATTEMPTS attempts",
            lastFailure,
        )
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

private val anySpeakMarkerRegex = Regex(
    pattern = """(?i)\[\s*\[\s*/?\s*SPEAK\s*\](?:\s*[\"”']?\s*\])?""",
)

private val legacySpokenLineRegex = Regex(
    pattern = """(?im)^\s*(?:say|speak|short\s+reply|spoken\s+reply)\s*:\s*[\"“]?(.+?)[\"”]?\s*$""",
)

private val feedbackLabelRegex = Regex(
    pattern = """(?im)^\s*(?:feedback|correction|explanation)\s*:\s*""",
)

private val spokenSectionLabelRegex = Regex(
    pattern = """(?im)^\s*(?:analysis|reply)\s*:\s*""",
)

internal fun parseTutorResponse(rawText: String): ParsedTutorResponse {
    val visibleText = rawText
        .replace(anySpeakMarkerRegex, "")
        .replace(legacySpokenLineRegex) { match -> match.groupValues[1] }
        .replace(feedbackLabelRegex, "")
        .trimForTutorOutput()
        .ifBlank {
            "The local tutor did not return a readable answer."
        }
    val spokenText = visibleText
        .replace(spokenSectionLabelRegex, "")
        .trimForTutorOutput()
        .takeIf { it.isNotBlank() }

    return ParsedTutorResponse(
        visibleText = visibleText,
        spokenText = spokenText,
    )
}

private const val MAX_GENERATION_ATTEMPTS = 3
private const val MIN_USABLE_STAGE_LENGTH = 12
private const val ANALYSIS_MAX_OUTPUT_TOKENS = 512
private const val REPLY_MAX_OUTPUT_TOKENS = 256
private const val MAX_HISTORY_CHARS = 1_200
private const val MAX_CURRENT_MESSAGE_CHARS = 800

private val generationStageHeadingRegex = Regex(
    pattern = """(?im)^\s*(?:analysis|language analysis|reply|answer|response)\s*:\s*""",
)

private fun String.cleanGenerationStage(): String =
    replace(generationStageHeadingRegex, "")
        .trimForTutorOutput()

private fun String.isKnownBoilerplateResponse(): Boolean {
    val normalized = lowercase()
    return listOf(
        "would you like to start",
        "i'm here to help with pistols",
        "you are asking for help",
        "you're asking for help",
        "which is a good start",
    ).any(normalized::contains)
}

internal fun composeTutorResponse(analysis: String, reply: String): ParsedTutorResponse =
    parseTutorResponse(
        """
            Analysis: ${analysis.cleanGenerationStage()}

            Reply: ${reply.cleanGenerationStage()}
        """.trimIndent(),
    )

private fun String.trimForTutorOutput(): String =
    trim(' ', '\t', '\r', '\n', '"', '\'', '“', '”')

private fun buildModelInput(
    messages: List<ConversationMessage>,
    userText: String,
): String {
    if (messages.isEmpty()) return userText

    var remainingCharacters = MAX_HISTORY_CHARS
    val recentConversation = messages
        .asReversed()
        .mapNotNull { message ->
            if (remainingCharacters <= 0) return@mapNotNull null

            val role = if (message.role == ConversationRole.User) "Learner" else "Tutor"
            val line = "$role: ${message.text.replace('\n', ' ')}"
            val retainedLine = line.take(remainingCharacters)
            remainingCharacters -= retainedLine.length
            retainedLine
        }
        .asReversed()
        .joinToString("\n")
    return """
        Recent conversation:
        $recentConversation

        Current learner message:
        ${userText.take(MAX_CURRENT_MESSAGE_CHARS)}
    """.trimIndent()
}
