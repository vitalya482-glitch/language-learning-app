package kz.lvk.languagelearning.feature.conversation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.lvk.languagelearning.core.speech.AndroidOnDeviceSpeechRecognizer

@Composable
fun ConversationScreen(
    state: ConversationUiState,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onRetryEngine: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var microphonePermissionDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val speechRecognizer = remember(context) {
        AndroidOnDeviceSpeechRecognizer(context.applicationContext)
    }
    val speechState by speechRecognizer.state.collectAsStateWithLifecycle()

    DisposableEffect(speechRecognizer) {
        onDispose { speechRecognizer.close() }
    }

    LaunchedEffect(speechState.finalText) {
        if (speechState.finalText.isNotBlank()) {
            input = speechState.finalText
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
                        CircularProgressIndicator()
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
                    speechRecognizer.startListening(languageTag = "en-US")
                },
                onStopListening = speechRecognizer::stopListening,
                onDownloadLanguageModel = {
                    speechRecognizer.requestLanguageModelDownload(languageTag = "en-US")
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
    speechState: kz.lvk.languagelearning.core.speech.SpeechRecognitionState,
    microphonePermissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    hasMicrophonePermission: () -> Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onDownloadLanguageModel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                .pointerInput(speechState.isAvailable) {
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
                .height(104.dp),
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VoiceLevelTrack(speechState.rmsDb)
                    if (speechState.partialText.isNotBlank()) {
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
                        text = stringResource(R.string.speech_language_model_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onDownloadLanguageModel) {
                        Text(stringResource(R.string.speech_download_language_model))
                    }
                }

                speechState.languageModelDownloadRequested -> Text(
                    text = stringResource(R.string.speech_language_model_download_requested),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun VoiceLevelTrack(rmsDb: Float) {
    val level = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
    val multipliers = listOf(0.45f, 0.75f, 1f, 0.65f, 0.9f, 0.55f, 0.8f, 0.5f, 0.7f)

    Row(
        modifier = Modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        multipliers.forEach { multiplier ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((6f + 20f * level * multiplier).dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
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
