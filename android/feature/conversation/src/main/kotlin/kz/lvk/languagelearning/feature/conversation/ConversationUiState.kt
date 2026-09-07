package kz.lvk.languagelearning.feature.conversation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kz.lvk.languagelearning.core.speech.SpeechSubmissionTiming

private val conversationTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

private fun conversationMessageTime(text: String, role: ConversationRole): String {
    val timestampEpochMillis = if (role == ConversationRole.User) {
        SpeechSubmissionTiming.consumeMatching(text)?.speechEndedAtEpochMillis
            ?: System.currentTimeMillis()
    } else {
        System.currentTimeMillis()
    }
    return conversationTimeFormatter.format(Instant.ofEpochMilli(timestampEpochMillis))
}

data class ConversationUiState(
    val isEngineReady: Boolean = false,
    val isGenerating: Boolean = false,
    val generationPhase: ConversationGenerationPhase? = null,
    val messages: List<ConversationMessage> = emptyList(),
    val errorMessage: String? = null,
)

data class ConversationMessage(
    val id: Long,
    val text: String,
    val role: ConversationRole,
    val timeLabel: String = conversationMessageTime(text, role),
    val spokenText: String? = null,
    val speechSegments: List<ConversationSpeechSegment> = emptyList(),
    val conversationText: String? = null,
)

data class ConversationSpeechSegment(
    val text: String,
    val language: ConversationSpeechLanguage,
)

enum class ConversationSpeechLanguage {
    Explanation,
    Target,
}

enum class ConversationRole {
    User,
    Assistant,
}

enum class ConversationGenerationPhase {
    Analyzing,
    Composing,
}
