package kz.lvk.languagelearning.feature.conversation

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
