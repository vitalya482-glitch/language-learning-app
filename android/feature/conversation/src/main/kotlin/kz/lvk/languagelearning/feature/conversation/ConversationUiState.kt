package kz.lvk.languagelearning.feature.conversation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val conversationTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

private fun conversationMessageTime(): String =
    conversationTimeFormatter.format(Instant.ofEpochMilli(System.currentTimeMillis()))

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
    val timeLabel: String = conversationMessageTime(),
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
