package kz.lvk.languagelearning.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCatalogTest {
    @Test
    fun `catalog contains unique verified model artifacts`() {
        val models = LocalModelCatalog.all

        assertEquals(3, models.size)
        assertEquals(models.size, models.map { it.id }.distinct().size)
        assertEquals(models.size, models.map { it.fileName }.distinct().size)
        models.forEach { model ->
            assertTrue(model.downloadUrl.startsWith("https://"))
            assertTrue(model.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(model.estimatedSizeBytes > 0L)
            assertTrue(model.minimumRamBytes > model.estimatedSizeBytes)
            assertTrue(model.minimumAvailableRamBytes > model.estimatedSizeBytes / 2L)
        }
    }

    @Test
    fun `larger models require more storage and memory`() {
        val models = LocalModelCatalog.all.sortedBy(LocalModelSpec::qualityRank)

        assertEquals(models.map { it.qualityRank }, models.map { it.qualityRank }.distinct())
        assertEquals(models.map { it.estimatedSizeBytes }.sorted(), models.map { it.estimatedSizeBytes })
        assertEquals(models.map { it.minimumRamBytes }.sorted(), models.map { it.minimumRamBytes })
        assertEquals(LocalModelCatalog.Qwen3_1_7B_Q4KM, models.single { it.recommended })
    }
}
