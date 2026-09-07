package kz.lvk.languagelearning.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val learningLevel: String,
    includePhraseAnalysis: Boolean,
    private val includeNaturalPhrase: Boolean,
    includeConversationReply: Boolean,
    private val explanationLanguageTag: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var nextMessageId = 0L
    private var sessionJob: Job? = null
    private val includePhraseAnalysis = includePhraseAnalysis
    private val includeConversationReply = includeConversationReply ||
        (!includePhraseAnalysis && !includeNaturalPhrase)
    private val nativeLanguageName = languageName(nativeLanguageTag)
    private val targetLanguageName = languageName(targetLanguageTag)
    private val explanationLanguageName = languageName(explanationLanguageTag)

    private val teacherPacketSystemPrompt: String
        get() = buildString {
            appendLine("TUTOR_PACKET_V1")
            appendLine("You are a compact $targetLanguageName teacher for a $learningLevel learner.")
            appendLine("Do all analysis and drafting in English. Keep the task simple and literal.")
            appendLine("Analyze only CURRENT LEARNER PHRASE. RECENT DIALOGUE is context only.")
            appendLine("Return exactly four labeled fields, one field per line:")
            appendLine("STATUS: OK or FIX")
            if (includeNaturalPhrase) {
                appendLine("CORRECTION: one corrected natural $targetLanguageName phrase, or NONE")
            } else {
                appendLine("CORRECTION: NONE")
            }
            if (includePhraseAnalysis) {
                appendLine("WHY: a short English explanation of the important error, or CORRECT")
            } else {
                appendLine("WHY: NONE")
            }
            if (includeConversationReply) {
                appendLine("REPLY: a short $targetLanguageName reply that continues the conversation")
            } else {
                appendLine("REPLY: NONE")
            }
            appendLine("Use FIX only for a real grammar or word-choice problem.")
            appendLine("Ignore capitalization, punctuation, and harmless spoken brevity.")
            appendLine("Preserve the learner's exact intended meaning in CORRECTION.")
            appendLine("CORRECTION must contain only the corrected phrase itself, never an explanation.")
            appendLine("WHY must contain only the explanation and must never contain REPLY.")
            appendLine("REPLY must contain only the conversational reply, never correction metadata.")
            appendLine("Never translate CORRECTION or REPLY into ${nativeLanguageName}.")
            appendLine("WHY must stay in English; the app translates it later when needed.")
            appendLine("Do not use Markdown, lists, headings, extra labels, or hidden reasoning.")
            if (includeConversationReply) {
                appendLine("REPLY must not repeat the learner's phrase or an earlier tutor question.")
                if (learningLevel.equals("A1", ignoreCase = true)) {
                    appendLine("For A1, REPLY must be two short sentences: a reaction, then exactly one question.")
                } else {
                    appendLine("Ask at most one specific question in REPLY.")
                }
            }
        }.trim()

    private val replyOnlySystemPrompt: String
        get() = buildString {
            appendLine("REPLY_ONLY_V1")
            appendLine("You are a friendly $targetLanguageName conversation partner.")
            appendLine("Reply only to CURRENT LEARNER PHRASE. RECENT DIALOGUE is context only.")
            appendLine("Use simple $targetLanguageName suitable for level $learningLevel.")
            appendLine("Do not repeat the learner's sentence or an earlier tutor question.")
            appendLine("Do not ask how you can help and do not discuss being an AI.")
            if (learningLevel.equals("A1", ignoreCase = true)) {
                appendLine("Return exactly two short sentences: a direct reaction or answer, then one specific question.")
            } else {
                appendLine("Return 1-3 short sentences and ask at most one specific question.")
            }
            appendLine("Return only the reply. No labels, Markdown, or hidden reasoning.")
        }.trim()

    private val translationSystemPrompt: String
        get() = """
            TRANSLATE_EXPLANATION_V1
            Translate only the supplied English grammar explanation into $explanationLanguageName
            ($explanationLanguageTag). Preserve quoted $targetLanguageName words exactly as written.
            Keep it to at most two short sentences. Return translation only. Do not add examples,
            labels, Markdown, or commentary.
        """.trimIndent()

    fun loadEngine() {
        if (_state.value.isEngineReady || sessionJob?.isActive == true) return
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

        sessionJob = viewModelScope.launch {
            try {
                engine.load(installedModel)
                _state.update { it.copy(isEngineReady = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update {
                    it.copy(errorMessage = error.message ?: "Unable to load local AI model")
                }
            }
        }
    }

    fun closeSession() {
        sessionJob?.cancel()
        sessionJob = null
        _state.update {
            it.copy(
                isEngineReady = false,
                isGenerating = false,
                generationPhase = null,
            )
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

        sessionJob = viewModelScope.launch {
            try {
                val history = buildConversationHistory(currentState.messages)
                val previousReplies = currentState.messages
                    .asSequence()
                    .filter { it.role == ConversationRole.Assistant }
                    .mapNotNull { it.conversationText }
                    .toList()
                val clearlyCorrectA1 =
                    learningLevel.equals("A1", ignoreCase = true) &&
                        userText.isClearlyCorrectA1Phrase(targetLanguageTag)

                val response = when {
                    clearlyCorrectA1 -> buildKnownCorrectA1Response(
                        history = history,
                        userText = userText,
                        previousReplies = previousReplies,
                    )
                    includePhraseAnalysis || includeNaturalPhrase -> buildTeacherPacketResponse(
                        history = history,
                        userText = userText,
                        previousReplies = previousReplies,
                    )
                    else -> buildReplyOnlyResponse(
                        history = history,
                        userText = userText,
                        previousReplies = previousReplies,
                    )
                }

                val assistantMessage = ConversationMessage(
                    id = nextMessageId++,
                    text = response.visibleText,
                    role = ConversationRole.Assistant,
                    spokenText = response.spokenText,
                    speechSegments = response.speechSegments,
                    conversationText = response.conversationText,
                )
                _state.update {
                    it.copy(
                        messages = it.messages + assistantMessage,
                        isGenerating = false,
                        generationPhase = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
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

    private suspend fun buildKnownCorrectA1Response(
        history: String,
        userText: String,
        previousReplies: List<String>,
    ): ParsedTutorResponse {
        _state.update { it.copy(generationPhase = ConversationGenerationPhase.Composing) }
        val analysis = if (includePhraseAnalysis) {
            correctA1Feedback(
                languageTag = explanationLanguageTag,
                isQuestion = userText.looksLikeQuestion(targetLanguageTag),
            )
        } else {
            null
        }
        val reply = if (includeConversationReply) {
            generateReplyOnly(history, userText)
                ?.sanitizeReply(userText, targetLanguageTag, learningLevel, previousReplies)
                ?: fallbackConversationReply(
                    userText = userText,
                    languageTag = targetLanguageTag,
                    learningLevel = learningLevel,
                    previousReplies = previousReplies,
                )
        } else {
            null
        }
        return composeTutorResponse(
            analysis = analysis,
            naturalPhrase = null,
            reply = reply,
            naturalPhraseIntroduction = naturalPhraseIntroduction(explanationLanguageTag),
        )
    }

    private suspend fun buildReplyOnlyResponse(
        history: String,
        userText: String,
        previousReplies: List<String>,
    ): ParsedTutorResponse {
        _state.update { it.copy(generationPhase = ConversationGenerationPhase.Composing) }
        val reply = generateReplyOnly(history, userText)
            ?.sanitizeReply(userText, targetLanguageTag, learningLevel, previousReplies)
            ?: fallbackConversationReply(
                userText = userText,
                languageTag = targetLanguageTag,
                learningLevel = learningLevel,
                previousReplies = previousReplies,
            )
        return composeTutorResponse(
            analysis = null,
            naturalPhrase = null,
            reply = reply,
            naturalPhraseIntroduction = naturalPhraseIntroduction(explanationLanguageTag),
        )
    }

    private suspend fun buildTeacherPacketResponse(
        history: String,
        userText: String,
        previousReplies: List<String>,
    ): ParsedTutorResponse {
        val rawPacket = generateRawWithEmptyRetry(
            LanguageModelRequest(
                systemPrompt = teacherPacketSystemPrompt,
                userText = buildTeacherPacketInput(history, userText),
                thinkingEnabled = false,
                maxOutputTokens = TEACHER_PACKET_MAX_OUTPUT_TOKENS,
            ),
        )
        val packet = rawPacket?.let(::parseTeacherPacket) ?: ParsedTeacherPacket()
        val validatedCorrection = packet.correction
            ?.extractCorrectionPhrase()
            ?.sanitizeCorrection(userText, targetLanguageTag)
        val correction = validatedCorrection?.takeIf { includeNaturalPhrase }
        val needsCorrection = resolveCorrectionState(
            modelNeedsCorrection = packet.needsCorrection,
            validatedCorrection = validatedCorrection,
            correctionExpected = includeNaturalPhrase,
        )

        _state.update { it.copy(generationPhase = ConversationGenerationPhase.Composing) }

        val analysis = if (includePhraseAnalysis) {
            when (needsCorrection) {
                false -> correctFeedback(explanationLanguageTag)
                true -> {
                    val englishWhy = packet.why
                        ?.cleanGenerationStage()
                        ?.takeIf { it.isNotBlank() && !it.equals("CORRECT", ignoreCase = true) }
                    englishWhy?.let { translateExplanationIfNeeded(it) }
                        ?: correctionFallback(explanationLanguageTag)
                }
                null -> inconclusiveFeedback(explanationLanguageTag)
            }
        } else {
            null
        }

        val reply = if (includeConversationReply) {
            packet.reply
                ?.sanitizeReply(userText, targetLanguageTag, learningLevel, previousReplies)
                ?: fallbackConversationReply(
                    userText = userText,
                    languageTag = targetLanguageTag,
                    learningLevel = learningLevel,
                    previousReplies = previousReplies + listOfNotNull(correction),
                )
        } else {
            null
        }

        return composeTutorResponse(
            analysis = analysis,
            naturalPhrase = correction,
            reply = reply,
            naturalPhraseIntroduction = naturalPhraseIntroduction(explanationLanguageTag),
        )
    }

    private suspend fun generateReplyOnly(history: String, userText: String): String? =
        generateRawWithEmptyRetry(
            LanguageModelRequest(
                systemPrompt = replyOnlySystemPrompt,
                userText = buildReplyInput(history, userText),
                thinkingEnabled = false,
                maxOutputTokens = REPLY_ONLY_MAX_OUTPUT_TOKENS,
            ),
        )

    private suspend fun translateExplanationIfNeeded(englishText: String): String {
        if (explanationLanguageTag.substringBefore('-').equals("en", ignoreCase = true)) {
            return englishText
        }
        if (englishText.matchesExpectedLanguageScript(explanationLanguageTag)) {
            return englishText
        }
        val translated = generateRawWithEmptyRetry(
            LanguageModelRequest(
                systemPrompt = translationSystemPrompt,
                userText = englishText.take(MAX_TRANSLATION_INPUT_CHARS),
                thinkingEnabled = false,
                maxOutputTokens = TRANSLATION_MAX_OUTPUT_TOKENS,
            ),
        )
            ?.cleanGenerationStage()
            ?.takeIf { it.matchesExpectedLanguageScript(explanationLanguageTag) }
        return translated ?: translationFailureFeedback(explanationLanguageTag)
    }

    private suspend fun generateRawWithEmptyRetry(request: LanguageModelRequest): String? {
        repeat(MAX_EMPTY_RESPONSE_ATTEMPTS) {
            try {
                val text = engine.generate(request).text.prepareRawModelResponse()
                if (text.isNotBlank()) return text
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val emptyResponse = error.message
                    ?.contains("empty response", ignoreCase = true) == true
                if (!emptyResponse) throw error
            }
        }
        return null
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

internal fun resolveCorrectionState(
    modelNeedsCorrection: Boolean?,
    validatedCorrection: String?,
    correctionExpected: Boolean,
): Boolean? = when {
    validatedCorrection != null -> true
    correctionExpected && modelNeedsCorrection == true -> null
    else -> modelNeedsCorrection
}

internal data class ParsedTutorResponse(
    val visibleText: String,
    val spokenText: String?,
    val speechSegments: List<ConversationSpeechSegment>,
    val conversationText: String?,
)

internal data class ParsedTeacherPacket(
    val needsCorrection: Boolean? = null,
    val correction: String? = null,
    val why: String? = null,
    val reply: String? = null,
)

private enum class TeacherPacketField {
    Status,
    Correction,
    Why,
    Reply,
}

private val teacherPacketLabelRegex = Regex(
    pattern = """(?i)^\s*(?:\*\*)?(STATUS|VERDICT|CORRECTION|CORRECTED|WHY|EXPLANATION|REPLY|ANSWER)(?:\*\*)?\s*:\s*(.*)$""",
)

internal fun parseTeacherPacket(rawText: String): ParsedTeacherPacket {
    val values = mutableMapOf<TeacherPacketField, MutableList<String>>()
    var currentField: TeacherPacketField? = null

    rawText.lines().forEach { rawLine ->
        val line = rawLine.replace("**", "").replace("`", "").trim()
        if (line.isBlank()) return@forEach
        val match = teacherPacketLabelRegex.matchEntire(line)
        if (match != null) {
            currentField = when (match.groupValues[1].uppercase()) {
                "STATUS", "VERDICT" -> TeacherPacketField.Status
                "CORRECTION", "CORRECTED" -> TeacherPacketField.Correction
                "WHY", "EXPLANATION" -> TeacherPacketField.Why
                "REPLY", "ANSWER" -> TeacherPacketField.Reply
                else -> null
            }
            currentField?.let { field ->
                val value = match.groupValues[2].trim()
                if (value.isNotEmpty()) {
                    values.getOrPut(field) { mutableListOf() } += value
                }
            }
        } else {
            currentField?.let { field ->
                values.getOrPut(field) { mutableListOf() } += line
            }
        }
    }

    fun value(field: TeacherPacketField): String? = values[field]
        ?.joinToString(" ")
        ?.cleanGenerationStage()
        ?.takeUnless { it.isNoneValue() }

    val statusText = value(TeacherPacketField.Status)?.uppercase().orEmpty()
    val correction = value(TeacherPacketField.Correction)
    val needsCorrection = when {
        "FIX" in statusText || "NEEDS" in statusText -> true
        "OK" in statusText || "CORRECT" in statusText -> false
        correction != null -> true
        else -> null
    }

    return ParsedTeacherPacket(
        needsCorrection = needsCorrection,
        correction = correction,
        why = value(TeacherPacketField.Why),
        reply = value(TeacherPacketField.Reply),
    )
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

internal fun buildConversationHistory(messages: List<ConversationMessage>): String {
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

private fun buildTeacherPacketInput(history: String, userText: String): String {
    val historySection = history.takeIf { it.isNotBlank() }?.let {
        "RECENT DIALOGUE — context only:\n$it\n\n"
    }.orEmpty()
    return """
        ${historySection}CURRENT LEARNER PHRASE:
        ${userText.take(MAX_CURRENT_MESSAGE_CHARS)}
    """.trimIndent()
}

internal fun buildReplyInput(history: String, userText: String): String {
    val historySection = history.takeIf { it.isNotBlank() }?.let {
        "RECENT DIALOGUE — context only:\n$it\n\n"
    }.orEmpty()
    return """
        ${historySection}CURRENT LEARNER PHRASE:
        ${userText.take(MAX_CURRENT_MESSAGE_CHARS)}
    """.trimIndent()
}

internal fun String.prepareRawModelResponse(): String = trim()

private val quotedTextRegex = Regex("""[\"“]([^\"”]+)[\"”]""")

internal fun String.extractCorrectionPhrase(): String {
    val raw = replace("**", "").replace("`", "").trim()
    val looksLikeExplanation = listOf(
        " is incorrect",
        "correct phrase",
        "corrected phrase",
        "should be",
        "say ",
    ).any { marker -> raw.contains(marker, ignoreCase = true) }
    if (looksLikeExplanation) {
        val quotedParts = quotedTextRegex.findAll(raw)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        quotedParts.lastOrNull()?.let { return it }
    }
    return raw.cleanGenerationStage()
}

private fun String.sanitizeCorrection(original: String, languageTag: String): String? {
    val cleaned = cleanNaturalPhrase()
    if (cleaned.isBlank() || cleaned.isNoneValue()) return null
    if (!cleaned.matchesExpectedLanguageScript(languageTag)) return null
    if (!cleaned.isMeaningfullyDifferentFrom(original)) return null
    if (cleaned.isKnownBoilerplateResponse()) return null
    return cleaned
}

private fun String.sanitizeReply(
    learnerText: String,
    languageTag: String,
    learningLevel: String,
    previousReplies: List<String>,
): String? {
    val cleaned = cleanGenerationStage()
    if (cleaned.length < MIN_USABLE_REPLY_LENGTH) return null
    if (!cleaned.matchesExpectedLanguageScript(languageTag)) return null
    if (cleaned.isKnownBoilerplateResponse()) return null
    if (cleaned.echoesLearnerPhrase(learnerText)) return null
    if (!cleaned.hasExpectedQuestionCount(learningLevel)) return null
    if (previousReplies.any { previous ->
            cleaned.isNearDuplicateOf(previous) || cleaned.repeatsQuestionFrom(previous)
        }
    ) return null
    return cleaned
}

private fun String.isNoneValue(): Boolean {
    val normalized = trim().uppercase().replace(".", "")
    return normalized in setOf("NONE", "N/A", "NA", "NULL", "-", "NO CORRECTION")
}

private val generationStageHeadingRegex = Regex(
    pattern = """(?im)^[ \t]*(?:analysis|language analysis|reply|answer|response)[ \t]*:[ \t]*""",
)

private fun String.cleanGenerationStage(): String =
    replace("**", "")
        .replace("`", "")
        .replace(generationStageHeadingRegex, "")
        .replace(Regex("(?m)^[ \\t]*(?:[-*]|\\d+[.)])[ \\t]+"), "")
        .trimForTutorOutput()

private val naturalPhraseLabelRegex = Regex(
    pattern = """(?im)^[ \t]*(?:natural(?: corrected)? phrase|correction|corrected)[ \t]*:[ \t]*""",
)

private fun String.cleanNaturalPhrase(): String =
    replace(naturalPhraseLabelRegex, "")
        .cleanGenerationStage()

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
        "how can i assist you today",
        "what can i help you with today",
        "what can i help with for you",
        "how can i help you with that",
        "i'm just a small ai",
        "i am just a small ai",
        "okay, i'm learning english",
        "i'm learning english. let's see what you're doing",
        "please provide some context",
        "do you have any questions about your english learning",
    ).any(normalized::contains)
}

private fun String.normalizedForComparison(): String =
    lowercase()
        .replace('’', '\'')
        .replace(Regex("[^\\p{L}\\p{N}']+"), " ")
        .trim()

private val wordRegex = Regex("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*")

private fun String.normalizedWords(): Set<String> =
    wordRegex.findAll(lowercase())
        .map { it.value }
        .filterNot { it in COMPARISON_STOP_WORDS }
        .toSet()

internal fun String.echoesLearnerPhrase(learnerText: String): Boolean {
    val candidateNormalized = normalizedForComparison()
    val learnerNormalized = learnerText.normalizedForComparison()
    if (learnerNormalized.length >= 3 && candidateNormalized.contains(learnerNormalized)) return true

    val learnerWords = learnerText.normalizedWords()
    if (learnerWords.isEmpty()) return false
    return Regex("[^.!?]+[.!?]?")
        .findAll(this)
        .map { it.value.trim() }
        .filter { it.isNotEmpty() }
        .any { sentence ->
            val sentenceWords = sentence.normalizedWords()
            val overlap = (sentenceWords intersect learnerWords).size.toDouble() / learnerWords.size
            overlap >= if (learnerWords.size <= 2) 1.0 else 0.7
        }
}

private fun String.isNearDuplicateOf(previous: String): Boolean {
    val currentWords = normalizedWords()
    val previousWords = previous.normalizedWords()
    val union = currentWords union previousWords
    if (union.isEmpty()) return normalizedForComparison() == previous.normalizedForComparison()
    return (currentWords intersect previousWords).size.toDouble() / union.size >= 0.72
}

private fun String.questionSegments(): List<String> =
    Regex("[^.!?]*\\?")
        .findAll(this)
        .map { it.value.normalizedForComparison() }
        .filter { it.isNotBlank() }
        .toList()

internal fun String.repeatsQuestionFrom(previous: String): Boolean {
    val previousQuestions = previous.questionSegments()
    if (previousQuestions.isEmpty()) return false
    return questionSegments().any { currentQuestion ->
        previousQuestions.any { previousQuestion ->
            currentQuestion == previousQuestion || currentQuestion.isNearDuplicateOf(previousQuestion)
        }
    }
}

internal fun String.hasExpectedQuestionCount(learningLevel: String): Boolean {
    val questionCount = count { it == '?' }
    return if (learningLevel.equals("A1", ignoreCase = true)) {
        questionCount == 1
    } else {
        questionCount <= 1
    }
}

private fun String.normalizedA1Phrase(): String = normalizedForComparison()

internal fun String.isClearlyCorrectA1Phrase(languageTag: String): Boolean {
    if (languageTag.substringBefore('-').lowercase() != "en") return false
    return normalizedA1Phrase() in setOf(
        "hello",
        "hi",
        "hey",
        "good morning",
        "good afternoon",
        "good evening",
        "how are you",
        "hello how are you",
        "hi how are you",
        "hello where are you",
        "what is your name",
        "what's your name",
        "where are you",
        "where do you live",
        "how old are you",
        "thank you",
        "thanks",
        "you're welcome",
        "nice to meet you",
    )
}

internal fun String.looksLikeQuestion(languageTag: String): Boolean {
    if (trimEnd().endsWith('?')) return true
    val words = normalizedA1Phrase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return false
    val questionOpeners = when (languageTag.substringBefore('-').lowercase()) {
        "en" -> setOf(
            "am", "are", "can", "could", "did", "do", "does", "have", "has", "how",
            "is", "may", "should", "was", "were", "what", "when", "where", "which",
            "who", "why", "will", "would",
        )
        else -> emptySet()
    }
    val questionAfterGreeting = words.first() in setOf("hello", "hi", "hey") &&
        words.getOrNull(1)?.let { it in questionOpeners } == true
    return words.first() in questionOpeners || questionAfterGreeting
}

internal fun fallbackConversationReply(
    userText: String,
    languageTag: String,
    learningLevel: String,
    previousReplies: List<String>,
): String {
    val candidates = when (languageTag.substringBefore('-').lowercase()) {
        "en" -> contextualEnglishFallbacks(userText)
        "ru" -> listOf(
            "Спасибо, что рассказал. Что произошло дальше?",
            "Это интересно. Какая часть была для тебя самой важной?",
        )
        "de" -> listOf(
            "Das klingt interessant. Was ist danach passiert?",
            "Danke fürs Erzählen. Was war für dich am wichtigsten?",
        )
        "es" -> listOf(
            "Parece interesante. ¿Qué pasó después?",
            "Gracias por contármelo. ¿Qué fue lo más importante para ti?",
        )
        "fr" -> listOf(
            "Cela semble intéressant. Qu’est-ce qui s’est passé ensuite ?",
            "Merci de me l’avoir raconté. Qu’est-ce qui était le plus important ?",
        )
        "it" -> listOf(
            "Sembra interessante. Che cosa è successo dopo?",
            "Grazie per avermelo raccontato. Che cosa era più importante per te?",
        )
        "kk" -> listOf(
            "Бұл қызық екен. Одан кейін не болды?",
            "Айтып бергеніңізге рақмет. Сіз үшін не маңызды болды?",
        )
        "zh" -> listOf(
            "这听起来很有意思。后来发生了什么？",
            "谢谢你告诉我这些。哪一部分对你最重要？",
        )
        else -> listOf("That sounds interesting. What happened next?")
    }
    return candidates.firstOrNull { candidate ->
        candidate.matchesExpectedLanguageScript(languageTag) &&
            !candidate.echoesLearnerPhrase(userText) &&
            candidate.hasExpectedQuestionCount(learningLevel) &&
            previousReplies.none { previous ->
                candidate.isNearDuplicateOf(previous) || candidate.repeatsQuestionFrom(previous)
            }
    } ?: candidates.first()
}

private fun contextualEnglishFallbacks(userText: String): List<String> {
    val normalized = userText.normalizedA1Phrase()
    val contextual = when {
        "how are you" in normalized ->
            listOf("I'm doing well, thank you. What are you doing today?")
        "how is your day" in normalized || "how's your day" in normalized ->
            listOf("My day is going well, thank you. What was the best part of your day?")
        ("mountain" in normalized || "mountings" in normalized) &&
            ("went" in normalized || "walk" in normalized) ->
            listOf("That sounds like an active day! Which place did you like most?")
        "english level" in normalized ->
            listOf("Of course, let's check it together. What do you usually do at work?")
        "work" in normalized || "job" in normalized || "manager" in normalized ->
            listOf("Your job sounds interesting. What task do you do most often?")
        else -> emptyList()
    }
    return contextual + listOf(
        "That sounds interesting. What happened next?",
        "Thanks for telling me. What did you enjoy most?",
        "I see what you mean. How did you feel about it?",
    )
}

internal fun correctA1Feedback(languageTag: String, isQuestion: Boolean): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> if (isQuestion) {
            "Грамматика верна, ты правильно задал вопрос.\nДавай продолжим диалог:"
        } else {
            "Грамматика верна, ты правильно построил фразу.\nДавай продолжим диалог:"
        }
        "de" -> "Die Grammatik stimmt. Setzen wir den Dialog fort:"
        "es" -> "La gramática es correcta. Continuemos el diálogo:"
        "fr" -> "La grammaire est correcte. Continuons le dialogue :"
        "it" -> "La grammatica è corretta. Continuiamo il dialogo:"
        "kk" -> "Грамматика дұрыс. Диалогті жалғастырайық:"
        "zh" -> "语法正确。让我们继续对话："
        else -> "The grammar is correct. Let's continue the conversation:"
    }

private fun correctFeedback(languageTag: String): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> "Фраза звучит правильно и естественно."
        "de" -> "Der Satz klingt richtig und natürlich."
        "es" -> "La frase suena correcta y natural."
        "fr" -> "La phrase est correcte et naturelle."
        "it" -> "La frase suona corretta e naturale."
        "kk" -> "Сөйлем дұрыс әрі табиғи естіледі."
        "zh" -> "这句话正确而且自然。"
        else -> "The phrase sounds correct and natural."
    }

internal fun translationFailureFeedback(languageTag: String): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> "Фразу стоит исправить, но подробное объяснение перевести не удалось."
        "de" -> "Der Satz sollte korrigiert werden, aber die Erklärung konnte nicht übersetzt werden."
        "es" -> "La frase necesita corrección, pero no se pudo traducir la explicación."
        "fr" -> "La phrase doit être corrigée, mais l’explication n’a pas pu être traduite."
        "it" -> "La frase va corretta, ma non è stato possibile tradurre la spiegazione."
        "kk" -> "Сөйлемді түзету керек, бірақ түсіндірмені аудару мүмкін болмады."
        "zh" -> "这句话需要修改，但无法翻译详细说明。"
        else -> "This phrase needs correction, but the explanation could not be translated."
    }

private fun correctionFallback(languageTag: String): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> "Фразу стоит немного исправить."
        "de" -> "Der Satz sollte etwas korrigiert werden."
        "es" -> "Conviene corregir un poco la frase."
        "fr" -> "La phrase mérite une petite correction."
        "it" -> "La frase va corretta leggermente."
        "kk" -> "Сөйлемді аздап түзету керек."
        "zh" -> "这句话需要稍微修改一下。"
        else -> "This phrase needs a small correction."
    }

private fun inconclusiveFeedback(languageTag: String): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> "Не удалось уверенно завершить разбор, но диалог можно продолжить."
        else -> "The analysis was inconclusive, but we can continue the conversation."
    }

internal fun naturalPhraseIntroduction(languageTag: String): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> "Так будет естественнее:"
        "de" -> "Natürlicher klingt es so:"
        "es" -> "Suena más natural así:"
        "fr" -> "Cela sonne plus naturel ainsi :"
        "it" -> "Suona più naturale così:"
        "kk" -> "Табиғи нұсқасы:"
        "zh" -> "更自然的说法是："
        else -> "A more natural version is:"
    }

internal fun String.matchesExpectedLanguageScript(languageTag: String): Boolean {
    val letters = filter(Char::isLetter)
    if (letters.isEmpty()) return false
    val base = languageTag.substringBefore('-').lowercase()
    return when (base) {
        "ru", "kk" -> letters.count { it in '\u0400'..'\u052F' } >= (letters.length * 0.45)
        "zh" -> letters.any { it in '\u4E00'..'\u9FFF' }
        else -> letters.count { it.code < 0x0250 } >= (letters.length * 0.55)
    }
}

private fun String.trimForTutorOutput(): String =
    trim(' ', '\t', '\r', '\n', '"', '\'', '“', '”')

private fun languageName(languageTag: String): String =
    Locale.forLanguageTag(languageTag)
        .getDisplayLanguage(Locale.ENGLISH)
        .ifBlank { languageTag }

private const val MAX_EMPTY_RESPONSE_ATTEMPTS = 2
private const val TEACHER_PACKET_MAX_OUTPUT_TOKENS = 160
private const val TRANSLATION_MAX_OUTPUT_TOKENS = 96
private const val REPLY_ONLY_MAX_OUTPUT_TOKENS = 96
private const val MAX_HISTORY_CHARS = 700
private const val MAX_CURRENT_MESSAGE_CHARS = 800
private const val MAX_TRANSLATION_INPUT_CHARS = 500
private const val MIN_USABLE_REPLY_LENGTH = 4

private val COMPARISON_STOP_WORDS = setOf(
    "a", "an", "am", "are", "can", "could", "do", "for", "i", "in", "is", "me",
    "my", "of", "one", "please", "the", "to", "you", "your", "yeah",
)
