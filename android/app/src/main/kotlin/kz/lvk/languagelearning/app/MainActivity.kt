package kz.lvk.languagelearning.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kz.lvk.languagelearning.core.ai.LocalModelDescriptor
import kz.lvk.languagelearning.core.designsystem.LanguageLearningTheme
import kz.lvk.languagelearning.core.models.LocalModelStatus
import kz.lvk.languagelearning.core.speech.SpeechLanguage
import kz.lvk.languagelearning.feature.conversation.ConversationRole
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
            val compositionScope = rememberCoroutineScope()
            var engineUnloadJob by remember { mutableStateOf<Job?>(null) }
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(app.container.updateManager),
            )
            val updateState by mainViewModel.updateState.collectAsStateWithLifecycle()
            val appSettings by app.container.settingsRepository.settings.collectAsStateWithLifecycle()
            val localModelsState by app.container.localModelManager.state.collectAsStateWithLifecycle()
            val diagnosticEvents by app.container.modelDiagnostics.events.collectAsStateWithLifecycle()
            val targetSpeechLanguage = SpeechLanguage(appSettings.targetLanguageTag)
            val nativeSpeechLanguage = SpeechLanguage(appSettings.nativeLanguageTag)

            LanguageLearningTheme {
                when {
                    showConversation -> {
                        // Keep the model fixed for the lifetime of this conversation. Download
                        // progress updates also refresh available RAM and must not replace a model
                        // that is already loaded by the native engine.
                        val modelEntry = remember(showConversation) {
                            localModelsState.entries.firstOrNull {
                                it.spec.id == localModelsState.selectedModelId &&
                                    it.status is LocalModelStatus.Installed
                            }
                        }
                        val modelSpec = modelEntry?.spec
                        val modelPath = (modelEntry?.status as? LocalModelStatus.Installed)?.localPath
                        val modelDescriptor = modelPath?.let { path ->
                            LocalModelDescriptor(
                                id = checkNotNull(modelSpec).id,
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
                            append(modelSpec?.id ?: "none")
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
                        LaunchedEffect(conversationKey, conversationViewModel) {
                            conversationViewModel.loadEngine()
                        }
                        val conversationState by conversationViewModel.state.collectAsStateWithLifecycle()
                        val lastAssistantMessage = conversationState.messages.lastOrNull {
                            it.role == ConversationRole.Assistant
                        }
                        var lastLoggedAssistantId by remember(conversationKey) {
                            mutableStateOf<Long?>(null)
                        }
                        LaunchedEffect(lastAssistantMessage?.id) {
                            val message = lastAssistantMessage ?: return@LaunchedEffect
                            if (message.id != lastLoggedAssistantId) {
                                app.container.modelDiagnostics.record("AI TUTOR", message.text)
                                lastLoggedAssistantId = message.id
                            }
                        }

                        val closeConversation = {
                            conversationViewModel.closeSession()
                            showConversation = false
                            engineUnloadJob = compositionScope.launch {
                                try {
                                    app.container.languageModelEngine.unload()
                                } finally {
                                    app.container.localModelManager.refresh()
                                }
                            }
                            Unit
                        }
                        BackHandler(onBack = closeConversation)
                        Box(modifier = Modifier.fillMaxSize()) {
                            ConversationScreen(
                                state = conversationState,
                                onBack = closeConversation,
                                onSendMessage = { text ->
                                    app.container.modelDiagnostics.record("USER", text)
                                    conversationViewModel.sendMessage(text)
                                },
                                onRetryEngine = conversationViewModel::retry,
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
                            ConversationDiagnosticsOverlay(
                                events = diagnosticEvents,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .statusBarsPadding()
                                    .padding(top = 8.dp, end = 8.dp),
                            )
                        }
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
                            onStartLearning = {
                                compositionScope.launch {
                                    val pendingUnload = engineUnloadJob
                                    pendingUnload?.join()
                                    if (engineUnloadJob === pendingUnload) engineUnloadJob = null
                                    app.container.localModelManager.refresh()
                                    app.container.localModelManager.prepareSelectedModelForUse()
                                    app.container.modelDiagnostics.clear()
                                    showConversation = true
                                }
                            },
                            onSettings = {
                                compositionScope.launch {
                                    val pendingUnload = engineUnloadJob
                                    pendingUnload?.join()
                                    if (engineUnloadJob === pendingUnload) engineUnloadJob = null
                                    app.container.localModelManager.refresh()
                                    showSettings = true
                                }
                            },
                            onCheckForUpdates = mainViewModel::checkForUpdates,
                            onInstallUpdate = mainViewModel::installUpdate,
                        )
                    }
                }
            }
        }
    }
}
