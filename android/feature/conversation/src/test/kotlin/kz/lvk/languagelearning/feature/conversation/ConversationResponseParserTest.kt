package kz.lvk.languagelearning.feature.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `composes enabled sections and preserves speech languages`() {
        val result = composeTutorResponse(
            analysis = "Use 'Do you know' when asking whether someone is familiar with a band.",
            naturalPhrase = "Do you know the band Guns N' Roses?",
            reply = "Yes. They are an American rock band. What is your favorite song?",
            naturalPhraseIntroduction = "Такая фраза звучала бы естественнее:",
        )

        assertEquals(
            "Use 'Do you know' when asking whether someone is familiar with a band.\n\n" +
                "Такая фраза звучала бы естественнее:\n" +
                "Do you know the band Guns N' Roses?\n\n" +
                "Yes. They are an American rock band. What is your favorite song?",
            result.visibleText,
        )
        assertEquals(4, result.speechSegments.size)
        assertEquals(
            ConversationSpeechLanguage.Explanation,
            result.speechSegments[0].language,
        )
        assertEquals(
            ConversationSpeechLanguage.Target,
            result.speechSegments[2].language,
        )
        assertEquals(
            "Yes. They are an American rock band. What is your favorite song?",
            result.conversationText,
        )
    }

    @Test
    fun `composes only selected sections`() {
        val result = composeTutorResponse(
            analysis = null,
            naturalPhrase = null,
            reply = "Yes, I know them. Which song do you like?",
            naturalPhraseIntroduction = "A more natural way to say this is:",
        )

        assertEquals("Yes, I know them. Which song do you like?", result.visibleText)
        assertEquals(1, result.speechSegments.size)
        assertEquals(ConversationSpeechLanguage.Target, result.speechSegments.single().language)
    }

    @Test
    fun `formats the complete dialog for debugging`() {
        val messages = listOf(
            ConversationMessage(1, "How are you?", ConversationRole.User),
            ConversationMessage(2, "I'm well.", ConversationRole.Assistant),
        )

        assertEquals(
            "USER:\nHow are you?\n\nAI TUTOR:\nI'm well.",
            formatConversationForClipboard(messages),
        )
    }

    @Test
    fun `model history contains replies but excludes teaching sections`() {
        val messages = listOf(
            ConversationMessage(1, "Can you check my English level?", ConversationRole.User),
            ConversationMessage(
                id = 2,
                text = "Long analysis and correction.\n\nTell me about your job.",
                role = ConversationRole.Assistant,
                conversationText = "Tell me about your job.",
            ),
            ConversationMessage(3, "I am a source manager.", ConversationRole.User),
        )

        assertEquals(
            "Learner: Can you check my English level?\n" +
                "Tutor: Tell me about your job.\n" +
                "Learner: I am a source manager.",
            buildConversationHistory(messages),
        )
    }

    @Test
    fun `rejects a tutor reply masquerading as a natural rewrite`() {
        assertFalse(
            "How can I help you assess your English level today?"
                .isPlausibleRewriteOf("Can you please check my English level?"),
        )
        assertEquals(
            true,
            "Could you please assess my English level?"
                .isPlausibleRewriteOf("Can you please check my English level?"),
        )
        assertFalse(
            "Hello, I'm a beginner. Let me start a conversation."
                .isPlausibleRewriteOf("Hello"),
        )
    }

    @Test
    fun `validates the requested output script`() {
        assertEquals(true, "Фраза построена правильно.".matchesExpectedLanguageScript("ru-RU"))
        assertFalse("The phrase is correct.".matchesExpectedLanguageScript("ru-RU"))
        assertEquals(true, "The phrase is correct.".matchesExpectedLanguageScript("en-US"))
    }

    @Test
    fun `parses an ok verdict without exposing the protocol`() {
        val result = parseLanguageAnalysis(
            "VERDICT: OK\nФраза понятна и естественно звучит в разговоре.",
        )

        assertFalse(result.needsCorrection ?: true)
        assertEquals("Фраза понятна и естественно звучит в разговоре.", result.text)
    }

    @Test
    fun `parses a correction verdict`() {
        val result = parseLanguageAnalysis(
            "VERDICT: NEEDS_CORRECTION\nUse 'a distribution company' here.",
        )

        assertTrue(result.needsCorrection ?: false)
        assertEquals("Use 'a distribution company' here.", result.text)
    }

    @Test
    fun `ignores punctuation-only natural phrase changes`() {
        assertFalse("Hello, where are you?".isMeaningfullyDifferentFrom("Hello where are you"))
        assertTrue("I'm here".isMeaningfullyDifferentFrom("Im here"))
    }
}
