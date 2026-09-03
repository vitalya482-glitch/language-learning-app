package kz.lvk.languagelearning.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.lvk.languagelearning.core.speech.AndroidSystemTextToSpeech
import kz.lvk.languagelearning.core.speech.SpeechLanguage
import kz.lvk.languagelearning.core.speech.SpeechLanguages

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    speechLanguage: SpeechLanguage = SpeechLanguages.English,
) {
    val context = LocalContext.current
    val systemTts = remember(context) {
        AndroidSystemTextToSpeech(context.applicationContext)
    }
    val ttsState by systemTts.state.collectAsStateWithLifecycle()
    val languageName = speechLanguage.displayName()

    LaunchedEffect(speechLanguage.tag) {
        systemTts.prepare(speechLanguage)
    }

    DisposableEffect(systemTts) {
        onDispose { systemTts.close() }
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
                    text = stringResource(R.string.settings_voice_language, languageName),
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
                                .clickable {
                                    systemTts.selectVoice(speechLanguage, voice.id)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = voice.id == ttsState.selectedVoiceId,
                                onClick = {
                                    systemTts.selectVoice(speechLanguage, voice.id)
                                },
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
