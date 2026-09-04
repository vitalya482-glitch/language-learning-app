package kz.lvk.languagelearning.core.settings

import java.util.Locale

data class AppLanguage(
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

object LanguageCatalog {
    val English = AppLanguage("en-US")
    val German = AppLanguage("de-DE")
    val Spanish = AppLanguage("es-ES")
    val French = AppLanguage("fr-FR")
    val Italian = AppLanguage("it-IT")
    val Russian = AppLanguage("ru-RU")
    val Kazakh = AppLanguage("kk-KZ")
    val ChineseSimplified = AppLanguage("zh-CN")

    val all: List<AppLanguage> = listOf(
        English,
        German,
        Spanish,
        French,
        Italian,
        Russian,
        Kazakh,
        ChineseSimplified,
    )

    fun byTag(tag: String): AppLanguage? = all.firstOrNull { it.tag == tag }
}

enum class LearningLevel {
    A1,
    A2,
    B1,
    B2,
    C1,
}

enum class TutorExplanationLanguage {
    Native,
    Target,
}

data class AppSettings(
    val nativeLanguageTag: String = LanguageCatalog.Russian.tag,
    val targetLanguageTag: String = LanguageCatalog.English.tag,
    val learningLevel: LearningLevel = LearningLevel.A1,
    val userDisplayName: String? = null,
    val includePhraseAnalysis: Boolean = true,
    val includeNaturalPhrase: Boolean = true,
    val includeConversationReply: Boolean = true,
    val tutorExplanationLanguage: TutorExplanationLanguage = TutorExplanationLanguage.Native,
    val ttsVoiceIdsByLanguage: Map<String, String> = emptyMap(),
    val explanationTtsVoiceIdsByLanguage: Map<String, String> = emptyMap(),
) {
    val nativeLanguage: AppLanguage
        get() = LanguageCatalog.byTag(nativeLanguageTag) ?: LanguageCatalog.Russian

    val targetLanguage: AppLanguage
        get() = LanguageCatalog.byTag(targetLanguageTag) ?: LanguageCatalog.English

    val targetVoiceId: String?
        get() = ttsVoiceIdsByLanguage[targetLanguageTag]

    val explanationLanguage: AppLanguage
        get() = when (tutorExplanationLanguage) {
            TutorExplanationLanguage.Native -> nativeLanguage
            TutorExplanationLanguage.Target -> targetLanguage
        }

    val explanationVoiceId: String?
        get() = explanationTtsVoiceIdsByLanguage[explanationLanguage.tag]
            ?: ttsVoiceIdsByLanguage[explanationLanguage.tag]
}
