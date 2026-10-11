# Solvix

**A clean, private Android chat app.**

Solvix opens directly into a conversation. Ask questions, share photos and read answers as they are generated—all on your device after the one-time model setup. No account is required.

Developed by **[Md. Shahriar Hossain](https://github.com/Shahriar811)**.

## Features

- **Minimal chat interface** with light, dark and system appearance.
- **Offline text and photo questions** after the assistant is installed.
- **Streaming answers**, a stop button, text selection and copy controls.
- **Local chat history** that keeps your 10 most recent unpinned conversations.
- **Pinned conversations** that stay until you explicitly delete them.
- **Six-model library** with model import, Hugging Face download and switching.
- **Hardware preflight** for CPU architecture, Android, RAM and storage before installation.
- **Developer information** and a GitHub profile link in Settings → About Solvix.

## Get started

1. Install Solvix and open Settings from the three-dot menu.
2. Under **Models**, expand the default or another model, then choose **Download** or **Import**. Review the hardware estimate and required storage. Downloads use Wi-Fi by default. Choose **Use model** after installation.
3. Once setup finishes, start chatting. Use **+** to choose or capture a photo on devices that support image analysis.

Model setup instructions, runtime requirements and the verified download source are in [MODEL_SETUP.md](MODEL_SETUP.md).

## Your conversations

Use the menu at the top left to open **Pinned** and **Recent** chats. Tap a chat to continue it, the pin icon to protect it from cleanup, or the trash icon to delete it after confirmation.

Pinned chats do not count toward the 10-chat recent limit. When a new conversation is saved, older unpinned chats are removed automatically. Unpinning an old conversation makes it eligible for cleanup. Deleting a chat also removes its saved photos.

Completed answers and stopped partial answers are saved locally and survive app restarts. Drafts and in-flight replies survive screen rotation; drafts are not saved across process termination. Clearing app data or uninstalling Solvix removes local history and the installed model.

## Responsiveness

Answers stream while being generated, and the model stays loaded while the app is open. The current conversation's context is reused; opening another saved chat restores its own context.

Model and answer-quality settings are unchanged. Hardware speed has not been benchmarked, and long conversations remain subject to the model's context limit.

## Build and validation

The project uses Kotlin 2.4.0, Android Gradle Plugin 8.10.1, D8/R8 9.1.56 and LiteRT-LM 0.18.0. Configure a compatible JDK and Android SDK, then run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
```

Local tests cover retention, pinning, deletion, persistence, photo cleanup, streaming, stopping, model integrity, hardware policy, model switching/rollback and model-library controls. Device tests and hardware benchmarks run separately.

## Developer

**Md. Shahriar Hossain**

- [GitHub profile — @Shahriar811](https://github.com/Shahriar811)
- [Source code](https://github.com/Shahriar811/Solvix)
- [Report an issue](https://github.com/Shahriar811/Solvix/issues)

## License

Solvix's application source code is licensed under the [MIT License](LICENSE).

Copyright © 2026 **Md. Shahriar Hossain**.

Third-party libraries and the separately downloaded model retain their own licenses. The app's license does not replace those terms. The full app license is available offline in **Settings → About Solvix → MIT License**.
