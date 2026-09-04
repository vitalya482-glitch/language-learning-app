package kz.lvk.languagelearning.core.models

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val modelsDir = (appContext.getExternalFilesDir(null) ?: appContext.filesDir)
        .resolve("models")
        .apply { mkdirs() }

    private val statuses = mutableMapOf<String, LocalModelStatus>()
    private val _state = MutableStateFlow(LocalModelsState())
    val state: StateFlow<LocalModelsState> = _state.asStateFlow()

    init {
        LocalModelCatalog.all.forEach { spec ->
            val finalFile = finalFile(spec)
            statuses[spec.id] = if (finalFile.isFile) {
                LocalModelStatus.Installed(finalFile.absolutePath, finalFile.length())
            } else {
                LocalModelStatus.NotInstalled
            }
        }
        publishState()
        resumeKnownDownloads()
    }

    fun download(modelId: String) {
        val spec = LocalModelCatalog.byId(modelId) ?: return
        if (statuses[modelId] is LocalModelStatus.Downloading) return
        if (finalFile(spec).isFile) {
            refresh()
            return
        }

        val available = availableBytes()
        val required = spec.estimatedSizeBytes + DOWNLOAD_HEADROOM_BYTES
        if (available > 0L && available < required) {
            statuses[modelId] = LocalModelStatus.Error(
                "Недостаточно свободного места. Нужно примерно ${humanBytes(required)}.",
            )
            publishState()
            return
        }

        val partial = partialFile(spec)
        if (partial.exists()) partial.delete()

        val request = DownloadManager.Request(Uri.parse(spec.downloadUrl))
            .setTitle(spec.displayName)
            .setDescription("Скачивание локальной AI-модели")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(partial))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val downloadId = downloadManager.enqueue(request)
        saveDownloadId(spec.id, downloadId)
        statuses[spec.id] = LocalModelStatus.Downloading(
            progressPercent = null,
            downloadedBytes = 0L,
            totalBytes = spec.estimatedSizeBytes,
        )
        publishState()

        scope.launch { monitorDownload(spec, downloadId) }
    }

    fun delete(modelId: String) {
        val spec = LocalModelCatalog.byId(modelId) ?: return
        val downloadId = savedDownloadId(spec.id)
        if (downloadId != null) {
            downloadManager.remove(downloadId)
            clearDownloadId(spec.id)
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                finalFile(spec).delete()
                partialFile(spec).delete()
            }
            statuses[spec.id] = LocalModelStatus.NotInstalled
            publishState()
        }
    }

    fun refresh() {
        LocalModelCatalog.all.forEach { spec ->
            if (statuses[spec.id] !is LocalModelStatus.Downloading) {
                val file = finalFile(spec)
                statuses[spec.id] = if (file.isFile) {
                    LocalModelStatus.Installed(file.absolutePath, file.length())
                } else if (statuses[spec.id] is LocalModelStatus.Error) {
                    statuses.getValue(spec.id)
                } else {
                    LocalModelStatus.NotInstalled
                }
            }
        }
        publishState()
    }

    fun installedPath(modelId: String): String? {
        return (statuses[modelId] as? LocalModelStatus.Installed)?.localPath
    }

    private fun resumeKnownDownloads() {
        LocalModelCatalog.all.forEach { spec ->
            val downloadId = savedDownloadId(spec.id) ?: return@forEach
            if (finalFile(spec).isFile) {
                clearDownloadId(spec.id)
                return@forEach
            }
            statuses[spec.id] = LocalModelStatus.Downloading(
                progressPercent = null,
                downloadedBytes = 0L,
                totalBytes = spec.estimatedSizeBytes,
            )
            scope.launch { monitorDownload(spec, downloadId) }
        }
        publishState()
    }

    private suspend fun monitorDownload(spec: LocalModelSpec, downloadId: Long) {
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val result = downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use DownloadSnapshot.Missing

                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                )
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                )
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                DownloadSnapshot.Found(status, downloaded, total, reason)
            } ?: DownloadSnapshot.Missing

            when (result) {
                DownloadSnapshot.Missing -> {
                    clearDownloadId(spec.id)
                    statuses[spec.id] = LocalModelStatus.Error("Загрузка модели исчезла из DownloadManager.")
                    publishState()
                    return
                }

                is DownloadSnapshot.Found -> when (result.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        clearDownloadId(spec.id)
                        verifyAndFinalize(spec)
                        return
                    }

                    DownloadManager.STATUS_FAILED -> {
                        clearDownloadId(spec.id)
                        statuses[spec.id] = LocalModelStatus.Error(
                            "Не удалось скачать модель (код ${result.reason}).",
                        )
                        publishState()
                        return
                    }

                    else -> {
                        val total = result.total.takeIf { it > 0L } ?: spec.estimatedSizeBytes
                        val progress = total.takeIf { it > 0L }?.let {
                            ((result.downloaded * 100L) / it).toInt().coerceIn(0, 100)
                        }
                        statuses[spec.id] = LocalModelStatus.Downloading(
                            progressPercent = progress,
                            downloadedBytes = result.downloaded.coerceAtLeast(0L),
                            totalBytes = total,
                        )
                        publishState()
                    }
                }
            }

            delay(500)
        }
    }

    private suspend fun verifyAndFinalize(spec: LocalModelSpec) {
        val partial = partialFile(spec)
        statuses[spec.id] = LocalModelStatus.Downloading(
            progressPercent = 100,
            downloadedBytes = partial.length(),
            totalBytes = partial.length(),
        )
        publishState()

        val result: Result<File> = withContext(Dispatchers.IO) {
            if (!partial.isFile) {
                return@withContext Result.failure(IllegalStateException("Скачанный файл модели не найден."))
            }

            val actualHash = partial.sha256()
            if (!actualHash.equals(spec.sha256, ignoreCase = true)) {
                partial.delete()
                return@withContext Result.failure(IllegalStateException("SHA-256 модели не совпадает."))
            }

            val final = finalFile(spec)
            if (final.exists()) final.delete()
            if (!partial.renameTo(final)) {
                partial.copyTo(final, overwrite = true)
                partial.delete()
            }
            Result.success(final)
        }

        result.onSuccess { file ->
            statuses[spec.id] = LocalModelStatus.Installed(
                localPath = file.absolutePath,
                sizeBytes = file.length(),
            )
        }.onFailure { error ->
            statuses[spec.id] = LocalModelStatus.Error(
                error.message ?: "Не удалось проверить локальную модель.",
            )
        }
        publishState()
    }

    private fun publishState() {
        _state.value = LocalModelsState(
            entries = LocalModelCatalog.all.map { spec ->
                LocalModelEntry(
                    spec = spec,
                    status = statuses[spec.id] ?: LocalModelStatus.NotInstalled,
                )
            },
            availableBytes = availableBytes(),
        )
    }

    private fun availableBytes(): Long = runCatching {
        StatFs(modelsDir.absolutePath).availableBytes
    }.getOrDefault(0L)

    private fun finalFile(spec: LocalModelSpec): File = modelsDir.resolve(spec.fileName)

    private fun partialFile(spec: LocalModelSpec): File = modelsDir.resolve("${spec.fileName}.download")

    private fun saveDownloadId(modelId: String, downloadId: Long) {
        preferences.edit().putLong(downloadKey(modelId), downloadId).apply()
    }

    private fun savedDownloadId(modelId: String): Long? {
        val key = downloadKey(modelId)
        if (!preferences.contains(key)) return null
        return preferences.getLong(key, -1L).takeIf { it >= 0L }
    }

    private fun clearDownloadId(modelId: String) {
        preferences.edit().remove(downloadKey(modelId)).apply()
    }

    private fun downloadKey(modelId: String): String = "download_id_$modelId"

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

    private sealed interface DownloadSnapshot {
        data object Missing : DownloadSnapshot
        data class Found(
            val status: Int,
            val downloaded: Long,
            val total: Long,
            val reason: Int,
        ) : DownloadSnapshot
    }

    private companion object {
        const val PREFERENCES_NAME = "language_learning_local_models"
        const val DOWNLOAD_HEADROOM_BYTES = 96L * 1024L * 1024L

        fun humanBytes(bytes: Long): String {
            val mb = bytes / 1_000_000.0
            return if (mb >= 1000.0) {
                String.format(Locale.US, "%.1f ГБ", mb / 1000.0)
            } else {
                String.format(Locale.US, "%.0f МБ", mb)
            }
        }
    }
}
