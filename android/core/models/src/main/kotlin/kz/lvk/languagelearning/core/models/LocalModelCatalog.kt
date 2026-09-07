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
    val minimumRamBytes: Long,
    val minimumAvailableRamBytes: Long,
    val qualityRank: Int,
    val recommended: Boolean = false,
)

object LocalModelCatalog {
    val Qwen3_0_6B_Q4KM = LocalModelSpec(
        id = "qwen3-0.6b-q4-k-m",
        displayName = "Qwen3 0.6B · минимальная",
        description = "Минимальная локальная модель для первого AI-теста. Мультиязычная, GGUF.",
        fileName = "qwen3-0.6b-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B.Q4_K_M.gguf?download=true",
        sha256 = "7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d",
        estimatedSizeBytes = 484_220_000L,
        sourceLabel = "Hugging Face · QuantFactory",
        licenseLabel = "Apache 2.0",
        minimumRamBytes = 1_600_000_000L,
        minimumAvailableRamBytes = 800_000_000L,
        qualityRank = 1,
    )

    val Qwen3_1_7B_Q4KM = LocalModelSpec(
        id = "qwen3-1.7b-q4-k-m",
        displayName = "Qwen3 1.7B · средняя",
        description = "Заметно лучше держит контекст и выполняет инструкции. Оптимальный баланс качества и скорости.",
        fileName = "qwen3-1.7b-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Qwen_Qwen3-1.7B-GGUF/resolve/main/Qwen_Qwen3-1.7B-Q4_K_M.gguf?download=true",
        sha256 = "72c5c3cb38fa32d5256e2fe30d03e7a64c6c79e668ad84057e3bd66e250b24fb",
        estimatedSizeBytes = 1_282_439_584L,
        sourceLabel = "Hugging Face · bartowski / Qwen",
        licenseLabel = "Apache 2.0",
        minimumRamBytes = 3_200_000_000L,
        minimumAvailableRamBytes = 1_800_000_000L,
        qualityRank = 2,
        recommended = true,
    )

    val Qwen3_4B_Q4KM = LocalModelSpec(
        id = "qwen3-4b-q4-k-m",
        displayName = "Qwen3 4B · максимальная",
        description = "Лучшее качество диалога из доступных вариантов, но отвечает медленнее и требует больше памяти.",
        fileName = "qwen3-4b-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true",
        sha256 = "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",
        estimatedSizeBytes = 2_497_280_256L,
        sourceLabel = "Hugging Face · Qwen",
        licenseLabel = "Apache 2.0",
        minimumRamBytes = 6_800_000_000L,
        minimumAvailableRamBytes = 3_200_000_000L,
        qualityRank = 3,
    )

    val all: List<LocalModelSpec> = listOf(
        Qwen3_0_6B_Q4KM,
        Qwen3_1_7B_Q4KM,
        Qwen3_4B_Q4KM,
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
    val isMemoryCompatible: Boolean,
    val canLoadNow: Boolean,
)

data class LocalModelsState(
    val entries: List<LocalModelEntry> = emptyList(),
    val availableBytes: Long = 0L,
    val totalRamBytes: Long = 0L,
    val availableRamBytes: Long = 0L,
    val selectedModelId: String? = null,
)
