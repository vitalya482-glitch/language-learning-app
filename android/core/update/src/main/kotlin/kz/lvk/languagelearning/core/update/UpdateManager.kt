package kz.lvk.languagelearning.core.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kz.lvk.languagelearning.core.common.VersionComparator

class UpdateManager(
    private val context: Context,
    private val repository: UpdateRepository,
    private val manifestUrl: String,
    private val currentVersionName: String,
    private val currentVersionCode: Long,
    private val currentPackageName: String,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        cleanupDownloadedUpdatePackages()
    }

    suspend fun checkForUpdates() {
        _state.value = UpdateState.Checking
        runCatching { repository.getManifest(manifestUrl) }
            .onSuccess { manifest ->
                val packageMatches = manifest.packageName == null || manifest.packageName == currentPackageName
                if (!packageMatches) {
                    _state.value = UpdateState.Error("Update package does not match this app build.")
                    return@onSuccess
                }

                val newer = manifest.versionCode?.let { it > currentVersionCode }
                    ?: VersionComparator.isNewer(manifest.latestVersion, currentVersionName)

                _state.value = if (newer) {
                    UpdateState.Available(manifest)
                } else {
                    UpdateState.UpToDate(currentVersionName)
                }
            }
            .onFailure { error ->
                _state.value = UpdateState.Error(error.message ?: "Update check failed")
            }
    }

    suspend fun downloadAndInstall(manifest: UpdateManifest) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.InstallPermissionRequired
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        val updatesDir = updatesDirectory()
            ?: run {
                _state.value = UpdateState.Error("Downloads directory is unavailable")
                return
            }
        val apkFile = File(updatesDir, "language-learning-${manifest.latestVersion}.apk")
        if (apkFile.exists()) apkFile.delete()

        val request = DownloadManager.Request(Uri.parse(manifest.packageInfo.url))
            .setTitle("Language Learning ${manifest.latestVersion}")
            .setDescription("Downloading update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(apkFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val downloadId = downloadManager.enqueue(request)
        _state.value = UpdateState.Downloading(null)

        val completed = waitForDownload(downloadManager, downloadId)
        if (!completed) return

        val expectedHash = manifest.packageInfo.sha256
        if (!expectedHash.isNullOrBlank()) {
            val actualHash = withContext(Dispatchers.IO) { apkFile.sha256() }
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                apkFile.delete()
                _state.value = UpdateState.Error("Downloaded APK checksum does not match the manifest")
                return
            }
        }

        launchInstaller(apkFile)
    }

    private suspend fun waitForDownload(downloadManager: DownloadManager, downloadId: Long): Boolean {
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    _state.value = UpdateState.Error("Download disappeared")
                    return false
                }

                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val progress = if (total > 0) ((downloaded * 100L) / total).toInt() else null

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> return true
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        _state.value = UpdateState.Error("Download failed (reason $reason)")
                        return false
                    }
                    else -> _state.value = UpdateState.Downloading(progress)
                }
            }
            delay(500)
        }
    }

    private fun launchInstaller(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        _state.value = UpdateState.LaunchingInstaller
        context.startActivity(intent)
    }

    private fun cleanupDownloadedUpdatePackages() {
        updatesDirectory()?.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    private fun updatesDirectory(): File? {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        return File(downloadsDir, "updates").apply { mkdirs() }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
