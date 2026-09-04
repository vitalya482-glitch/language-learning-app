package kz.lvk.languagelearning.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.Locale
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
    private val targetLanguageTag: String,
    learningLevel: String,
    includePhraseAnalysis: Boolean,
    private val includeNaturalPhrase: Boolean,
    includeConversationReply: Boolean,
    private val explanationLanguageTag: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var nextMessageId = 0L
    private val includePhraseAnalysis = includePhraseAnalysis
    private val includeConversationReply = includeConversationReply ||
        (!includePhraseAnalysis && !includeNaturalPhrase)
    private val nativeLanguageName = languageName(nativeLanguageTag)
    private val targetLanguageName = languageName(targetLanguageTag)
    private val explanationLanguageName = languageName(explanationLanguageTag)

    private val analysisSystemPrompt = """
        You are a $targetLanguageName teacher. The learner's native language is
        $nativeLanguageName and their level is $learningLevel. Analyze only the text marked
        CURRENT LEARNER PHRASE. Explain its meaning and the most important grammar or word-choice
        issue in $targetLanguageName only; never analyze it as $nativeLanguageName. Treat missing
        punctuation, capitalization, and harmless spoken-language brevity as correct. The first
        line must be exactly VERDICT: OK when the phrase is understandable and natural enough, or
        VERDICT: NEEDS_CORRECTION when a real wording or grammar correction is useful. After that,
        write 1-2 short sentences in $explanationLanguageName ($explanationLanguageTag). Do not
        continue the conversation, invent an intention, add a corrected example, use a list,
        Markdown, generic praise, or text from an earlier turn.
    """.trimIndent()

    private val naturalPhraseSystemPrompt = """
        Rewrite only the text marked CURRENT LEARNER PHRASE as one natural sentence in
        $targetLanguageName ($targetLanguageTag). Preserve the learner's exact meaning, point of
        view, names, job, place, and question. Do not add new facts or turn the learner's sentence
        into a tutor's reply. Keep a short greeting short. Return only the rewritten learner
        phrase, with no label, translation, quote marks, explanation, list, or Markdown.
    """.trimIndent()

    private val replySystemPrompt = """
        You are a friendly $targetLanguageName conversation partner. Respond directly to the text
        marked CURRENT LEARNER PHRASE in 1-3 concise sentences in $targetLanguageName
        ($targetLanguageTag), then ask one specific question that naturally continues its topic.
        Answer the learner's literal question before asking your question. The recent dialogue is
        context only. Never say that you understand the context, repeat an earlier answer, ask how
        you can help, ask the learner to provide more details when a direct answer is possible,
        give generic praise, or discuss an older topic. If the
        learner asks you to assess their language level, start the assessment with a concrete
        open-ended question instead of asking what they want to assess. Do not repeat the language
        analysis or use headings, tags, lists, or Markdown.
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

    fun retry() {
        val current = _state.value
        val lastMessage = current.messages.lastOrNull()
        if (
            current.isEngineReady &&
            !current.isGenerating &&
            current.errorMessage != null &&
            lastMessage?.role == ConversationRole.User
        ) {
            _state.update {
                it.copy(
                    messages = it.messages.dropLast(1),
                    errorMessage = null,
                )
            }
            sendMessage(lastMessage.text)
        } else {
            loadEngine()
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
                val conversationHistory = buildConversationHistory(currentState.messages)
                val analysisInput = buildAnalysisInput(conversationHistory, userText)
                val rawAnalysis = try {
                    generateStageWithRetry(
                        LanguageModelRequest(
                            systemPrompt = analysisSystemPrompt,
                            userText = analysisInput,
                            thinkingEnabled = true,
                            maxOutputTokens = ANALYSIS_MAX_OUTPUT_TOKENS,
                        ),
                        acceptLastNonEmptyAfterRetries = true,
                        isAcceptable = { candidate ->
                            parseLanguageAnalysis(candidate).text
                                .matchesExpectedLanguageScript(explanationLanguageTag)
                        },
                    )
                } catch (_: NoUsableModelStageException) {
                    unavailableAnalysisText(explanationLanguageTag)
                }
                val parsedAnalysis = parseLanguageAnalysis(rawAnalysis)
                val analysis = parsedAnalysis.text.ifBlank {
                    analysisVerdictFallback(
                        needsCorrection = parsedAnalysis.needsCorrection,
                        languageTag = explanationLanguageTag,
                    )
                }

                _state.update {
                    it.copy(generationPhase = ConversationGenerationPhase.Composing)
                }

                val naturalPhrase = if (
                    includeNaturalPhrase && parsedAnalysis.needsCorrection != false
                ) {
                    try {
                        generateStageWithRetry(
                            LanguageModelRequest(
                                systemPrompt = naturalPhraseSystemPrompt,
                                userText = buildNaturalPhraseInput(userText, analysis),
                                thinkingEnabled = false,
                                maxOutputTokens = NATURAL_PHRASE_MAX_OUTPUT_TOKENS,
                            ),
                            rejectBoilerplate = true,
                            minimumLength = MIN_SHORT_STAGE_LENGTH,
                            isAcceptable = { candidate ->
                                val cleaned = candidate.cleanNaturalPhrase()
                                cleaned.isPlausibleRewriteOf(userText) &&
                                    cleaned.matchesExpectedLanguageScript(targetLanguageTag)
                            },
                        ).cleanNaturalPhrase()
                            .takeIf { candidate ->
                                candidate.isMeaningfullyDifferentFrom(userText)
                            }
                    } catch (_: NoUsableModelStageException) {
                        null
                    }
                } else {
                    null
                }
                val reply = if (includeConversationReply) {
                    try {
                        generateStageWithRetry(
                            LanguageModelRequest(
                                systemPrompt = replySystemPrompt,
                                userText = buildReplyInput(
                                    conversationHistory = conversationHistory,
                                    userText = userText,
                                    analysis = analysis,
                                ),
                                thinkingEnabled = false,
                                maxOutputTokens = REPLY_MAX_OUTPUT_TOKENS,
                            ),
                            rejectBoilerplate = true,
                            acceptLastNonEmptyAfterRetries = true,
                            isAcceptable = { candidate ->
                                candidate.matchesExpectedLanguageScript(targetLanguageTag) &&
                                    currentState.messages
                                    .asSequence()
                                    .filter { it.role == ConversationRole.Assistant }
                                    .mapNotNull { it.conversationText }
                                    .none { previous -> candidate.isNearDuplicateOf(previous) }
                            },
                        )
                    } catch (_: NoUsableModelStageException) {
                        fallbackConversationReply(targetLanguageTag)
                    }
                } else {
                    null
                }

                composeTutorResponse(
                    analysis = analysis.takeIf { includePhraseAnalysis },
                    naturalPhrase = naturalPhrase,
                    reply = reply,
                    naturalPhraseIntroduction = naturalPhraseIntroduction(explanationLanguageTag),
                )
            }.onSuccess { parsedResponse ->
                val assistantMessage = ConversationMessage(
                    id = nextMessageId++,
                    text = parsedResponse.visibleText,
                    role = ConversationRole.Assistant,
                    spokenText = parsedResponse.spokenText,
                    speechSegments = parsedResponse.speechSegments,
                    conversationText = parsedResponse.conversationText,
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
        minimumLength: Int = MIN_USABLE_STAGE_LENGTH,
        acceptLastNonEmptyAfterRetries: Boolean = false,
        isAcceptable: (String) -> Boolean = { true },
    ): String {
        var lastFailure: Throwable? = null
        var lastNonEmptyText: String? = null

        repeat(MAX_GENERATION_ATTEMPTS) {
            try {
                val text = engine.generate(request).text.cleanGenerationStage()
                if (text.isNotBlank()) {
                    lastNonEmptyText = text
                }
                if (
                    text.length >= minimumLength &&
                    (!rejectBoilerplate || !text.isKnownBoilerplateResponse()) &&
                    isAcceptable(text)
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

        if (acceptLastNonEmptyAfterRetries && lastNonEmptyText != null) {
            return lastNonEmptyText
        }

        throw NoUsableModelStageException(
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
        private val includePhraseAnalysis: Boolean,
        private val includeNaturalPhrase: Boolean,
        private val includeConversationReply: Boolean,
        private val explanationLanguageTag: String,
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
                includePhraseAnalysis = includePhraseAnalysis,
                includeNaturalPhrase = includeNaturalPhrase,
                includeConversationReply = includeConversationReply,
                explanationLanguageTag = explanationLanguageTag,
            ) as T
        }
    }
}

internal data class ParsedTutorResponse(
    val visibleText: String,
    val spokenText: String?,
    val speechSegments: List<ConversationSpeechSegment>,
    val conversationText: String?,
)

private class NoUsableModelStageException(
    message: String,
    cause: Throwable?,
) : IllegalStateException(message, cause)

internal data class ParsedLanguageAnalysis(
    val text: String,
    val needsCorrection: Boolean?,
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
    pattern = """(?im)^[ \t]*(?:analysis|reply)[ \t]*:[ \t]*""",
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
        speechSegments = spokenText?.let {
            listOf(
                ConversationSpeechSegment(
                    text = it,
                    language = ConversationSpeechLanguage.Target,
                ),
            )
        }.orEmpty(),
        conversationText = spokenText,
    )
}

private const val MAX_GENERATION_ATTEMPTS = 3
private const val MIN_SHORT_STAGE_LENGTH = 2
private const val MIN_USABLE_STAGE_LENGTH = 12
private const val ANALYSIS_MAX_OUTPUT_TOKENS = 384
private const val NATURAL_PHRASE_MAX_OUTPUT_TOKENS = 64
private const val REPLY_MAX_OUTPUT_TOKENS = 192
private const val MAX_HISTORY_CHARS = 800
private const val MAX_CURRENT_MESSAGE_CHARS = 800

private val generationStageHeadingRegex = Regex(
    pattern = """(?im)^[ \t]*(?:analysis|language analysis|reply|answer|response)[ \t]*:[ \t]*""",
)

private fun String.cleanGenerationStage(): String =
    replace(generationStageHeadingRegex, "")
        .replace("**", "")
        .replace(Regex("(?m)^[ \\t]*(?:[-*]|\\d+[.)])[ \\t]+"), "")
        .trimForTutorOutput()

private val naturalPhraseLabelRegex = Regex(
    pattern = """(?im)^[ \t]*(?:natural(?: corrected)? phrase|correction|corrected)[ \t]*:[ \t]*""",
)

private fun String.cleanNaturalPhrase(): String =
    replace(naturalPhraseLabelRegex, "")
        .cleanGenerationStage()

private val analysisVerdictRegex = Regex(
    pattern = """(?i)^\s*(?:verdict|status)\s*:\s*(ok|correct|fix|needs[_ -]?correction)\b\s*[.—:;-]?\s*(.*)$""",
)

internal fun parseLanguageAnalysis(rawText: String): ParsedLanguageAnalysis {
    val lines = rawText.cleanGenerationStage().lines().toMutableList()
    val verdictIndex = lines.indexOfFirst { analysisVerdictRegex.matches(it) }
    if (verdictIndex < 0) {
        return ParsedLanguageAnalysis(
            text = lines.joinToString("\n").trimForTutorOutput(),
            needsCorrection = null,
        )
    }

    val match = analysisVerdictRegex.matchEntire(lines[verdictIndex])
        ?: return ParsedLanguageAnalysis(rawText.cleanGenerationStage(), null)
    val verdict = match.groupValues[1].lowercase().replace(Regex("[_ -]"), "")
    val remainder = match.groupValues[2].trim()
    lines.removeAt(verdictIndex)
    if (remainder.isNotEmpty()) {
        lines.add(verdictIndex, remainder)
    }
    return ParsedLanguageAnalysis(
        text = lines.joinToString("\n").trimForTutorOutput(),
        needsCorrection = verdict != "ok" && verdict != "correct",
    )
}

internal fun String.isMeaningfullyDifferentFrom(original: String): Boolean {
    fun String.forCorrectionComparison(): String =
        lowercase()
            .replace('’', '\'')
            .replace(Regex("[\\s,.!?;:…]+"), " ")
            .trim()

    return forCorrectionComparison() != original.forCorrectionComparison()
}

private fun String.isKnownBoilerplateResponse(): Boolean {
    val normalized = lowercase()
    return listOf(
        "would you like to start",
        "i'm here to help with pistols",
        "you are asking for help",
        "you're asking for help",
        "which is a good start",
        "how can i assist you today",
        "what can i help you with today",
        "how can i help you assess",
        "please provide some context",
        "do you have any specific questions or content",
    ).any(normalized::contains)
}

internal fun composeTutorResponse(
    analysis: String?,
    naturalPhrase: String?,
    reply: String?,
    naturalPhraseIntroduction: String,
): ParsedTutorResponse {
    val visibleSections = mutableListOf<String>()
    val speechSegments = mutableListOf<ConversationSpeechSegment>()

    analysis?.cleanGenerationStage()?.takeIf { it.isNotBlank() }?.let { text ->
        visibleSections += text
        speechSegments += ConversationSpeechSegment(
            text = text,
            language = ConversationSpeechLanguage.Explanation,
        )
    }
    naturalPhrase?.cleanNaturalPhrase()?.takeIf { it.isNotBlank() }?.let { text ->
        visibleSections += "$naturalPhraseIntroduction\n$text"
        speechSegments += ConversationSpeechSegment(
            text = naturalPhraseIntroduction,
            language = ConversationSpeechLanguage.Explanation,
        )
        speechSegments += ConversationSpeechSegment(
            text = text,
            language = ConversationSpeechLanguage.Target,
        )
    }
    reply?.cleanGenerationStage()?.takeIf { it.isNotBlank() }?.let { text ->
        visibleSections += text
        speechSegments += ConversationSpeechSegment(
            text = text,
            language = ConversationSpeechLanguage.Target,
        )
    }

    val fallback = "The local tutor did not return a readable answer."
    val visibleText = visibleSections.joinToString("\n\n").ifBlank { fallback }
    val finalSegments = speechSegments.ifEmpty {
        listOf(ConversationSpeechSegment(fallback, ConversationSpeechLanguage.Target))
    }
    return ParsedTutorResponse(
        visibleText = visibleText,
        spokenText = finalSegments.joinToString("\n\n") { it.text },
        speechSegments = finalSegments,
        conversationText = reply?.cleanGenerationStage()?.takeIf { it.isNotBlank() },
    )
}

internal fun naturalPhraseIntroduction(languageTag: String): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> "Такая фраза звучала бы естественнее:"
        "de" -> "Natürlicher würde dieser Satz so klingen:"
        "es" -> "Una forma más natural de decirlo sería:"
        "fr" -> "Une façon plus naturelle de le dire serait :"
        "it" -> "Un modo più naturale per dirlo sarebbe:"
        "kk" -> "Бұл сөйлем табиғи түрде былай айтылады:"
        "zh" -> "更自然的说法是："
        else -> "A more natural way to say this is:"
    }

private fun unavailableAnalysisText(languageTag: String): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> "Подробный разбор получить не удалось, но разговор можно продолжить."
        "de" -> "Die ausführliche Analyse war nicht verfügbar, aber wir können weiterreden."
        "es" -> "No se pudo obtener el análisis detallado, pero podemos continuar."
        "fr" -> "L’analyse détaillée n’est pas disponible, mais nous pouvons continuer."
        "it" -> "L’analisi dettagliata non è disponibile, ma possiamo continuare."
        "kk" -> "Толық талдау қолжетімсіз болды, бірақ әңгімені жалғастыра аламыз."
        "zh" -> "暂时无法获得详细分析，但我们可以继续对话。"
        else -> "The detailed analysis was unavailable, but we can continue."
    }

private fun analysisVerdictFallback(needsCorrection: Boolean?, languageTag: String): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> if (needsCorrection == false) {
            "Фраза звучит естественно и понятна."
        } else {
            "Фразу стоит немного исправить."
        }
        else -> if (needsCorrection == false) {
            "The phrase sounds natural and clear."
        } else {
            "This phrase would benefit from a small correction."
        }
    }

private fun fallbackConversationReply(languageTag: String): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> "Расскажите об этом немного подробнее. Что для вас здесь самое важное?"
        "de" -> "Erzähl mir bitte etwas mehr darüber. Was ist dir dabei am wichtigsten?"
        "es" -> "Cuéntame un poco más sobre eso. ¿Qué es lo más importante para ti?"
        "fr" -> "Parlez-m’en un peu plus. Qu’est-ce qui est le plus important pour vous ?"
        "it" -> "Raccontami qualcosa in più. Qual è la cosa più importante per te?"
        "kk" -> "Бұл туралы толығырақ айтып беріңізші. Сіз үшін ең маңыздысы не?"
        "zh" -> "请再多说一点。对你来说最重要的是什么？"
        else -> "Tell me a little more about that. What is most important to you here?"
    }

private fun String.trimForTutorOutput(): String =
    trim(' ', '\t', '\r', '\n', '"', '\'', '“', '”')

private fun languageName(languageTag: String): String =
    Locale.forLanguageTag(languageTag)
        .getDisplayLanguage(Locale.ENGLISH)
        .ifBlank { languageTag }

internal fun buildConversationHistory(
    messages: List<ConversationMessage>,
): String {
    var remainingCharacters = MAX_HISTORY_CHARS
    return messages
        .asReversed()
        .mapNotNull { message ->
            if (remainingCharacters <= 0) return@mapNotNull null

            val content = when (message.role) {
                ConversationRole.User -> message.text
                ConversationRole.Assistant -> message.conversationText ?: return@mapNotNull null
            }.replace('\n', ' ').trim()
            if (content.isEmpty()) return@mapNotNull null

            val role = if (message.role == ConversationRole.User) "Learner" else "Tutor"
            val line = "$role: $content"
            val retainedLine = line.take(remainingCharacters)
            remainingCharacters -= retainedLine.length
            retainedLine
        }
        .asReversed()
        .joinToString("\n")
}

private fun buildAnalysisInput(
    conversationHistory: String,
    userText: String,
): String {
    val historySection = conversationHistory.takeIf { it.isNotBlank() }?.let {
        "RECENT DIALOGUE — context only, do not analyze it:\n$it\n\n"
    }.orEmpty()
    return """
        ${historySection}CURRENT LEARNER PHRASE — analyze only this:
        ${userText.take(MAX_CURRENT_MESSAGE_CHARS)}
    """.trimIndent()
}

private fun buildNaturalPhraseInput(userText: String, analysis: String): String =
    """
        CURRENT LEARNER PHRASE:
        ${userText.take(MAX_CURRENT_MESSAGE_CHARS)}

        LANGUAGE ANALYSIS — use only as guidance:
        $analysis

        Rewrite this exact CURRENT LEARNER PHRASE:
        ${userText.take(MAX_CURRENT_MESSAGE_CHARS)}
    """.trimIndent()

private fun buildReplyInput(
    conversationHistory: String,
    userText: String,
    analysis: String,
): String {
    val historySection = conversationHistory.takeIf { it.isNotBlank() }?.let {
        "RECENT DIALOGUE — context only:\n$it\n\n"
    }.orEmpty()
    return """
        ${historySection}CURRENT LEARNER PHRASE:
        ${userText.take(MAX_CURRENT_MESSAGE_CHARS)}

        LANGUAGE ANALYSIS — do not repeat this in your reply:
        $analysis

        Reply now to this exact CURRENT LEARNER PHRASE:
        ${userText.take(MAX_CURRENT_MESSAGE_CHARS)}
    """.trimIndent()
}

private val wordRegex = Regex("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*")
private val rewriteStopWords = setOf(
    "a", "an", "am", "are", "can", "could", "do", "for", "i", "in", "is", "me",
    "my", "of", "one", "please", "the", "to", "you", "your", "yeah",
)
private val tutorReplyOpeners = listOf(
    "how can i help",
    "how can i assist",
    "please provide",
    "do you have any specific",
    "would you like to start",
)

private fun String.normalizedWords(): Set<String> =
    wordRegex.findAll(lowercase())
        .map { it.value }
        .filterNot { it in rewriteStopWords }
        .toSet()

internal fun String.isPlausibleRewriteOf(original: String): Boolean {
    val normalizedCandidate = lowercase().trim()
    if (tutorReplyOpeners.any(normalizedCandidate::contains)) return false

    val originalWords = original.normalizedWords()
    val candidateWords = normalizedWords()
    if (originalWords.size < 2) return candidateWords.size <= originalWords.size + 1
    return originalWords.intersect(candidateWords).isNotEmpty()
}

internal fun String.matchesExpectedLanguageScript(languageTag: String): Boolean {
    val expectedCharacters = when (languageTag.substringBefore('-').lowercase()) {
        "ru", "kk" -> count { it in '\u0400'..'\u052f' }
        "zh" -> count { it in '\u3400'..'\u9fff' }
        else -> count {
            it in 'A'..'Z' || it in 'a'..'z' || it in '\u00c0'..'\u024f'
        }
    }
    val allLetters = count { it.isLetter() }
    return allLetters == 0 || expectedCharacters * 2 >= allLetters
}

private fun String.isNearDuplicateOf(previous: String): Boolean {
    val normalizedCurrent = lowercase().replace(Regex("\\s+"), " ").trim()
    val normalizedPrevious = previous.lowercase().replace(Regex("\\s+"), " ").trim()
    if (normalizedCurrent == normalizedPrevious) return true

    val currentWords = normalizedCurrent.normalizedWords()
    val previousWords = normalizedPrevious.normalizedWords()
    val union = currentWords union previousWords
    if (union.isEmpty()) return false
    return (currentWords intersect previousWords).size.toDouble() / union.size >= 0.72
}
