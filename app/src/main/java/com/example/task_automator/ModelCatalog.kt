package com.example.task_automator.helpers

import java.util.Locale

const val GIB = 1_073_741_824L

data class ModelSpec(
    val id: String, val name: String, val fileName: String, val sizeBytes: Long,
    val sha256: String, val revision: String, val repo: String,
    val minTotalRamGiB: Int, val estimatedRuntimeRamGiB: Double, val contextTokens: Int,
    val supportsImages: Boolean, val reasoning: Boolean, val disableCache: Boolean, val description: String
) {
    val downloadUrl: String get() = "https://huggingface.co/$repo/resolve/$revision/$fileName"
    val sourceUrl: String get() = "https://huggingface.co/$repo"
    val sizeLabel: String get() = String.format(Locale.US, "%.2f GB", sizeBytes / 1_000_000_000.0)
    val requiredStorage: Long get() = sizeBytes * (if (disableCache) 1 else 2) + 200_000_000L
}

object ModelCatalog {
    const val DEFAULT_ID = "default"
    val models = listOf(
        ModelSpec("default", "Solvix Default", "gemma-4-E2B-it.litertlm", 2588147712, "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1", "litert-community/gemma-4-E2B-it-litert-lm", 6, 3.5, 4096, true, false, false, "Balanced text and photo chat. Your original default assistant."),
        ModelSpec("gemma-e4b", "Gemma 4 E4B", "gemma-4-E4B-it.litertlm", 3659530240, "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", "2eee7ac325f20eb8c9ac1d0e972f7c84663062da", "litert-community/gemma-4-E4B-it-litert-lm", 8, 4.8, 4096, true, false, false, "Clear upgrade for general knowledge, coding and image understanding; slower than Default."),
        ModelSpec("qwen35-4b", "Qwen3.5-4B", "Qwen3.5-4B_mixed_int4.litertlm", 2754365536, "37209c42c4bd8108373305e7f9820a84fd6d8f8a3481549a31c48bd7880b700f", "1f89ed8a25015aa8a488db473901b05a9e1aa3d1", "litert-community/Qwen3.5-4B", 8, 4.5, 4096, false, false, false, "Larger multilingual and coding alternative. This mobile bundle is text-only."),
        ModelSpec("qwen3-8b", "Qwen3-8B", "qwen3_8b_mixed_int4.litertlm", 4887412736, "cb4e6d0de4bbf6656d177812cf0c6a983967dedd17e7f88e84b901c3a9862a42", "71ff705588319d52d374977eff3da4eee0c0d26e", "litert-community/Qwen3-8B", 10, 6.8, 2048, false, false, false, "Larger text reasoning model. Experimental on Pixel 7 Pro; high RAM use and slower answers."),
        ModelSpec("phi4-mini", "Phi-4 Mini", "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm", 3910090752, "7764d4deb53800578307be33039476b38a6c370fff71bedb3c0552563e23ab02", "8cd368be75fdb94d5a6f6f5b40f1ab22a6c2543e", "litert-community/Phi-4-mini-instruct", 8, 5.8, 4096, false, false, false, "Math and instruction-following specialist. Text-only; higher memory use than Default."),
        ModelSpec("nemotron-4b", "Nemotron 3 Nano 4B", "Nemotron-3-Nano-4B_int8.litertlm", 4126697184, "feefdcd55693022ddbdbed7d0aafdd266cac950c6a348d25a8e96b12827ddbda", "77fb42cf0d0c7c8bab5d3e038960fd51d42ef880", "litert-community/Nemotron-3-Nano-4B", 10, 6.5, 4096, false, true, true, "Reasoning specialist. Thinks before answering; high RAM use and longer waits.")
    )
    val default: ModelSpec get() = models.first()
    fun find(id: String): ModelSpec = models.firstOrNull { it.id == id } ?: error("Unknown model")
}
