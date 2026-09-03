package kz.lvk.languagelearning.core.speech

import java.util.Locale

data class SpeechLanguage(
    val tag: String,
) {
    val languageCode: String
        get() = Locale.forLanguageTag(tag).language

    fun displayName(locale: Locale = Locale.getDefault()): String {
        val rawName = Locale.forLanguageTag(tag)
            .getDisplayLanguage(locale)
            .ifBlank { tag }

        return rawName.replaceFirstChar { first ->
            if (first.isLowerCase()) first.titlecase() else first.toString()
        }
    }
}

object SpeechLanguages {
    val English = SpeechLanguage("en-US")
    val German = SpeechLanguage("de-DE")
    val Spanish = SpeechLanguage("es-ES")
    val French = SpeechLanguage("fr-FR")
    val Italian = SpeechLanguage("it-IT")
    val Russian = SpeechLanguage("ru-RU")
    val Kazakh = SpeechLanguage("kk-KZ")
    val ChineseSimplified = SpeechLanguage("zh-CN")

    val all: List<SpeechLanguage> = listOf(
        English,
        German,
        Spanish,
        French,
        Italian,
        Russian,
        Kazakh,
        ChineseSimplified,
    )
}
