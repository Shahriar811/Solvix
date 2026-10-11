# Offline model library

Open **Settings → Models**. The original assistant remains **Solvix Default**. Expand a model to see its capabilities, hardware estimate, source and license. Choose **Download** or **Import**, review the preflight details, and install it. Installed models have a **Use model** action.

## Catalog

All URLs use pinned revisions and each complete file is checked against its exact size and SHA-256. Files are public and do not need a Hugging Face login. These are the supported generic mobile bundles; imports of GGUF, old `.task`, processor-specific or different model files are rejected.

| Model | Verified file size | Input | Practical note |
| --- | --- | --- | --- |
| [Solvix Default](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) | 2.59 GB | Text, photos | Original balanced assistant; existing installed files are retained. |
| [Gemma 4 E4B](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm) | 3.66 GB | Text, photos | Clear general upgrade over Default in Google's published benchmarks; slower. |
| [Qwen3.5-4B Mixed INT4](https://huggingface.co/litert-community/Qwen3.5-4B) | 2.75 GB | Text | Multilingual/coding alternative. This export excludes the upstream vision tower. |
| [Qwen3-8B Mixed INT4](https://huggingface.co/litert-community/Qwen3-8B) | 4.89 GB | Text | High memory use; experimental on Pixel 7 Pro. Export supports a 2,048-token context. |
| [Phi-4 Mini INT8](https://huggingface.co/litert-community/Phi-4-mini-instruct) | 3.91 GB | Text | Math/instruction-following alternative. Source reports about 5.3 GB peak CPU RSS on S24 Ultra. |
| [Nemotron 3 Nano 4B INT8](https://huggingface.co/litert-community/Nemotron-3-Nano-4B) | 4.13 GB | Text | Reasoning specialist with high memory use. CPU selected; persistent compile cache disabled for this bundle. |

Larger parameter counts do not prove that every answer is better than Default. E4B is a direct same-family upgrade; the others have different strengths, training and quantization. These five alternatives have not been benchmarked in Solvix on the Pixel 7 Pro.

## Device checks

Solvix reads the phone model, SoC, Android API, supported ABIs, process bitness, CPU core count, total/available RAM and free app storage. No phone identity or location permission is required.

Preflight blocks 32-bit/non-ARM64 processes, Android versions below API 27, insufficient total RAM and insufficient installation storage. Minimum detected RAM is 6 GiB for Default, 8 GiB for E4B/Qwen3.5/Phi, and 10 GiB for Qwen3-8B/Nemotron. RAM requirements are conservative estimates, not measurements on every phone. Available RAM is shown separately with a caution because the current model will be unloaded when switching.

Installation reserves the model size, an estimated same-size runtime cache for cached profiles, and 200 MB overhead. Nemotron does not reserve that compile cache. Imports retain the original source file; its already-used storage is reflected in the phone's free-space reading.

Google lists the [Pixel 7 Pro](https://support.google.com/pixelphone/answer/7158570?hl=en) with 12 GB RAM and Tensor G2. It should pass the catalog's total-RAM/ARM64 checks, but current free memory, driver/kernel support, context length and thermal state can still prevent a model from running. A passed preflight means **estimated hardware compatibility**, not a guarantee. Heavy models may be slow or stopped by Android under memory pressure.

## Switching and storage

Only one model engine is loaded. Selection is saved only after the new engine initializes. If that fails, Solvix attempts to reload the previous model. Models are kept in separate app-specific files; cancelled or invalid imports do not remove other installed models. Remove an inactive model from its details to reclaim its model-file storage. Chat history remains saved.

Photo input is enabled only for Default/E4B when vision loads successfully. Switching to a text-only model opens a new chat if the current conversation contains photos. Saved photo conversations remain in history; select a photo-capable model to continue them.

Downloads use Android DownloadManager, survive process restarts and default to Wi-Fi only. An existing default download can resume after upgrading. Interrupted imports must be selected again. Full size and checksum verification occurs before installation.

## Runtime

- LiteRT-LM Android **0.18.0** with Kotlin **2.4.0** and coroutines **1.11.0**.
- AGP remains **8.10.1**, with **D8/R8 9.1.56** explicitly supplied for Kotlin 2.4 class-file compatibility.
- Text inference uses four CPU threads. Default/E4B image encoding uses GPU with CPU text fallback.
- Context: 4,096 tokens for most profiles; 2,048 for this Qwen3-8B export.
- Nemotron can reason before answering, with a 2,048-token thinking budget; this increases waiting time. Other profiles use the existing fast non-thinking setting.
- No audio recording, image generation or automatic tool execution is provided.

Model licenses remain separate from Solvix's MIT license. Review each source's current license, including NVIDIA's upstream terms for Nemotron. Model binaries are not included in the APK or repository.
