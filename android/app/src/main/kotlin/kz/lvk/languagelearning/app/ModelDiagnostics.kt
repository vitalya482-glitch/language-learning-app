package kz.lvk.languagelearning.app

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kz.lvk.languagelearning.core.ai.LanguageModelEngine
import kz.lvk.languagelearning.core.ai.LanguageModelRequest
import kz.lvk.languagelearning.core.ai.LanguageModelResponse
import kz.lvk.languagelearning.core.ai.LocalModelDescriptor
import kz.lvk.languagelearning.core.speech.SpeechSubmissionTiming

data class ModelDiagnosticEvent(
    val id: Long,
    val timestampEpochMillis: Long,
    val source: String,
    val text: String,
)

class ModelDiagnostics {
    private val nextId = AtomicLong(0L)
    private val _events = MutableStateFlow<List<ModelDiagnosticEvent>>(emptyList())
    val events: StateFlow<List<ModelDiagnosticEvent>> = _events.asStateFlow()

    fun clear() {
        _events.value = emptyList()
    }

    fun record(source: String, text: String) {
        val cleanedText = text.trim()
        if (source == "USER") {
            val speechTiming = SpeechSubmissionTiming.peekMatching(cleanedText)
            if (speechTiming != null) {
                appendEvents(
                    listOf(
                        ModelDiagnosticEvent(
                            id = nextId.incrementAndGet(),
                            timestampEpochMillis = speechTiming.speechEndedAtEpochMillis,
                            source = "USER",
                            text = cleanedText,
                        ),
                        ModelDiagnosticEvent(
                            id = nextId.incrementAndGet(),
                            timestampEpochMillis = speechTiming.resultReceivedAtEpochMillis,
                            source = "STT RESULT",
                            text = "Android speech finalization: ${speechTiming.finalizationDurationMillis} ms",
                        ),
                    ),
                )
                return
            }
        }

        appendEvent(
            timestampEpochMillis = System.currentTimeMillis(),
            source = source,
            text = cleanedText,
        )
    }

    private fun appendEvent(
        timestampEpochMillis: Long,
        source: String,
        text: String,
    ) {
        appendEvents(
            listOf(
                ModelDiagnosticEvent(
                    id = nextId.incrementAndGet(),
                    timestampEpochMillis = timestampEpochMillis,
                    source = source,
                    text = text,
                ),
            ),
        )
    }

    private fun appendEvents(newEvents: List<ModelDiagnosticEvent>) {
        _events.update { current ->
            (current + newEvents).takeLast(MAX_EVENTS)
        }
    }

    private companion object {
        const val MAX_EVENTS = 400
    }
}

class LoggingLanguageModelEngine(
    private val delegate: LanguageModelEngine,
    private val diagnostics: ModelDiagnostics,
) : LanguageModelEngine {
    private val nextGenerationCallId = AtomicLong(0L)

    override suspend fun load(model: LocalModelDescriptor) {
        diagnostics.record(
            source = "ENGINE LOAD START",
            text = "${model.displayName}\n${model.localPath}",
        )
        try {
            delegate.load(model)
            diagnostics.record("ENGINE LOAD DONE", model.displayName)
        } catch (error: Throwable) {
            diagnostics.record(
                source = "ENGINE LOAD ERROR",
                text = error.message ?: error::class.java.simpleName,
            )
            throw error
        }
    }

    override suspend fun generate(request: LanguageModelRequest): LanguageModelResponse {
        val stage = detectStage(request.systemPrompt)
        val callId = nextGenerationCallId.incrementAndGet()
        val startedAtNanos = System.nanoTime()
        diagnostics.record(
            source = "$stage REQUEST #$callId",
            text = buildString {
                append("maxOutputTokens=")
                append(request.maxOutputTokens)
                append("\nthinkingEnabled=")
                append(request.thinkingEnabled)
                append("\n\n")
                append(request.userText)
            },
        )

        return try {
            delegate.generate(request).also { response ->
                val elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
                diagnostics.record(
                    source = "$stage RESPONSE #$callId · ${elapsedMs} ms",
                    text = response.text.ifBlank { "(empty response)" },
                )
            }
        } catch (error: Throwable) {
            val elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
            diagnostics.record(
                source = "$stage ERROR #$callId · ${elapsedMs} ms",
                text = error.message ?: error::class.java.simpleName,
            )
            throw error
        }
    }

    override suspend fun unload() {
        diagnostics.record("ENGINE UNLOAD START", "")
        try {
            delegate.unload()
            diagnostics.record("ENGINE UNLOAD DONE", "")
        } catch (error: Throwable) {
            diagnostics.record(
                source = "ENGINE UNLOAD ERROR",
                text = error.message ?: error::class.java.simpleName,
            )
            throw error
        }
    }

    private fun detectStage(systemPrompt: String): String = when {
        systemPrompt.contains("VERDICT:", ignoreCase = true) -> "MODEL ANALYSIS"
        systemPrompt.contains("Rewrite only", ignoreCase = true) -> "MODEL NATURAL PHRASE"
        systemPrompt.contains("conversation partner", ignoreCase = true) -> "MODEL REPLY"
        else -> "MODEL GENERATION"
    }
}
