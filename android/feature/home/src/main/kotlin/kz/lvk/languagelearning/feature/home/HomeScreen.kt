package kz.lvk.languagelearning.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kz.lvk.languagelearning.core.designsystem.components.LvkOutlinedButton
import kz.lvk.languagelearning.core.designsystem.components.LvkPrimaryButton
import kz.lvk.languagelearning.core.update.UpdateManifest
import kz.lvk.languagelearning.core.update.UpdateState

@Composable
fun HomeScreen(
    versionName: String,
    versionCode: Long,
    updateState: UpdateState,
    onStartLearning: () -> Unit,
    onSettings: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: (UpdateManifest) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.home_version, versionName, versionCode),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.dev_build_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            LvkPrimaryButton(
                text = stringResource(R.string.start_learning),
                onClick = onStartLearning,
            )
            Spacer(Modifier.height(12.dp))
            LvkPrimaryButton(
                text = stringResource(R.string.history),
                onClick = {},
            )
            Spacer(Modifier.height(12.dp))
            LvkPrimaryButton(
                text = stringResource(R.string.statistics),
                onClick = {},
            )
            Spacer(Modifier.height(12.dp))
            LvkPrimaryButton(
                text = stringResource(R.string.settings),
                onClick = onSettings,
            )
            Spacer(Modifier.height(24.dp))

            when (updateState) {
                UpdateState.Idle -> Unit
                UpdateState.Checking -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.checking_updates))
                }
                is UpdateState.UpToDate -> Text(stringResource(R.string.up_to_date))
                is UpdateState.Available -> {
                    Text(stringResource(R.string.update_available, updateState.manifest.latestVersion))
                    Spacer(Modifier.height(12.dp))
                    LvkPrimaryButton(
                        text = stringResource(R.string.install_update),
                        onClick = { onInstallUpdate(updateState.manifest) },
                    )
                }
                is UpdateState.Downloading -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    val progress = updateState.progressPercent?.let { "$it%" } ?: ""
                    Text(stringResource(R.string.downloading, progress))
                }
                UpdateState.InstallPermissionRequired -> Text(stringResource(R.string.permission_required))
                UpdateState.LaunchingInstaller -> Text(stringResource(R.string.launching_installer))
                is UpdateState.Error -> Text(
                    text = stringResource(R.string.update_error, updateState.message),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))
            LvkOutlinedButton(
                text = stringResource(R.string.check_updates),
                enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
                onClick = onCheckForUpdates,
            )
        }
    }
}
