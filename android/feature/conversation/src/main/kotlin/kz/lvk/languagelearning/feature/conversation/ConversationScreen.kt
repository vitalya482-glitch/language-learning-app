package kz.lvk.languagelearning.feature.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.delay
import kz.lvk.languagelearning.core.speech.AndroidOnDeviceSpeechRecognizer
import kz.lvk.languagelearning.core.speech.AndroidSystemTextToSpeech
import kz.lvk.languagelearning.core.speech.SpeechLanguage
import kz.lvk.languagelearning.core.speech.SpeechLanguages
import kz.lvk.languagelearning.core.speech.SpeechRecognitionState

@Composable
fun ConversationScreen(
    state: ConversationUiState,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onRetryEngine: () -> Unit,
    speechLanguage: SpeechLanguage = SpeechLanguages.English,
    nativeSpeechLanguage: SpeechLanguage = speechLanguage,
    ttsVoiceId: String? = null,
) {
    var input by remember { mutableStateOf("") }
    var microphonePermissionDenied by remember { mutableStateOf(false) }
    var useNativeSpeechLanguage by remember(nativeSpeechLanguage.tag, speechLanguage.tag) {
        mutableStateOf(false)
    }
    var generationElapsedMs by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val speechRecognizer = remember(context) {
        AndroidOnDeviceSpeechRecognizer(context.applicationContext)
    }
    val systemTts = remember(context) {
        AndroidSystemTextToSpeech(context.applicationContext)
    }
    val speechState by speechRecognizer.state.collectAsStateWithLifecycle()
    val ttsState by systemTts.state.collectAsStateWithLifecycle()
    val activeSpeechLanguage = if (useNativeSpeechLanguage) {
        nativeSpeechLanguage
    } else {
        speechLanguage
    }
    val activeSpeechLanguageDisplayName = activeSpeechLanguage.displayName()

    DisposableEffect(speechRecognizer, systemTts) {
        onDispose {
            speechRecognizer.close()
            systemTts.close()
        }
    }

    LaunchedEffect(speechLanguage.tag, ttsVoiceId) {
        systemTts.prepare(speechLanguage, ttsVoiceId)
    }

    LaunchedEffect(speechState.finalText) {
        if (speechState.finalText.isNotBlank()) {
            input = speechState.finalText
        }
    }

    LaunchedEffect(state.isGenerating) {
        if (state.isGenerating) {
            val startedAt = SystemClock.elapsedRealtime()
            while (true) {
                generationElapsedMs = SystemClock.elapsedRealtime() - startedAt
                delay(250)
            }
        } else {
            generationElapsedMs = 0L
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        microphonePermissionDenied = !granted
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.conversation_back))
                }
                Text(
                    text = stringResource(R.string.conversation_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            text = if (state.isEngineReady) {
                                stringResource(R.string.conversation_ready)
                            } else {
                                stringResource(R.string.conversation_loading)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    ConversationMessageBubble(message)
                }
                if (state.isGenerating) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Text(
                                text = stringResource(
                                    R.string.conversation_generating,
                                    formatRecordingDuration(generationElapsedMs),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            state.errorMessage?.let { error ->
                Text(
                    text = stringResource(R.string.conversation_error, error),
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetryEngine) {
                    Text(stringResource(R.string.conversation_retry))
                }
            }

            SpeechInputControl(
                speechState = speechState,
                languageDisplayName = activeSpeechLanguageDisplayName,
                nativeLanguageDisplayName = nativeSpeechLanguage.displayName(),
                targetLanguageDisplayName = speechLanguage.displayName(),
                useNativeLanguage = useNativeSpeechLanguage,
                showLanguageSelector = nativeSpeechLanguage.tag != speechLanguage.tag,
                onUseNativeLanguage = { useNativeSpeechLanguage = true },
                onUseTargetLanguage = { useNativeSpeechLanguage = false },
                microphonePermissionDenied = microphonePermissionDenied,
                onRequestPermission = {
                    microphonePermissionDenied = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                hasMicrophonePermission = {
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                },
                onStartListening = {
                    microphonePermissionDenied = false
                    systemTts.stop()
                    speechRecognizer.startListening(activeSpeechLanguage)
                },
                onStopListening = speechRecognizer::stopListening,
                onDownloadLanguageModel = {
                    speechRecognizer.requestLanguageModelDownload(activeSpeechLanguage)
                },
            )

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isEngineReady && !state.isGenerating,
                label = { Text(stringResource(R.string.conversation_input_label)) },
                minLines = 2,
                maxLines = 4,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (ttsState.isSpeaking) {
                        systemTts.stop()
                    } else {
                        systemTts.speak(input, speechLanguage)
                    }
                },
                enabled = input.isNotBlank() && ttsState.isReady,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (ttsState.isSpeaking) {
                        stringResource(R.string.conversation_stop_speaking)
                    } else {
                        stringResource(R.string.conversation_speak_text)
                    },
                )
            }
            ttsState.errorMessage?.let { ttsError ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.conversation_tts_error, ttsError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    onSendMessage(input)
                    input = ""
                },
                enabled = input.isNotBlank() && state.isEngineReady && !state.isGenerating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.conversation_send))
            }
        }
    }
}

@Composable
private fun SpeechInputControl(
    speechState: SpeechRecognitionState,
    languageDisplayName: String,
    nativeLanguageDisplayName: String,
    targetLanguageDisplayName: String,
    useNativeLanguage: Boolean,
    showLanguageSelector: Boolean,
    onUseNativeLanguage: () -> Unit,
    onUseTargetLanguage: () -> Unit,
    microphonePermissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    hasMicrophonePermission: () -> Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onDownloadLanguageModel: () -> Unit,
) {
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(speechState.isListening, speechState.recordingStartedAtMs) {
        val startedAt = speechState.recordingStartedAtMs
        if (speechState.isListening && startedAt != null) {
            while (true) {
                recordingElapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
                delay(100)
            }
        } else {
            recordingElapsedMs = 0L
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showLanguageSelector) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.speech_input_language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onUseNativeLanguage,
                    enabled = !speechState.isListening && !speechState.isFinalizing,
                ) {
                    Text(
                        if (useNativeLanguage) {
                            "✓ $nativeLanguageDisplayName"
                        } else {
                            nativeLanguageDisplayName
                        },
                    )
                }
                TextButton(
                    onClick = onUseTargetLanguage,
                    enabled = !speechState.isListening && !speechState.isFinalizing,
                ) {
                    Text(
                        if (!useNativeLanguage) {
                            "✓ $targetLanguageDisplayName"
                        } else {
                            targetLanguageDisplayName
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Surface(
            shape = CircleShape,
            color = if (speechState.isListening) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (speechState.isListening) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier
                .size(104.dp)
                .pointerInput(speechState.isAvailable, useNativeLanguage) {
                    detectTapGestures(
                        onPress = {
                            if (speechState.isAvailable) {
                                if (hasMicrophonePermission()) {
                                    onStartListening()
                                } else {
                                    onRequestPermission()
                                }
                            }
                            tryAwaitRelease()
                            onStopListening()
                        },
                    )
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (speechState.isListening) {
                        stringResource(R.string.speech_listening_button)
                    } else {
                        stringResource(R.string.speech_talk_button)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(122.dp),
            contentAlignment = Alignment.Center,
        ) {
            val speechError = speechState.errorMessage
            when {
                !speechState.isAvailable -> Text(
                    text = stringResource(R.string.speech_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                microphonePermissionDenied -> Text(
                    text = stringResource(R.string.speech_permission_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                speechState.isListening -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpeechWaveform(
                            levels = speechState.levelHistory,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = formatRecordingDuration(recordingElapsedMs),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (speechState.partialText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = speechState.partialText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                }

                speechState.isFinalizing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.speech_recognizing),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                speechState.languageModelDownloadRequired -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            R.string.speech_language_model_missing,
                            languageDisplayName,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onDownloadLanguageModel) {
                        Text(
                            stringResource(
                                R.string.speech_download_language_model,
                                languageDisplayName,
                            ),
                        )
                    }
                }

                speechState.languageModelDownloadRequested -> Text(
                    text = stringResource(
                        R.string.speech_language_model_download_requested,
                        languageDisplayName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                speechState.errorCode == SpeechRecognizer.ERROR_NO_MATCH -> Text(
                    text = stringResource(R.string.speech_no_match, languageDisplayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                speechError != null -> Text(
                    text = speechError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                else -> Text(
                    text = stringResource(R.string.speech_hold_to_talk),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpeechWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
) {
    val waveformColor = MaterialTheme.colorScheme.primary
    val barCount = 48

    Canvas(modifier = modifier) {
        val samples = if (levels.size >= barCount) {
            levels.takeLast(barCount)
        } else {
            List(barCount - levels.size) { 0f } + levels
        }

        val slotWidth = size.width / barCount
        val strokeWidth = (slotWidth * 0.42f).coerceAtLeast(2.dp.toPx())
        val minimumHeight = 4.dp.toPx()
        val centerY = size.height / 2f

        samples.forEachIndexed { index, level ->
            val barHeight = (minimumHeight + (size.height - minimumHeight) * level)
                .coerceIn(minimumHeight, size.height)
            val x = slotWidth * index + slotWidth / 2f

            drawLine(
                color = waveformColor,
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatRecordingDuration(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
private fun ConversationMessageBubble(message: ConversationMessage) {
    val isUser = message.role == ConversationRole.User
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
