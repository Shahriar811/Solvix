# Solvix

A minimal Android chat app for a private offline assistant. The app opens directly into chat; no account is required.

- Text chat and photo questions, with replies shown as they are generated.
- A history drawer with the 10 most recent unpinned conversations.
- Pin conversations to protect them from automatic cleanup; delete any conversation explicitly.
- Conversations and photos stay in private local storage and survive app restarts.
- Simple light, dark and system appearance options.
- Download or import the offline model from Settings.

Dashboard, sensors, location tools, notification access and sign-in have been removed. The launcher name, logo and application ID are preserved.

## Using chat history

Open the menu at the top left to see **Pinned** and **Recent** chats. Tap a conversation to continue it. Use the pin button to keep it, and the delete button to remove it after confirmation. Pinned conversations do not count toward the 10-chat recent limit. Unpinning an older chat makes it eligible for automatic removal.

A conversation is saved after a successful answer or a stopped partial answer. Drafts are kept through rotation but not persisted after process termination. Deleting a chat also removes its associated photos. The archive uses atomic file replacement; if saving fails, the input is kept for retry.

## Response speed

Replies stream instead of waiting for the complete answer. One model engine remains loaded while the app is open, and the active conversation's context is reused. Switching to a saved chat restores that chat's full saved context. Image encoding happens once per attachment.

The model, CPU backend, four-thread configuration, sampling settings and 4,096-token context limit are unchanged. Streaming reduces the wait before seeing text; it is not a measured improvement in tokens per second. No hardware performance claim is made without device benchmarks.

## Build and tests

Use a compatible JDK and the configured Android SDK:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
```

Local tests cover history retention, pinning, deletion, persistence, attachment cleanup, streaming, stopping, request failures and model integrity. Device tests are optional and must be run separately.

See [model setup](MODEL_SETUP.md) for the required download and runtime compatibility.
