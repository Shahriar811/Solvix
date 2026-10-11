# Current Solvix validation notes

The current app focuses on chat. The previous dashboard, authentication, sensors, location and notification features have been removed at the user's request.

History keeps the 10 newest unpinned conversations and all pinned conversations. Pinning, deletion and completed replies commit to private local storage before updating the visible history. Photos are removed only after their references have been removed from the saved archive. Corrupt history is preserved and reported instead of silently overwritten.

The chat retains drafts, attachment selection and pending responses across rotation. Streaming displays text during generation and supports stopping; the native conversation is closed only after its completion callback confirms termination. Each saved conversation restores its own context. The model engine and its caches stay warm across new chats.

Local validation runs the debug build, all unit tests, Android lint and compilation of device tests. No phone launch, installation, benchmark or instrumentation run was performed for this redesign. Interactive verification and performance benchmarks remain to be run when device use is allowed.

The model and generation settings were kept unchanged. Streaming improves responsiveness, without claiming a measured hardware speedup. Chat drafts are not saved across process termination. Conversation length remains subject to the model's existing context limit. Pinned chats survive automatic retention, but not an explicit deletion, app data clearing or uninstall.

Version 1.1 uses one inset owner around a header, weighted message area and bottom composer. Navigation-bar and keyboard insets are combined with a maximum instead of stacked. The footer beneath the composer has been removed. Three headless Compose regression tests cover keyboard closed, opening at several heights, closing, and focusing the input. These tests run locally with Robolectric; no phone or model engine is used.

Version 1.2 adds a six-model catalog with verified public downloads, per-model imports, preflight hardware/storage estimates and safe switching with rollback. Existing default files are preserved. Headless policy/switching/UI tests pass locally; real inference and memory limits for the new models still require phone testing. No phone launch, installation or multi-gigabyte weight download was performed during development.
