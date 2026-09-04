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

        assertEquals(
            "I'm doing well, thank you. How are you?\nФраза правильная.",
            result.visibleText,
        )
        assertEquals(result.visibleText, result.spokenText)
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

        assertEquals("Hello!\nCorrected: Hello, my friend.", result.visibleText)
        assertEquals(result.visibleText, result.spokenText)
    }

    @Test
    fun `speaks the complete natural response`() {
        val raw = "I'm doing well, thank you!\nФраза правильная."

        val result = parseTutorResponse(raw)

        assertEquals(raw, result.visibleText)
        assertEquals(raw, result.spokenText)
    }

    @Test
    fun `composes and speaks both analysis and reply`() {
        val result = composeTutorResponse(
            analysis = "A natural version is: Do you know the band Guns N' Roses?",
            reply = "Yes. They are an American rock band. What is your favorite song?",
        )

        assertEquals(
            "Analysis: A natural version is: Do you know the band Guns N' Roses?\n\n" +
                "Reply: Yes. They are an American rock band. What is your favorite song?",
            result.visibleText,
        )
        assertEquals(
            "A natural version is: Do you know the band Guns N' Roses?\n\n" +
                "Yes. They are an American rock band. What is your favorite song?",
            result.spokenText,
        )
    }
}
