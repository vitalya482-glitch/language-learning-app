package kz.lvk.languagelearning.core.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    fun setNativeLanguage(language: AppLanguage)
    fun setTargetLanguage(language: AppLanguage)
    fun setLearningLevel(level: LearningLevel)
    fun setUserDisplayName(name: String?)
    fun setPhraseAnalysisEnabled(enabled: Boolean)
    fun setNaturalPhraseEnabled(enabled: Boolean)
    fun setConversationReplyEnabled(enabled: Boolean)
    fun setTutorExplanationLanguage(language: TutorExplanationLanguage)
    fun setTtsVoice(language: AppLanguage, voiceId: String?)
    fun setExplanationTtsVoice(language: AppLanguage, voiceId: String?)
}

class SharedPreferencesSettingsRepository(context: Context) : SettingsRepository {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private val _settings = MutableStateFlow(loadSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        migrateLegacyTtsVoicesIfNeeded()
    }

    override fun setNativeLanguage(language: AppLanguage) {
        preferences.edit()
            .putString(KEY_NATIVE_LANGUAGE, language.tag)
            .apply()
        _settings.update { it.copy(nativeLanguageTag = language.tag) }
    }

    override fun setTargetLanguage(language: AppLanguage) {
        preferences.edit()
            .putString(KEY_TARGET_LANGUAGE, language.tag)
            .apply()
        _settings.update { it.copy(targetLanguageTag = language.tag) }
    }

    override fun setLearningLevel(level: LearningLevel) {
        preferences.edit()
            .putString(KEY_LEARNING_LEVEL, level.name)
            .apply()
        _settings.update { it.copy(learningLevel = level) }
    }

    override fun setUserDisplayName(name: String?) {
        val normalizedName = name?.trim()?.takeIf { it.isNotEmpty() }
        preferences.edit().apply {
            if (normalizedName == null) {
                remove(KEY_USER_DISPLAY_NAME)
            } else {
                putString(KEY_USER_DISPLAY_NAME, normalizedName)
            }
        }.apply()
        _settings.update { it.copy(userDisplayName = normalizedName) }
    }

    override fun setPhraseAnalysisEnabled(enabled: Boolean) {
        updateResponseOption(KEY_INCLUDE_PHRASE_ANALYSIS, enabled) { current ->
            current.copy(includePhraseAnalysis = enabled)
        }
    }

    override fun setNaturalPhraseEnabled(enabled: Boolean) {
        updateResponseOption(KEY_INCLUDE_NATURAL_PHRASE, enabled) { current ->
            current.copy(includeNaturalPhrase = enabled)
        }
    }

    override fun setConversationReplyEnabled(enabled: Boolean) {
        updateResponseOption(KEY_INCLUDE_CONVERSATION_REPLY, enabled) { current ->
            current.copy(includeConversationReply = enabled)
        }
    }

    override fun setTutorExplanationLanguage(language: TutorExplanationLanguage) {
        preferences.edit()
            .putString(KEY_TUTOR_EXPLANATION_LANGUAGE, language.name)
            .apply()
        _settings.update { it.copy(tutorExplanationLanguage = language) }
    }

    override fun setTtsVoice(language: AppLanguage, voiceId: String?) {
        val key = voicePreferenceKey(language.tag)
        preferences.edit().apply {
            if (voiceId == null) {
                remove(key)
            } else {
                putString(key, voiceId)
            }
        }.apply()

        _settings.update { current ->
            val updatedVoices = current.ttsVoiceIdsByLanguage.toMutableMap().apply {
                if (voiceId == null) {
                    remove(language.tag)
                } else {
                    put(language.tag, voiceId)
                }
            }
            current.copy(ttsVoiceIdsByLanguage = updatedVoices)
        }
    }

    override fun setExplanationTtsVoice(language: AppLanguage, voiceId: String?) {
        val key = explanationVoicePreferenceKey(language.tag)
        preferences.edit().apply {
            if (voiceId == null) {
                remove(key)
            } else {
                putString(key, voiceId)
            }
        }.apply()

        _settings.update { current ->
            val updatedVoices = current.explanationTtsVoiceIdsByLanguage.toMutableMap().apply {
                if (voiceId == null) {
                    remove(language.tag)
                } else {
                    put(language.tag, voiceId)
                }
            }
            current.copy(explanationTtsVoiceIdsByLanguage = updatedVoices)
        }
    }

    private fun loadSettings(): AppSettings {
        val nativeTag = preferences.getString(
            KEY_NATIVE_LANGUAGE,
            LanguageCatalog.Russian.tag,
        ).orEmpty().takeIf { LanguageCatalog.byTag(it) != null }
            ?: LanguageCatalog.Russian.tag

        val targetTag = preferences.getString(
            KEY_TARGET_LANGUAGE,
            LanguageCatalog.English.tag,
        ).orEmpty().takeIf { LanguageCatalog.byTag(it) != null }
            ?: LanguageCatalog.English.tag

        val level = preferences.getString(KEY_LEARNING_LEVEL, LearningLevel.A1.name)
            ?.let { saved -> runCatching { LearningLevel.valueOf(saved) }.getOrNull() }
            ?: LearningLevel.A1

        val userDisplayName = preferences.getString(KEY_USER_DISPLAY_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        var includePhraseAnalysis = preferences.getBoolean(KEY_INCLUDE_PHRASE_ANALYSIS, true)
        var includeNaturalPhrase = preferences.getBoolean(KEY_INCLUDE_NATURAL_PHRASE, true)
        var includeConversationReply = preferences.getBoolean(KEY_INCLUDE_CONVERSATION_REPLY, true)
        if (!includePhraseAnalysis && !includeNaturalPhrase && !includeConversationReply) {
            includeConversationReply = true
        }
        val tutorExplanationLanguage = preferences
            .getString(KEY_TUTOR_EXPLANATION_LANGUAGE, TutorExplanationLanguage.Native.name)
            ?.let { saved ->
                runCatching { TutorExplanationLanguage.valueOf(saved) }.getOrNull()
            }
            ?: TutorExplanationLanguage.Native

        val voices = LanguageCatalog.all.mapNotNull { language ->
            preferences.getString(voicePreferenceKey(language.tag), null)
                ?.takeIf { it.isNotBlank() }
                ?.let { language.tag to it }
        }.toMap()

        val explanationVoices = LanguageCatalog.all.mapNotNull { language ->
            preferences.getString(explanationVoicePreferenceKey(language.tag), null)
                ?.takeIf { it.isNotBlank() }
                ?.let { language.tag to it }
        }.toMap()

        return AppSettings(
            nativeLanguageTag = nativeTag,
            targetLanguageTag = targetTag,
            learningLevel = level,
            userDisplayName = userDisplayName,
            includePhraseAnalysis = includePhraseAnalysis,
            includeNaturalPhrase = includeNaturalPhrase,
            includeConversationReply = includeConversationReply,
            tutorExplanationLanguage = tutorExplanationLanguage,
            ttsVoiceIdsByLanguage = voices,
            explanationTtsVoiceIdsByLanguage = explanationVoices,
        )
    }

    private fun migrateLegacyTtsVoicesIfNeeded() {
        val legacyPreferences = appContext.getSharedPreferences(
            LEGACY_TTS_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val editor = preferences.edit()
        var changed = false

        LanguageCatalog.all.forEach { language ->
            val newKey = voicePreferenceKey(language.tag)
            if (!preferences.contains(newKey)) {
                val oldKey = "voice_${language.languageCode}"
                val legacyVoice = legacyPreferences.getString(oldKey, null)
                if (!legacyVoice.isNullOrBlank()) {
                    editor.putString(newKey, legacyVoice)
                    changed = true
                }
            }
        }

        if (changed) {
            editor.apply()
            _settings.value = loadSettings()
        }
    }

    private fun voicePreferenceKey(languageTag: String): String =
        "$KEY_TTS_VOICE_PREFIX$languageTag"

    private fun explanationVoicePreferenceKey(languageTag: String): String =
        "$KEY_EXPLANATION_TTS_VOICE_PREFIX$languageTag"

    private fun updateResponseOption(
        key: String,
        enabled: Boolean,
        transform: (AppSettings) -> AppSettings,
    ) {
        val updated = transform(_settings.value)
        if (
            !updated.includePhraseAnalysis &&
            !updated.includeNaturalPhrase &&
            !updated.includeConversationReply
        ) {
            return
        }

        preferences.edit().putBoolean(key, enabled).apply()
        _settings.value = updated
    }

    private companion object {
        const val PREFERENCES_NAME = "language_learning_app_settings"
        const val KEY_NATIVE_LANGUAGE = "native_language_tag"
        const val KEY_TARGET_LANGUAGE = "target_language_tag"
        const val KEY_LEARNING_LEVEL = "learning_level"
        const val KEY_USER_DISPLAY_NAME = "user_display_name"
        const val KEY_INCLUDE_PHRASE_ANALYSIS = "include_phrase_analysis"
        const val KEY_INCLUDE_NATURAL_PHRASE = "include_natural_phrase"
        const val KEY_INCLUDE_CONVERSATION_REPLY = "include_conversation_reply"
        const val KEY_TUTOR_EXPLANATION_LANGUAGE = "tutor_explanation_language"
        const val KEY_TTS_VOICE_PREFIX = "tts_voice_"
        const val KEY_EXPLANATION_TTS_VOICE_PREFIX = "explanation_tts_voice_"
        const val LEGACY_TTS_PREFERENCES_NAME = "language_learning_system_tts"
    }
}
