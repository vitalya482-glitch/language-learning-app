from pathlib import Path

source_path = Path('android/feature/conversation/src/main/kotlin/kz/lvk/languagelearning/feature/conversation/ConversationViewModel.kt')
test_path = Path('android/feature/conversation/src/test/kotlin/kz/lvk/languagelearning/feature/conversation/ConversationResponseParserTest.kt')

source = source_path.read_text(encoding='utf-8')
tests = test_path.read_text(encoding='utf-8')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    return text.replace(old, new, 1)


source = replace_once(
    source,
    '''        val correction = packet.correction
            ?.extractCorrectionPhrase()
            ?.sanitizeCorrection(userText, targetLanguageTag)
            ?.takeIf { includeNaturalPhrase }
        val needsCorrection = packet.needsCorrection
''',
    '''        val validatedCorrection = packet.correction
            ?.extractCorrectionPhrase()
            ?.sanitizeCorrection(userText, targetLanguageTag)
        val correction = validatedCorrection?.takeIf { includeNaturalPhrase }
        val needsCorrection = resolveCorrectionState(
            modelNeedsCorrection = packet.needsCorrection,
            validatedCorrection = validatedCorrection,
            correctionExpected = includeNaturalPhrase,
        )
''',
    'correction state resolution',
)

source = replace_once(
    source,
    '''                null -> {
                    packet.why
                        ?.cleanGenerationStage()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { translateExplanationIfNeeded(it) }
                        ?: inconclusiveFeedback(explanationLanguageTag)
                }
''',
    '''                null -> inconclusiveFeedback(explanationLanguageTag)
''',
    'inconclusive analysis branch',
)

source = replace_once(
    source,
    '        return translated ?: englishText\n',
    '        return translated ?: translationFailureFeedback(explanationLanguageTag)\n',
    'translation failure fallback',
)

marker = 'internal data class ParsedTutorResponse(\n'
helper = '''internal fun resolveCorrectionState(
    modelNeedsCorrection: Boolean?,
    validatedCorrection: String?,
    correctionExpected: Boolean,
): Boolean? = when {
    validatedCorrection != null -> true
    correctionExpected && modelNeedsCorrection == true -> null
    else -> modelNeedsCorrection
}

'''
if marker not in source:
    raise SystemExit('resolve helper insertion marker not found')
source = source.replace(marker, helper + marker, 1)

marker = 'private fun correctionFallback(languageTag: String): String =\n'
helper = '''internal fun translationFailureFeedback(languageTag: String): String =
    when (languageTag.substringBefore('-').lowercase()) {
        "ru" -> "Фразу стоит исправить, но подробное объяснение перевести не удалось."
        "de" -> "Der Satz sollte korrigiert werden, aber die Erklärung konnte nicht übersetzt werden."
        "es" -> "La frase necesita corrección, pero no se pudo traducir la explicación."
        "fr" -> "La phrase doit être corrigée, mais l’explication n’a pas pu être traduite."
        "it" -> "La frase va corretta, ma non è stato possibile tradurre la spiegazione."
        "kk" -> "Сөйлемді түзету керек, бірақ түсіндірмені аудару мүмкін болмады."
        "zh" -> "这句话需要修改，但无法翻译详细说明。"
        else -> "This phrase needs correction, but the explanation could not be translated."
    }

'''
if marker not in source:
    raise SystemExit('translation fallback insertion marker not found')
source = source.replace(marker, helper + marker, 1)

test_insert = '''
    @Test
    fun `real correction overrides model OK status`() {
        assertEquals(
            true,
            resolveCorrectionState(
                modelNeedsCorrection = false,
                validatedCorrection = "They were shown last month.",
                correctionExpected = true,
            ),
        )
    }

    @Test
    fun `FIX without usable correction becomes inconclusive when correction is expected`() {
        assertNull(
            resolveCorrectionState(
                modelNeedsCorrection = true,
                validatedCorrection = null,
                correctionExpected = true,
            ),
        )
    }

    @Test
    fun `FIX remains valid when correction display is disabled`() {
        assertEquals(
            true,
            resolveCorrectionState(
                modelNeedsCorrection = true,
                validatedCorrection = null,
                correctionExpected = false,
            ),
        )
    }

    @Test
    fun `Russian translation failure fallback never exposes English WHY`() {
        val fallback = translationFailureFeedback("ru-RU")
        assertTrue(fallback.contains("перевести"))
        assertFalse(fallback.contains("This phrase"))
    }
'''

test_marker = '\n    @Test\n    fun `short greeting echo is detected`() {'
if test_marker not in tests:
    raise SystemExit('test insertion marker not found')
tests = tests.replace(test_marker, test_insert + test_marker, 1)

source_path.write_text(source, encoding='utf-8')
test_path.write_text(tests, encoding='utf-8')
