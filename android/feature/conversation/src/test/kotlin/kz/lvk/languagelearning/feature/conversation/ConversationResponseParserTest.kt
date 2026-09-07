package kz.lvk.languagelearning.feature.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationResponseParserTest {
    @Test
    fun `parses english first teacher packet`() {
        val result = parseTeacherPacket(
            """
                STATUS: FIX
                CORRECTION: Yesterday I went to the mountains and walked a lot.
                WHY: Use "yesterday" for the previous day and use "walked" in the past tense.
                REPLY: That sounds like an active day. Who did you go with?
            """.trimIndent(),
        )

        assertEquals(true, result.needsCorrection)
        assertEquals("Yesterday I went to the mountains and walked a lot.", result.correction)
        assertTrue(result.why?.contains("walked") == true)
        assertEquals("That sounds like an active day. Who did you go with?", result.reply)
    }

    @Test
    fun `keeps useful fields when model omits status`() {
        val result = parseTeacherPacket(
            """
                CORRECTION: I'm a sales manager.
                WHY: Use the article "a" before the job title.
                REPLY: That sounds interesting. What do you sell?
            """.trimIndent(),
        )

        assertEquals(true, result.needsCorrection)
        assertEquals("I'm a sales manager.", result.correction)
        assertTrue(result.why?.startsWith("Use") == true)
    }

    @Test
    fun `understands none fields without rejecting packet`() {
        val result = parseTeacherPacket(
            """
                STATUS: OK
                CORRECTION: NONE
                WHY: CORRECT
                REPLY: I'm doing well, thank you. What are you doing today?
            """.trimIndent(),
        )

        assertEquals(false, result.needsCorrection)
        assertNull(result.correction)
        assertEquals("CORRECT", result.why)
    }

    @Test
    fun `structured response preparation preserves reply field`() {
        val raw = """
            STATUS: FIX
            CORRECTION: I like to travel.
            WHY: Add an object or complement after "I like".
            REPLY: Travelling is fun. Which place do you like most?
        """.trimIndent()

        val result = parseTeacherPacket(raw.prepareRawModelResponse())

        assertEquals("Add an object or complement after \"I like\".", result.why)
        assertEquals("Travelling is fun. Which place do you like most?", result.reply)
    }

    @Test
    fun `verbose correction field yields only corrected phrase`() {
        val result = "\"I like\" is incorrect. The correct phrase should be \"I like to travel.\""
            .extractCorrectionPhrase()

        assertEquals("I like to travel.", result)
    }

    @Test
    fun `empty teacher packet remains inconclusive`() {
        assertNull(parseTeacherPacket("").needsCorrection)
    }

    @Test
    fun `short greeting echo is detected`() {
        assertTrue("Hi, how are you? I'm learning English.".echoesLearnerPhrase("hi how are you?"))
        assertFalse("I'm doing well, thank you. What are you doing today?".echoesLearnerPhrase("hi how are you?"))
    }

    @Test
    fun `composes explanation correction and reply with speech languages`() {
        val result = composeTutorResponse(
            analysis = "Используй yesterday и прошедшую форму walked.",
            naturalPhrase = "Yesterday I went to the mountains and walked a lot.",
            reply = "That sounds like an active day. Who did you go with?",
            naturalPhraseIntroduction = "Так будет естественнее:",
        )

        assertTrue(result.visibleText.contains("Используй yesterday"))
        assertTrue(result.visibleText.contains("Yesterday I went to the mountains"))
        assertEquals(4, result.speechSegments.size)
        assertEquals(ConversationSpeechLanguage.Explanation, result.speechSegments[0].language)
        assertEquals(ConversationSpeechLanguage.Target, result.speechSegments[2].language)
        assertEquals("That sounds like an active day. Who did you go with?", result.conversationText)
    }

    @Test
    fun `model history keeps only conversational tutor reply`() {
        val messages = listOf(
            ConversationMessage(1, "I work in sales.", ConversationRole.User),
            ConversationMessage(
                id = 2,
                text = "Разбор.\n\nI work in sales.\n\nWhat do you sell?",
                role = ConversationRole.Assistant,
                conversationText = "What do you sell?",
            ),
            ConversationMessage(3, "I sell batteries.", ConversationRole.User),
        )

        assertEquals(
            "Learner: I work in sales.\nTutor: What do you sell?\nLearner: I sell batteries.",
            buildConversationHistory(messages),
        )
    }

    @Test
    fun `A1 greeting shortcut remains available`() {
        assertTrue("hi how are you".isClearlyCorrectA1Phrase("en-US"))
        assertTrue("hi how are you".looksLikeQuestion("en-US"))
        assertFalse("yerstoday i went in mountings".isClearlyCorrectA1Phrase("en-US"))
    }

    @Test
    fun `A1 reply requires exactly one question`() {
        assertTrue("I'm well, thanks. What are you doing today?".hasExpectedQuestionCount("A1"))
        assertFalse("How are you? What are you doing?".hasExpectedQuestionCount("A1"))
    }

    @Test
    fun `fallback stays on mountain topic`() {
        val reply = fallbackConversationReply(
            userText = "yerstoday i went in mountings and walk a lot",
            languageTag = "en-US",
            learningLevel = "A1",
            previousReplies = emptyList(),
        )

        assertEquals("That sounds like an active day! Which place did you like most?", reply)
    }

    @Test
    fun `script detector accepts Russian translation and English target`() {
        assertTrue("Используй прошедшую форму walked.".matchesExpectedLanguageScript("ru-RU"))
        assertTrue("Yesterday I walked a lot.".matchesExpectedLanguageScript("en-US"))
        assertFalse("The phrase is correct.".matchesExpectedLanguageScript("ru-RU"))
    }
}
