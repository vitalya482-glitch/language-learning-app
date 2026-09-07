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

    @Test
    fun `recognizes a correct A1 greeting without punctuation`() {
        assertTrue("Hello how are you".isClearlyCorrectA1Phrase("en-US"))
        assertTrue("Hello how are you".looksLikeQuestion("en-US"))
        assertFalse("I manager company".isClearlyCorrectA1Phrase("en-US"))
    }

    @Test
    fun `uses concise Russian A1 feedback before continuing`() {
        assertEquals(
            "Грамматика верна, ты правильно задал вопрос.\nДавай продолжим диалог:",
            correctA1Feedback(languageTag = "ru-RU", isQuestion = true),
        )
    }

    @Test
    fun `detects a repeated tutor question inside a different reply`() {
        val previous = "Yes, please tell me about your day. What did you do today?"
        val candidate = "That sounds good. What did you do today?"

        assertTrue(candidate.repeatsQuestionFrom(previous))
        assertFalse("That sounds good. What did you enjoy most?".repeatsQuestionFrom(previous))
    }

    @Test
    fun `detects when a reply echoes the learner sentence`() {
        assertTrue(
            "Yes, today I went in the mountains and walked a lot. What happened next?"
                .echoesLearnerPhrase("Today I went in mountains and walk a lot"),
        )
        assertFalse(
            "That sounds like an active day! What did you enjoy most?"
                .echoesLearnerPhrase("Today I went in mountains and walk a lot"),
        )
    }

    @Test
    fun `reply prompt contains the current phrase once and excludes analysis`() {
        val input = buildReplyInput(
            conversationHistory = "Learner: Hello\nTutor: Hi! How are you?",
            userText = "My day was busy",
        )

        assertEquals(1, Regex("My day was busy").findAll(input).count())
        assertFalse(input.contains("LANGUAGE ANALYSIS"))
    }

    @Test
    fun `A1 reply requires exactly one continuing question`() {
        assertTrue("I'm well, thanks. How are you?".hasExpectedQuestionCount("A1"))
        assertFalse("I'm well, thanks.".hasExpectedQuestionCount("A1"))
        assertFalse("How are you? What are you doing?".hasExpectedQuestionCount("A1"))
    }

    @Test
    fun `fallback continues the current topic instead of repeating a previous question`() {
        val previous = listOf(
            "Yes, please tell me about your day. What happened first?",
            "That sounds like an active day! Which place did you like most?",
        )

        val result = fallbackConversationReply(
            userText = "What about you, how is your day going?",
            languageTag = "en-US",
            learningLevel = "A1",
            previousReplies = previous,
        )

        assertEquals(
            "My day is going well, thank you. What was the best part of your day?",
            result,
        )
        assertTrue(result.hasExpectedQuestionCount("A1"))
        assertTrue(previous.none(result::repeatsQuestionFrom))
    }

    @Test
    fun `fallback skips a generic reply that was already used`() {
        val previous = listOf("That sounds interesting. What happened next?")

        val result = fallbackConversationReply(
            userText = "I read a book",
            languageTag = "en-US",
            learningLevel = "A1",
            previousReplies = previous,
        )

        assertFalse(result.isNearDuplicateOf(previous.single()))
        assertFalse(result.repeatsQuestionFrom(previous.single()))
    }
}
