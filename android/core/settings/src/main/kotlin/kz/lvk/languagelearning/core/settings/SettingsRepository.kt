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

    private companion object {
        const val PREFERENCES_NAME = "language_learning_app_settings"
        const val KEY_NATIVE_LANGUAGE = "native_language_tag"
        const val KEY_TARGET_LANGUAGE = "target_language_tag"
        const val KEY_LEARNING_LEVEL = "learning_level"
        const val KEY_USER_DISPLAY_NAME = "user_display_name"
        const val KEY_TTS_VOICE_PREFIX = "tts_voice_"
        const val KEY_EXPLANATION_TTS_VOICE_PREFIX = "explanation_tts_voice_"
        const val LEGACY_TTS_PREFERENCES_NAME = "language_learning_system_tts"
    }
}
