package kz.lvk.languagelearning.core.update

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val progressPercent: Int?) : UpdateState
    data object InstallPermissionRequired : UpdateState
    data object LaunchingInstaller : UpdateState
    data class Error(val message: String) : UpdateState
}
