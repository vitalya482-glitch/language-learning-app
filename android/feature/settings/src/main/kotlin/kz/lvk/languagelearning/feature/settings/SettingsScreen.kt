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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.lvk.languagelearning.core.settings.AppLanguage
import kz.lvk.languagelearning.core.settings.AppSettings
import kz.lvk.languagelearning.core.settings.DeviceDisplayName
import kz.lvk.languagelearning.core.settings.LanguageCatalog
import kz.lvk.languagelearning.core.settings.LearningLevel
import kz.lvk.languagelearning.core.speech.AndroidSystemTextToSpeech
import kz.lvk.languagelearning.core.speech.SpeechLanguage

@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    onBack: () -> Unit,
    onNativeLanguageChange: (AppLanguage) -> Unit,
    onTargetLanguageChange: (AppLanguage) -> Unit,
    onLearningLevelChange: (LearningLevel) -> Unit,
    onTtsVoiceChange: (AppLanguage, String?) -> Unit,
) {
    val context = LocalContext.current
    val systemTts = remember(context) {
        AndroidSystemTextToSpeech(context.applicationContext)
    }
    val ttsState by systemTts.state.collectAsStateWithLifecycle()
    val targetLanguage = appSettings.targetLanguage
    val speechLanguage = remember(targetLanguage.tag) {
        SpeechLanguage(targetLanguage.tag)
    }
    val preferredVoiceId = appSettings.ttsVoiceIdsByLanguage[targetLanguage.tag]
    val previewName = remember(context, appSettings.userDisplayName) {
        appSettings.userDisplayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DeviceDisplayName.resolve(context.applicationContext)
    }
    val previewText = remember(targetLanguage.tag, previewName) {
        buildVoicePreviewText(targetLanguage, previewName)
    }

    LaunchedEffect(targetLanguage.tag, preferredVoiceId) {
        systemTts.prepare(speechLanguage, preferredVoiceId)
    }

    DisposableEffect(systemTts) {
        onDispose { systemTts.close() }
    }

    fun selectAndPreviewVoice(voiceId: String) {
        systemTts.selectVoice(speechLanguage, voiceId)
        onTtsVoiceChange(targetLanguage, voiceId)
        systemTts.speak(previewText, speechLanguage)
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
                    selectedText = appSettings.nativeLanguage.displayName(),
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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.settings_voice_language,
                        targetLanguage.displayName(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
            }

            when {
                !ttsState.isReady && ttsState.errorMessage == null -> {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.settings_voice_loading))
                        }
                    }
                }

                ttsState.voices.isEmpty() -> {
                    item {
                        Text(
                            text = ttsState.errorMessage
                                ?: stringResource(R.string.settings_voice_none),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                else -> {
                    items(
                        items = ttsState.voices,
                        key = { it.id },
                    ) { voice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectAndPreviewVoice(voice.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = voice.id == ttsState.selectedVoiceId,
                                onClick = { selectAndPreviewVoice(voice.id) },
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
                }
            }
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
