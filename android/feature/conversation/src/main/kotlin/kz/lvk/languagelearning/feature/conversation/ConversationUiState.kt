package kz.lvk.languagelearning.feature.conversation

data class ConversationUiState(
    val isEngineReady: Boolean = false,
    val isGenerating: Boolean = false,
    val messages: List<ConversationMessage> = emptyList(),
    val errorMessage: String? = null,
)

data class ConversationMessage(
    val id: Long,
    val text: String,
    val role: ConversationRole,
)

enum class ConversationRole {
    User,
    Assistant,
}
