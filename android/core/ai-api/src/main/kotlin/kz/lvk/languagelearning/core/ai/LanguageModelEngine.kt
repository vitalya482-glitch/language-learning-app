package kz.lvk.languagelearning.core.ai

/**
 * Platform-neutral contract for future local AI engines.
 * Android implementations may use JNI/NDK, Android system AI APIs, NNAPI/GPU,
 * while iOS can bind the same native C++ core to Swift.
 */
interface LanguageModelEngine {
    suspend fun load(model: LocalModelDescriptor)
    suspend fun generate(request: LanguageModelRequest): LanguageModelResponse
    suspend fun unload()
}

data class LocalModelDescriptor(
    val id: String,
    val displayName: String,
    val localPath: String,
)

data class LanguageModelRequest(
    val systemPrompt: String,
    val userText: String,
)

data class LanguageModelResponse(
    val text: String,
)
