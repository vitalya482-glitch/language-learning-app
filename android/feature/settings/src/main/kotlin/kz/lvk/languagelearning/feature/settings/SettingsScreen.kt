package kz.lvk.languagelearning.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.lvk.languagelearning.core.models.LocalModelManager
import kz.lvk.languagelearning.core.settings.AppLanguage
import kz.lvk.languagelearning.core.settings.AppSettings
import kz.lvk.languagelearning.core.settings.DeviceDisplayName
import kz.lvk.languagelearning.core.settings.LanguageCatalog
import kz.lvk.languagelearning.core.settings.LearningLevel
import kz.lvk.languagelearning.core.settings.TutorExplanationLanguage
import kz.lvk.languagelearning.core.speech.AndroidSystemTextToSpeech
import kz.lvk.languagelearning.core.speech.SpeechLanguage
import kz.lvk.languagelearning.core.speech.SystemTextToSpeechState
import kz.lvk.languagelearning.core.speech.SystemTtsVoice
import kz.lvk.languagelearning.feature.models.LocalModelsScreen

@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    localModelManager: LocalModelManager,
    onBack: () -> Unit,
    onNativeLanguageChange: (AppLanguage) -> Unit,
    onTargetLanguageChange: (AppLanguage) -> Unit,
    onLearningLevelChange: (LearningLevel) -> Unit,
    onPhraseAnalysisEnabledChange: (Boolean) -> Unit,
    onNaturalPhraseEnabledChange: (Boolean) -> Unit,
    onConversationReplyEnabledChange: (Boolean) -> Unit,
    onTutorExplanationLanguageChange: (TutorExplanationLanguage) -> Unit,
    onTtsVoiceChange: (AppLanguage, String?) -> Unit,
    onExplanationTtsVoiceChange: (AppLanguage, String?) -> Unit,
) {
    val context = LocalContext.current
    var showLocalModels by rememberSaveable { mutableStateOf(false) }
    val localModelsState by localModelManager.state.collectAsStateWithLifecycle()

    if (showLocalModels) {
        LocalModelsScreen(
            state = localModelsState,
            onBack = { showLocalModels = false },
            onDownload = localModelManager::download,
            onDelete = localModelManager::delete,
        )
        return
    }

    val targetTts = remember(context) {
        AndroidSystemTextToSpeech(context.applicationContext)
    }
    val explanationTts = remember(context) {
        AndroidSystemTextToSpeech(context.applicationContext)
    }
    val targetTtsState by targetTts.state.collectAsStateWithLifecycle()
    val explanationTtsState by explanationTts.state.collectAsStateWithLifecycle()

    val targetLanguage = appSettings.targetLanguage
    val nativeLanguage = appSettings.nativeLanguage
    val explanationLanguage = appSettings.explanationLanguage
    val targetSpeechLanguage = remember(targetLanguage.tag) {
        SpeechLanguage(targetLanguage.tag)
    }
    val explanationSpeechLanguage = remember(explanationLanguage.tag) {
        SpeechLanguage(explanationLanguage.tag)
    }
    val preferredTargetVoiceId = appSettings.targetVoiceId
    val preferredExplanationVoiceId = appSettings.explanationVoiceId
    val previewName = remember(context, appSettings.userDisplayName) {
        appSettings.userDisplayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DeviceDisplayName.resolve(context.applicationContext)
    }
    val targetPreviewText = remember(targetLanguage.tag, previewName) {
        buildVoicePreviewText(targetLanguage, previewName)
    }
    val explanationPreviewText = remember(explanationLanguage.tag, previewName) {
        buildVoicePreviewText(explanationLanguage, previewName)
    }

    LaunchedEffect(targetLanguage.tag, preferredTargetVoiceId) {
        targetTts.prepare(targetSpeechLanguage, preferredTargetVoiceId)
    }
    LaunchedEffect(explanationLanguage.tag, preferredExplanationVoiceId) {
        explanationTts.prepare(explanationSpeechLanguage, preferredExplanationVoiceId)
    }

    DisposableEffect(targetTts, explanationTts) {
        onDispose {
            targetTts.close()
            explanationTts.close()
        }
    }

    fun selectAndPreviewTargetVoice(voiceId: String) {
        explanationTts.stop()
        targetTts.selectVoice(targetSpeechLanguage, voiceId)
        onTtsVoiceChange(targetLanguage, voiceId)
        targetTts.speak(targetPreviewText, targetSpeechLanguage)
    }

    fun selectAndPreviewExplanationVoice(voiceId: String) {
        targetTts.stop()
        explanationTts.selectVoice(explanationSpeechLanguage, voiceId)
        onExplanationTtsVoiceChange(explanationLanguage, voiceId)
        explanationTts.speak(explanationPreviewText, explanationSpeechLanguage)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.settings_back))
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.settings_learning_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(14.dp))

                SettingsChoice(
                    label = stringResource(R.string.settings_native_language),
                    selectedText = nativeLanguage.displayName(),
                    options = LanguageCatalog.all,
                    optionText = { it.displayName() },
                    onSelected = onNativeLanguageChange,
                )
                Spacer(Modifier.height(14.dp))

                SettingsChoice(
                    label = stringResource(R.string.settings_target_language),
                    selectedText = targetLanguage.displayName(),
                    options = LanguageCatalog.all,
                    optionText = { it.displayName() },
                    onSelected = onTargetLanguageChange,
                )
                Spacer(Modifier.height(14.dp))

                SettingsChoice(
                    label = stringResource(R.string.settings_learning_level),
                    selectedText = appSettings.learningLevel.name,
                    options = LearningLevel.entries,
                    optionText = { it.name },
                    onSelected = onLearningLevelChange,
                )

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.settings_tutor_response_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_tutor_response_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))

                val selectedResponseOptionCount = listOf(
                    appSettings.includePhraseAnalysis,
                    appSettings.includeNaturalPhrase,
                    appSettings.includeConversationReply,
                ).count { it }
                ResponseOptionRow(
                    checked = appSettings.includePhraseAnalysis,
                    enabled = !appSettings.includePhraseAnalysis || selectedResponseOptionCount > 1,
                    title = stringResource(R.string.settings_response_analysis),
                    description = stringResource(R.string.settings_response_analysis_note),
                    onCheckedChange = onPhraseAnalysisEnabledChange,
                )
                ResponseOptionRow(
                    checked = appSettings.includeNaturalPhrase,
                    enabled = !appSettings.includeNaturalPhrase || selectedResponseOptionCount > 1,
                    title = stringResource(R.string.settings_response_natural_phrase),
                    description = stringResource(R.string.settings_response_natural_phrase_note),
                    onCheckedChange = onNaturalPhraseEnabledChange,
                )
                ResponseOptionRow(
                    checked = appSettings.includeConversationReply,
                    enabled = !appSettings.includeConversationReply || selectedResponseOptionCount > 1,
                    title = stringResource(R.string.settings_response_reply),
                    description = stringResource(R.string.settings_response_reply_note),
                    onCheckedChange = onConversationReplyEnabledChange,
                )

                if (appSettings.includePhraseAnalysis || appSettings.includeNaturalPhrase) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_explanation_language),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    ExplanationLanguageRow(
                        selected = appSettings.tutorExplanationLanguage ==
                            TutorExplanationLanguage.Native,
                        label = nativeLanguage.displayName(),
                        onSelect = {
                            onTutorExplanationLanguageChange(TutorExplanationLanguage.Native)
                        },
                    )
                    ExplanationLanguageRow(
                        selected = appSettings.tutorExplanationLanguage ==
                            TutorExplanationLanguage.Target,
                        label = targetLanguage.displayName(),
                        onSelect = {
                            onTutorExplanationLanguageChange(TutorExplanationLanguage.Target)
                        },
                    )
                }

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.settings_models_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_models_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        localModelManager.refresh()
                        showLocalModels = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_models_open))
                }

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.settings_voice_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_voice_system_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_voice_preview_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.settings_target_voice_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.settings_voice_language,
                        targetLanguage.displayName(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }

            voicePickerItems(
                keyPrefix = "target",
                state = targetTtsState,
                onSelectVoice = ::selectAndPreviewTargetVoice,
            )

            item {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.settings_explanation_voice_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_explanation_voice_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.settings_voice_language,
                        explanationLanguage.displayName(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }

            voicePickerItems(
                keyPrefix = "explanation",
                state = explanationTtsState,
                onSelectVoice = ::selectAndPreviewExplanationVoice,
            )
        }
    }
}

@Composable
private fun ResponseOptionRow(
    checked: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExplanationLanguageRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.voicePickerItems(
    keyPrefix: String,
    state: SystemTextToSpeechState,
    onSelectVoice: (String) -> Unit,
) {
    when {
        !state.isReady && state.errorMessage == null -> {
            item(key = "$keyPrefix-loading") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.settings_voice_loading))
                }
            }
        }

        state.voices.isEmpty() -> {
            item(key = "$keyPrefix-empty") {
                Text(
                    text = state.errorMessage
                        ?: stringResource(R.string.settings_voice_none),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        else -> {
            items(
                items = state.voices,
                key = { voice -> "$keyPrefix-${voice.id}" },
            ) { voice ->
                VoiceRow(
                    voice = voice,
                    selected = voice.id == state.selectedVoiceId,
                    onSelect = { onSelectVoice(voice.id) },
                )
            }
        }
    }
}

@Composable
private fun VoiceRow(
    voice: SystemTtsVoice,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = voice.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (voice.requiresNetwork) {
                    stringResource(R.string.settings_voice_network)
                } else {
                    stringResource(R.string.settings_voice_local)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildVoicePreviewText(language: AppLanguage, name: String): String {
    val greeting = when (language.languageCode) {
        "de" -> "Hallo"
        "es" -> "Hola"
        "fr" -> "Bonjour"
        "it" -> "Ciao"
        "ru" -> "Привет"
        "kk" -> "Сәлем"
        "zh" -> "你好"
        else -> "Hello"
    }

    return if (language.languageCode == "zh") {
        "$greeting，$name"
    } else {
        "$greeting, $name"
    }
}

@Composable
private fun <T> SettingsChoice(
    label: String,
    selectedText: String,
    options: List<T>,
    optionText: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedText)
                    Text("▾")
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionText(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}
