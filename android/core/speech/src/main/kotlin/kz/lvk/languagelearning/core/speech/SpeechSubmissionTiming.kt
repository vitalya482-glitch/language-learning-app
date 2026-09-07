package kz.lvk.languagelearning.core.speech

/**
 * Keeps the timing of the most recent speech-recognition result long enough for the
 * conversation layer and diagnostics to attach the user's message to the moment speech ended,
 * rather than to the later moment when Android finally delivered the recognized text.
 */
data class SpeechSubmissionTimingSnapshot(
    val text: String,
    val speechEndedAtEpochMillis: Long,
    val resultReceivedAtEpochMillis: Long,
) {
    val finalizationDurationMillis: Long
        get() = (resultReceivedAtEpochMillis - speechEndedAtEpochMillis).coerceAtLeast(0L)
}

object SpeechSubmissionTiming {
    @Volatile
    private var latest: SpeechSubmissionTimingSnapshot? = null

    fun publish(
        text: String,
        speechEndedAtEpochMillis: Long,
        resultReceivedAtEpochMillis: Long,
    ) {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) return

        latest = SpeechSubmissionTimingSnapshot(
            text = normalizedText,
            speechEndedAtEpochMillis = speechEndedAtEpochMillis,
            resultReceivedAtEpochMillis = resultReceivedAtEpochMillis,
        )
    }

    fun peekMatching(
        text: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): SpeechSubmissionTimingSnapshot? {
        val snapshot = latest ?: return null
        if (snapshot.text != text.trim()) return null
        if (nowEpochMillis - snapshot.resultReceivedAtEpochMillis !in 0L..MAX_AGE_MS) return null
        return snapshot
    }

    fun consumeMatching(
        text: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): SpeechSubmissionTimingSnapshot? {
        val snapshot = peekMatching(text, nowEpochMillis) ?: return null
        latest = null
        return snapshot
    }

    fun clear() {
        latest = null
    }

    private const val MAX_AGE_MS = 60_000L
}
