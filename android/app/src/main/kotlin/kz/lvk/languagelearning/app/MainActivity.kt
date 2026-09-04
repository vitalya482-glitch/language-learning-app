package kz.lvk.languagelearning.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kz.lvk.languagelearning.core.ai.LocalModelDescriptor
import kz.lvk.languagelearning.core.designsystem.LanguageLearningTheme
import kz.lvk.languagelearning.core.models.LocalModelCatalog
import kz.lvk.languagelearning.core.speech.SpeechLanguage
import kz.lvk.languagelearning.feature.conversation.ConversationScreen
import kz.lvk.languagelearning.feature.conversation.ConversationViewModel
import kz.lvk.languagelearning.feature.home.HomeScreen
import kz.lvk.languagelearning.feature.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as LanguageLearningApplication
        setContent {
            var showConversation by rememberSaveable { mutableStateOf(false) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(app.container.updateManager),
            )
            val updateState by mainViewModel.updateState.collectAsStateWithLifecycle()
            val appSettings by app.container.settingsRepository.settings.collectAsStateWithLifecycle()
            val targetSpeechLanguage = SpeechLanguage(appSettings.targetLanguageTag)
            val nativeSpeechLanguage = SpeechLanguage(appSettings.nativeLanguageTag)

            LanguageLearningTheme {
                when {
                    showConversation -> {
                        val modelSpec = LocalModelCatalog.Qwen3_0_6B_Q4KM
                        val modelPath = app.container.localModelManager.installedPath(modelSpec.id)
                        val modelDescriptor = modelPath?.let { path ->
                            LocalModelDescriptor(
                                id = modelSpec.id,
                                displayName = modelSpec.displayName,
                                localPath = path,
                            )
                        }
                        val conversationKey = buildString {
                            append("conversation-")
                            append(appSettings.nativeLanguageTag)
                            append('-')
                            append(appSettings.targetLanguageTag)
                            append('-')
                            append(appSettings.learningLevel.name)
                            append('-')
                            append(appSettings.includePhraseAnalysis)
                            append('-')
                            append(appSettings.includeNaturalPhrase)
                            append('-')
                            append(appSettings.includeConversationReply)
                            append('-')
                            append(appSettings.tutorExplanationLanguage.name)
                            append('-')
                            append(modelPath?.hashCode() ?: 0)
                        }
                        val conversationViewModel: ConversationViewModel = viewModel(
                            key = conversationKey,
                            factory = ConversationViewModel.Factory(
                                engine = app.container.languageModelEngine,
                                model = modelDescriptor,
                                nativeLanguageTag = appSettings.nativeLanguageTag,
                                targetLanguageTag = appSettings.targetLanguageTag,
                                learningLevel = appSettings.learningLevel.name,
                                includePhraseAnalysis = appSettings.includePhraseAnalysis,
                                includeNaturalPhrase = appSettings.includeNaturalPhrase,
                                includeConversationReply = appSettings.includeConversationReply,
                                explanationLanguageTag = appSettings.explanationLanguage.tag,
                            ),
                        )
                        val conversationState by conversationViewModel.state.collectAsStateWithLifecycle()

                        BackHandler { showConversation = false }
                        ConversationScreen(
                            state = conversationState,
                            onBack = { showConversation = false },
                            onSendMessage = conversationViewModel::sendMessage,
                            onRetryEngine = conversationViewModel::loadEngine,
                            speechLanguage = targetSpeechLanguage,
                            nativeSpeechLanguage = nativeSpeechLanguage,
                            explanationSpeechLanguage = SpeechLanguage(
                                appSettings.explanationLanguage.tag,
                            ),
                            ttsVoiceId = appSettings.targetVoiceId,
                            explanationTtsVoiceId = appSettings.explanationVoiceId,
                            nativeTtsVoiceId =
                                appSettings.explanationTtsVoiceIdsByLanguage[
                                    appSettings.nativeLanguageTag
                                ] ?: appSettings.ttsVoiceIdsByLanguage[
                                    appSettings.nativeLanguageTag
                                ],
                        )
                    }

                    showSettings -> {
                        BackHandler { showSettings = false }
                        SettingsScreen(
                            appSettings = appSettings,
                            localModelManager = app.container.localModelManager,
                            onBack = { showSettings = false },
                            onNativeLanguageChange = app.container.settingsRepository::setNativeLanguage,
                            onTargetLanguageChange = app.container.settingsRepository::setTargetLanguage,
                            onLearningLevelChange = app.container.settingsRepository::setLearningLevel,
                            onPhraseAnalysisEnabledChange =
                                app.container.settingsRepository::setPhraseAnalysisEnabled,
                            onNaturalPhraseEnabledChange =
                                app.container.settingsRepository::setNaturalPhraseEnabled,
                            onConversationReplyEnabledChange =
                                app.container.settingsRepository::setConversationReplyEnabled,
                            onTutorExplanationLanguageChange =
                                app.container.settingsRepository::setTutorExplanationLanguage,
                            onTtsVoiceChange = app.container.settingsRepository::setTtsVoice,
                            onExplanationTtsVoiceChange = app.container.settingsRepository::setExplanationTtsVoice,
                        )
                    }

                    else -> {
                        HomeScreen(
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE.toLong(),
                            updateState = updateState,
                            onStartLearning = { showConversation = true },
                            onSettings = { showSettings = true },
                            onCheckForUpdates = mainViewModel::checkForUpdates,
                            onInstallUpdate = mainViewModel::installUpdate,
                        )
                    }
                }
            }
        }
    }
}
