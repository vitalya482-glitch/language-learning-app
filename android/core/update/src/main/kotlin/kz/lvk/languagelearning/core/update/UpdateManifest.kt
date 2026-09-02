package kz.lvk.languagelearning.core.update

data class UpdateManifest(
    val schemaVersion: Int,
    val appId: String,
    val name: String,
    val latestVersion: String,
    val versionCode: Long?,
    val channel: String,
    val mandatory: Boolean,
    val packageName: String?,
    val packageInfo: UpdatePackage,
    val notes: List<String>,
)

data class UpdatePackage(
    val url: String,
    val sha256: String?,
    val size: Long?,
)
