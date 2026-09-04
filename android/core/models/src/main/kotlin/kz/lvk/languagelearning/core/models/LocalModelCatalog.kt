package kz.lvk.languagelearning.core.models

data class LocalModelSpec(
    val id: String,
    val displayName: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val estimatedSizeBytes: Long,
    val sourceLabel: String,
    val licenseLabel: String,
    val recommended: Boolean = false,
)

object LocalModelCatalog {
    val Qwen3_0_6B_Q4KM = LocalModelSpec(
        id = "qwen3-0.6b-q4-k-m",
        displayName = "Qwen3 0.6B · Q4_K_M",
        description = "Минимальная локальная модель для первого AI-теста. Мультиязычная, GGUF.",
        fileName = "qwen3-0.6b-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B.Q4_K_M.gguf?download=true",
        sha256 = "7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d",
        estimatedSizeBytes = 484_000_000L,
        sourceLabel = "Hugging Face · QuantFactory",
        licenseLabel = "Apache 2.0",
        recommended = true,
    )

    val all: List<LocalModelSpec> = listOf(
        Qwen3_0_6B_Q4KM,
    )

    fun byId(id: String): LocalModelSpec? = all.firstOrNull { it.id == id }
}

sealed interface LocalModelStatus {
    data object NotInstalled : LocalModelStatus

    data class Downloading(
        val progressPercent: Int?,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : LocalModelStatus

    data class Installed(
        val localPath: String,
        val sizeBytes: Long,
    ) : LocalModelStatus

    data class Error(
        val message: String,
    ) : LocalModelStatus
}

data class LocalModelEntry(
    val spec: LocalModelSpec,
    val status: LocalModelStatus,
)

data class LocalModelsState(
    val entries: List<LocalModelEntry> = emptyList(),
    val availableBytes: Long = 0L,
)
