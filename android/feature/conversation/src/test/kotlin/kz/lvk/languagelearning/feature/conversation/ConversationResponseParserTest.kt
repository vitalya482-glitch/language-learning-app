package kz.lvk.languagelearning.feature.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConversationResponseParserTest {
    @Test
    fun `parses compact tutor protocol`() {
        val result = parseTutorResponse(
            """
                SAY: I'm doing well, thank you. How are you?
                FEEDBACK: Фраза правильная.
            """.trimIndent(),
        )

        assertEquals("I'm doing well, thank you. How are you?", result.spokenText)
        assertEquals(
            "Фраза правильная.\n\nI'm doing well, thank you. How are you?",
            result.visibleText,
        )
    }

    @Test
    fun `does not expose a speak-only block`() {
        val result = parseTutorResponse("[[SPEAK]]What are you doing?[[/SPEAK]]")

        assertEquals("What are you doing?", result.spokenText)
        assertEquals("What are you doing?", result.visibleText)
        assertFalse(result.visibleText.contains("SPEAK"))
    }

    @Test
    fun `accepts malformed closing marker produced by a small model`() {
        val result = parseTutorResponse("[[SPEAK]]What are you doing?[[/SPEAK]\"]")

        assertEquals("What are you doing?", result.spokenText)
        assertEquals("What are you doing?", result.visibleText)
        assertFalse(result.visibleText.contains("SPEAK"))
    }

    @Test
    fun `keeps feedback outside a legacy speak block`() {
        val result = parseTutorResponse(
            "[[SPEAK]]Hello![[/SPEAK]]\nCorrected: Hello, my friend.",
        )

        assertEquals("Hello!", result.spokenText)
        assertEquals("Corrected: Hello, my friend.\n\nHello!", result.visibleText)
    }

    @Test
    fun `uses first line for speech without duplicating a natural response`() {
        val raw = "I'm doing well, thank you!\nФраза правильная."

        val result = parseTutorResponse(raw)

        assertEquals("I'm doing well, thank you!", result.spokenText)
        assertEquals(raw, result.visibleText)
    }
}
