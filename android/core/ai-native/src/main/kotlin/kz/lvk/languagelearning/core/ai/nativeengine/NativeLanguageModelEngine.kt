package kz.lvk.languagelearning.core.ai.nativeengine

import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.lvk.languagelearning.core.ai.LanguageModelEngine
import kz.lvk.languagelearning.core.ai.LanguageModelRequest
import kz.lvk.languagelearning.core.ai.LanguageModelResponse
import kz.lvk.languagelearning.core.ai.LocalModelDescriptor

class NativeLanguageModelEngine : LanguageModelEngine, Closeable {
    private val lock = Any()
    private var nativeHandle: Long = nativeCreate()

    override suspend fun load(model: LocalModelDescriptor) = withContext(Dispatchers.Default) {
        synchronized(lock) {
            nativeLoad(
                handle = requireHandle(),
                id = model.id,
                displayName = model.displayName,
                localPath = model.localPath,
            )
        }
    }

    override suspend fun generate(request: LanguageModelRequest): LanguageModelResponse =
        withContext(Dispatchers.Default) {
            synchronized(lock) {
                LanguageModelResponse(
                    text = nativeGenerate(
                        handle = requireHandle(),
                        systemPrompt = request.systemPrompt,
                        userText = request.userText,
                        thinkingEnabled = request.thinkingEnabled,
                        maxOutputTokens = request.maxOutputTokens,
                    ),
                )
            }
        }

    override suspend fun unload() = withContext(Dispatchers.Default) {
        synchronized(lock) {
            nativeUnload(requireHandle())
        }
    }

    override fun close() {
        synchronized(lock) {
            if (nativeHandle != 0L) {
                nativeDestroy(nativeHandle)
                nativeHandle = 0L
            }
        }
    }

    private fun requireHandle(): Long = nativeHandle.takeIf { it != 0L }
        ?: error("Native language model engine is closed")

    private external fun nativeCreate(): Long
    private external fun nativeLoad(
        handle: Long,
        id: String,
        displayName: String,
        localPath: String,
    )
    private external fun nativeGenerate(
        handle: Long,
        systemPrompt: String,
        userText: String,
        thinkingEnabled: Boolean,
        maxOutputTokens: Int,
    ): String
    private external fun nativeUnload(handle: Long)
    private external fun nativeDestroy(handle: Long)

    private companion object {
        init {
            System.loadLibrary("language_learning_ai_jni")
        }
    }
}
