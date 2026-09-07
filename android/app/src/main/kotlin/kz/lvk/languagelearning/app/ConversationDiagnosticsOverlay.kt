package kz.lvk.languagelearning.app

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ConversationDiagnosticsOverlay(
    events: List<ModelDiagnosticEvent>,
    modifier: Modifier = Modifier,
) {
    var showLog by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showLog = true },
        modifier = modifier,
    ) {
        Text("Лог модели")
    }

    if (showLog) {
        ConversationDiagnosticsDialog(
            events = events,
            onDismiss = { showLog = false },
        )
    }
}

@Composable
private fun ConversationDiagnosticsDialog(
    events: List<ModelDiagnosticEvent>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val listState = rememberLazyListState()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.scrollToItem(events.lastIndex)
        }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000L)
            copied = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                    Text(
                        text = "Диагностический лог",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Здесь видны USER, итоговый AI TUTOR и каждый сырой вызов локальной модели с точным временем.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText(
                                "Language Learning diagnostics",
                                formatDiagnosticLog(events),
                            ),
                        )
                        copied = true
                    },
                    enabled = events.isNotEmpty(),
                ) {
                    Text(if (copied) "Скопировано" else "Копировать лог")
                }
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(events, key = { it.id }) { event ->
                        DiagnosticEventCard(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticEventCard(event: ModelDiagnosticEvent) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${formatDiagnosticTimestamp(event.timestampEpochMillis)}  ${event.source}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (event.text.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = event.text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

internal fun formatDiagnosticLog(events: List<ModelDiagnosticEvent>): String =
    events.joinToString(separator = "\n\n") { event ->
        buildString {
            append('[')
            append(formatDiagnosticTimestamp(event.timestampEpochMillis))
            append("] ")
            append(event.source)
            if (event.text.isNotBlank()) {
                append("\n")
                append(event.text)
            }
        }
    }

private fun formatDiagnosticTimestamp(timestampEpochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        .format(Date(timestampEpochMillis))
