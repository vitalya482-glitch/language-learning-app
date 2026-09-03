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
import kz.lvk.languagelearning.core.designsystem.LanguageLearningTheme
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

            LanguageLearningTheme {
                when {
                    showConversation -> {
                        val conversationViewModel: ConversationViewModel = viewModel(
                            factory = ConversationViewModel.Factory(app.container.languageModelEngine),
                        )
                        val conversationState by conversationViewModel.state.collectAsStateWithLifecycle()

                        BackHandler { showConversation = false }
                        ConversationScreen(
                            state = conversationState,
                            onBack = { showConversation = false },
                            onSendMessage = conversationViewModel::sendMessage,
                            onRetryEngine = conversationViewModel::loadEngine,
                            speechLanguage = targetSpeechLanguage,
                            ttsVoiceId = appSettings.targetVoiceId,
                        )
                    }

                    showSettings -> {
                        BackHandler { showSettings = false }
                        SettingsScreen(
                            appSettings = appSettings,
                            onBack = { showSettings = false },
                            onNativeLanguageChange = app.container.settingsRepository::setNativeLanguage,
                            onTargetLanguageChange = app.container.settingsRepository::setTargetLanguage,
                            onLearningLevelChange = app.container.settingsRepository::setLearningLevel,
                            onTtsVoiceChange = app.container.settingsRepository::setTtsVoice,
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
