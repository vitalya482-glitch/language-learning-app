package kz.lvk.languagelearning.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kz.lvk.languagelearning.core.designsystem.LanguageLearningTheme
import kz.lvk.languagelearning.feature.home.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as LanguageLearningApplication
        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(app.container.updateManager),
            )
            val updateState by mainViewModel.updateState.collectAsStateWithLifecycle()

            LanguageLearningTheme {
                HomeScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE.toLong(),
                    updateState = updateState,
                    onCheckForUpdates = mainViewModel::checkForUpdates,
                    onInstallUpdate = mainViewModel::installUpdate,
                )
            }
        }
    }
}
