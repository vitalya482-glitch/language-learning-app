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
) : ViewModel() {
    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var nextMessageId = 0L

    init {
        loadEngine()
    }

    fun loadEngine() {
        _state.update { it.copy(isEngineReady = false, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                engine.load(
                    LocalModelDescriptor(
                        id = "native-smoke-test",
                        displayName = "Native smoke test",
                        localPath = "native://smoke-test",
                    ),
                )
            }.onSuccess {
                _state.update { it.copy(isEngineReady = true) }
            }.onFailure { error ->
                _state.update {
                    it.copy(errorMessage = error.message ?: "Unable to load native engine")
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
                        systemPrompt = "You are a concise language tutor.",
                        userText = userText,
                    ),
                )
            }.onSuccess { response ->
                val assistantMessage = ConversationMessage(
                    id = nextMessageId++,
                    text = response.text,
                    role = ConversationRole.Assistant,
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
                        errorMessage = error.message ?: "Native generation failed",
                    )
                }
            }
        }
    }

    class Factory(
        private val engine: LanguageModelEngine,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
                "Unsupported ViewModel class: ${modelClass.name}"
            }
            return ConversationViewModel(engine) as T
        }
    }
}
