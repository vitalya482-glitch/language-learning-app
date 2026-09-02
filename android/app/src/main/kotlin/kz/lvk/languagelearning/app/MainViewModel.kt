package kz.lvk.languagelearning.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kz.lvk.languagelearning.core.update.UpdateManager
import kz.lvk.languagelearning.core.update.UpdateManifest

class MainViewModel(
    private val updateManager: UpdateManager,
) : ViewModel() {
    val updateState = updateManager.state

    fun checkForUpdates() {
        viewModelScope.launch { updateManager.checkForUpdates() }
    }

    fun installUpdate(manifest: UpdateManifest) {
        viewModelScope.launch { updateManager.downloadAndInstall(manifest) }
    }

    class Factory(
        private val updateManager: UpdateManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(updateManager) as T
    }
}
