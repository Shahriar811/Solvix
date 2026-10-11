package com.example.task_automator

import com.example.task_automator.helpers.*
import org.junit.Assert.*
import org.junit.Test

class ModelCompatibilityTest {
    private val pixel = DeviceHardware("Google Pixel 7 Pro", "Tensor G2", 35, listOf("arm64-v8a"), true,
        12 * GIB, 8 * GIB, 100 * GIB, 8, false)
    @Test fun pixelPassesAllSixStaticHardwareChecks() {
        assertEquals(6, ModelCatalog.models.size)
        ModelCatalog.models.forEach { assertTrue(it.name, ModelCompatibility.assess(it, pixel).allowed) }
    }
    @Test fun lowRamPhoneRejectsLargerModels() {
        val phone = pixel.copy(totalRamBytes = 7 * GIB)
        assertTrue(ModelCompatibility.assess(ModelCatalog.default, phone).allowed)
        assertFalse(ModelCompatibility.assess(ModelCatalog.find("qwen3-8b"), phone).allowed)
    }
    @Test fun rejects32BitAndOldAndroidBeforeTransfer() {
        assertFalse(ModelCompatibility.assess(ModelCatalog.default, pixel.copy(process64Bit = false)).allowed)
        assertFalse(ModelCompatibility.assess(ModelCatalog.default, pixel.copy(abis = listOf("armeabi-v7a"))).allowed)
        assertFalse(ModelCompatibility.assess(ModelCatalog.default, pixel.copy(apiLevel = 26)).allowed)
    }
    @Test fun storageCheckIncludesCacheButExistingModelsNeedNoNewDownloadSpace() {
        val model = ModelCatalog.find("gemma-e4b")
        val phone = pixel.copy(freeStorageBytes = model.requiredStorage - 1)
        assertFalse(ModelCompatibility.assess(model, phone).allowed)
        assertTrue(ModelCompatibility.assess(model, phone, installed = true).allowed)
    }
    @Test fun lowAvailableMemoryIsExplainedWithoutPretendingItProvesIncompatibility() {
        val result = ModelCompatibility.assess(ModelCatalog.default, pixel.copy(availableRamBytes = GIB, lowMemory = true))
        assertTrue(result.allowed)
        assertTrue(result.cautions.any { it.contains("RAM") })
        assertTrue(result.label.contains("estimate"))
    }
    @Test fun catalogFilesHavePinnedChecksumsAndHonestCapabilities() {
        assertEquals(6, ModelCatalog.models.map { it.id }.distinct().size)
        assertEquals(6, ModelCatalog.models.map { it.fileName }.distinct().size)
        ModelCatalog.models.forEach {
            assertTrue(it.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(it.revision.matches(Regex("[0-9a-f]{40}")))
            assertTrue(it.downloadUrl.startsWith("https://huggingface.co/litert-community/"))
            assertTrue(it.sizeBytes > 0)
        }
        assertTrue(ModelCatalog.default.supportsImages)
        assertTrue(ModelCatalog.find("gemma-e4b").supportsImages)
        assertFalse(ModelCatalog.find("qwen35-4b").supportsImages)
        assertEquals(2048, ModelCatalog.find("qwen3-8b").contextTokens)
    }
}
