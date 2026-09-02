package kz.lvk.languagelearning.feature.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun ConversationScreen(
    state: ConversationUiState,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onRetryEngine: () -> Unit,
) {
    var input by remember { mutableStateOf("") }

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
