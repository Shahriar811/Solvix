package com.example.task_automator.helpers

data class DeviceHardware(
    val name: String,
    val soc: String,
    val apiLevel: Int,
    val abis: List<String>,
    val process64Bit: Boolean,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long,
    val cpuCores: Int,
    val lowMemory: Boolean
)

data class CompatibilityResult(val allowed: Boolean, val label: String, val reasons: List<String>, val cautions: List<String>)

/** Conservative preflight estimates. Kernel/driver support is only confirmed by loading the model. */
object ModelCompatibility {
    fun assess(model: ModelSpec, device: DeviceHardware, installed: Boolean = false): CompatibilityResult {
        val reasons = mutableListOf<String>()
        val cautions = mutableListOf<String>()
        if (!device.process64Bit || "arm64-v8a" !in device.abis) reasons += "Requires a 64-bit ARM Android device."
        if (device.apiLevel < 27) reasons += "Requires Android 8.1 or newer."
        if (device.totalRamBytes < model.minTotalRamGiB * GIB) reasons += "Needs at least ${model.minTotalRamGiB} GiB of detected RAM."
        if (!installed && device.freeStorageBytes < model.requiredStorage) reasons += "Not enough storage for this model and its estimated runtime cache."
        if (device.lowMemory || device.availableRamBytes < (model.estimatedRuntimeRamGiB * GIB).toLong()) {
            cautions += "Available RAM is tight. Close other apps before using it; the current assistant will be unloaded when switching."
        }
        if (model.minTotalRamGiB >= 10) cautions += "High memory use. Experimental on this phone; Android may stop it if memory runs low."
        if (model.id != ModelCatalog.DEFAULT_ID) cautions += "Larger models may answer more slowly on Tensor G2."
        if (model.supportsImages) cautions += "Image support also depends on the GPU driver; text can fall back to CPU."
        return CompatibilityResult(reasons.isEmpty(), if (reasons.isNotEmpty()) "Not compatible now" else "Hardware check passed · estimate", reasons, cautions)
    }
}
