package kz.lvk.languagelearning.feature.models

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kz.lvk.languagelearning.core.designsystem.components.LvkOutlinedButton
import kz.lvk.languagelearning.core.designsystem.components.LvkPrimaryButton
import kz.lvk.languagelearning.core.models.LocalModelEntry
import kz.lvk.languagelearning.core.models.LocalModelStatus
import kz.lvk.languagelearning.core.models.LocalModelsState

@Composable
fun LocalModelsScreen(
    state: LocalModelsState,
    onBack: () -> Unit,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.models_back))
                    }
                    Text(
                        text = stringResource(R.string.models_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.models_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.models_free_space,
                        formatBytes(state.availableBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
            }

            items(
                items = state.entries,
                key = { it.spec.id },
            ) { entry ->
                ModelEntry(
                    entry = entry,
                    onDownload = { onDownload(entry.spec.id) },
                    onDelete = { onDelete(entry.spec.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ModelEntry(
    entry: LocalModelEntry,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val spec = entry.spec

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (spec.recommended) {
                stringResource(R.string.models_recommended_name, spec.displayName)
            } else {
                spec.displayName
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = spec.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.models_metadata,
                formatBytes(spec.estimatedSizeBytes),
                spec.sourceLabel,
                spec.licenseLabel,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val status = entry.status) {
            LocalModelStatus.NotInstalled -> {
                Text(
                    text = stringResource(R.string.models_not_installed),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LvkPrimaryButton(
                    text = stringResource(R.string.models_download),
                    onClick = onDownload,
                )
            }

            is LocalModelStatus.Downloading -> {
                val progress = status.progressPercent
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = if (progress == 100) {
                        stringResource(R.string.models_verifying)
                    } else {
                        stringResource(
                            R.string.models_downloading,
                            progress?.let { "$it%" } ?: "…",
                            formatBytes(status.downloadedBytes),
                            formatBytes(status.totalBytes),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                LvkOutlinedButton(
                    text = stringResource(R.string.models_cancel_delete),
                    onClick = onDelete,
                )
            }

            is LocalModelStatus.Installed -> {
                Text(
                    text = stringResource(
                        R.string.models_installed,
                        formatBytes(status.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.models_inference_next),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LvkOutlinedButton(
                    text = stringResource(R.string.models_delete),
                    onClick = onDelete,
                )
            }

            is LocalModelStatus.Error -> {
                Text(
                    text = status.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                LvkPrimaryButton(
                    text = stringResource(R.string.models_retry),
                    onClick = onDownload,
                )
                LvkOutlinedButton(
                    text = stringResource(R.string.models_delete_partial),
                    onClick = onDelete,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000.0) {
        String.format(Locale.US, "%.1f GB", mb / 1000.0)
    } else {
        String.format(Locale.US, "%.0f MB", mb)
    }
}
