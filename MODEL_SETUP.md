# Offline assistant setup

Open the three-dot menu in Chat. Under **Offline assistant**, choose **Download** or **Import**. The model name is not shown in the app interface.

Download fetches the standard 2.59 GB mobile bundle from Hugging Face. Wi-Fi only is selected by default. Android's download manager continues the transfer in the background and resumes when a suitable connection returns. Progress and cancellation appear in Settings.

Import opens Android's file picker. Select the standard `.litertlm` file from Downloads, USB storage or another document provider. Solvix copies it into app storage, so it no longer depends on the original. Full file size and SHA-256 are verified before installation. Processor-specific, GPU-only, web and old `.task` bundles are not accepted.

You need at least 2.7 GB of additional storage. The model is excluded from backups. Uninstalling the app removes the installed model and private chat history. Existing installations retain their model file without re-downloading it.

After setup, text and image inference run locally. No sign-in is required. Camera permission is requested only when capturing a photo. Image analysis needs a compatible GPU; text chat remains available if vision initialization is unsupported. This app does not provide audio recording or image generation.

## Runtime and source

- LiteRT-LM Android **0.13.1**, Kotlin **2.2.21**, AGP **8.10.1**.
- Text inference uses the CPU with four threads; vision uses the GPU.
- The standard mobile bundle is the same model used by previous Solvix builds. File names, cache and verification preferences are retained internally for compatibility.
- [Model source and license](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
- [Pinned model download](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1/gemma-4-E2B-it.litertlm)
- Size: `2588147712` bytes
- SHA-256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
- [Runtime streaming API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md)

The model binary is downloaded separately and is not included in the APK or Git repository.
