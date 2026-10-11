# Offline Gemma 4 setup

Solvix uses one `gemma-4-E2B-it.litertlm` bundle for text chat and image understanding. The previous Gemma 3 / Gemma 3n `.task` files are no longer loaded.

For testing without authentication, tap **Continue as guest** on the sign-in screen. Guest mode gives access to the local chat, model setup, sensors and location tools without creating a Firebase account or signing in with Google. The session remains active after restarting the app. Use **Leave guest mode** on the dashboard to return to sign in. Android permissions are still required for features such as camera and location.

Open **Chat** and choose one of these setup options:

- **Download** fetches the standard 2.59 GB bundle from Hugging Face. Wi-Fi only is selected by default. Android's download manager continues the transfer in the background and resumes when a suitable connection returns. Progress and cancellation appear in Chat.
- **Import** opens Android's file picker. Select the standard `.litertlm` file from Downloads, USB storage or another document provider. Solvix copies it into app storage, so it no longer depends on the original file. You can remove your original copy after setup completes.

You need at least 2.7 GB of additional free storage. Both routes verify the file size and SHA-256 before installing it. No ADB transfer or Hugging Face login is required for this public model. The file is stored in Solvix's app-specific `models` directory and excluded from backups. Uninstalling the app removes the installed model.

Once setup is complete, text and image inference run locally. Authentication and other app features may still require network access. The current chat supports text, gallery images and camera snapshots; this change does not add audio recording or image generation.

Camera and location permissions are requested when using those features. Notification access is optional and can be enabled from Chat; startup does not open Android settings. Chat drafts, attachments and in-flight replies survive screen rotation, but the current conversation is not saved across process termination. Failed requests retain their input for retry.

The location screen shows positions and destination distances. Its notes and selected apps do not execute geofence automation or send messages automatically.

## Compatibility

- Runtime: LiteRT-LM Android **0.13.1**, pinned with Kotlin **2.2.21** for the project's existing AGP 8.10.1 build tools.
- Text inference uses the CPU. Image inference uses the GPU vision encoder. If vision initialization fails, Solvix falls back to text chat and explains that image analysis is unavailable.
- Use a supported 64-bit Android device. Available RAM, GPU drivers and context length affect whether the model can load and how quickly it responds.
- Import the standard bundle. Intel Lunar Lake / Panther Lake, specific Tensor or Qualcomm NPU, GPU-only and web bundles are not accepted by this setup.

## Model source

- [Model card and Apache 2.0 license](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
- [Pinned model download](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1/gemma-4-E2B-it.litertlm)
- Size: `2588147712` bytes
- SHA-256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
- [LiteRT-LM Android documentation](https://developers.google.com/edge/litert-lm/android)

The model binary is downloaded separately and is not included in the APK or Git repository.
