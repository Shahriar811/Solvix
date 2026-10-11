# Solvix code review — 12 October 2026

## Fixes

- Chat state and inference now belong to the same ViewModel. Rotation preserves messages, drafts, attachments and pending replies. Failed requests retain their input; repeated taps cannot start duplicate inference calls.
- Camera capture requests permission when needed, handles a missing camera app and treats cancellation as cancellation. Gallery decoding is bounded on Android 8.1 as well as newer versions.
- Removed obsolete storage permissions: model and image file pickers grant access to selected files. Notification settings are optional and open from Chat instead of interrupting app startup.
- Model cancellation prevents new installs from overlapping cleanup, preserves a completed installation and ignores cancellation-related I/O failures. Wi-Fi-only downloads now explicitly restrict the network type. Import handles missing file names and unknown file sizes; the full size and checksum are still mandatory before installation.
- Model-loading retries cannot reset an already-ready engine or queue duplicate initialization.
- Maps receive start/resume/pause/stop/destroy events. Settings changes refresh location status, approximate permission is accepted, and the permission button requests access. Map callbacks use current location/follow state. Location notes and destination selection survive rotation.
- Location and sensor listeners pause outside the foreground. Partial sensor registrations are cleaned up after failure. Location timestamps retain the provider's measurement time.
- Sign-in handles cancellation and missing Google tokens. Google sign-in and account creation reject repeated taps while busy. Successful signup opens the dashboard; a failed display-name update does not leave an already-created account stuck on signup.
- Notification text accepts styled CharSequence values. Notification contents and precise coordinates are no longer written to debug logs.
- Fixed the camera hardware declaration that made Android lint fail. Numeric displays use an explicit locale. Dashboard labels no longer claim that GPS, inference or geofence automation are active without checking.

## Validation

Local checks: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin`.

The nine unit tests include four new chat regression tests and four model-integrity tests. No app launch, device installation or instrumentation run was performed during this review, as requested. Camera, map, permission and authentication changes still need interactive device verification later.

## Limits

Chat survives configuration changes but is not persisted across process termination. Location notes and linked-app selections do not execute geofence automation. Audio input and image generation are not implemented. Dependency/deprecation and resource-style warnings remain; this review does not perform a broad dependency migration.

## Platform references

- [Android permission requests](https://developer.android.com/training/permissions/requesting)
- [Camera intents and permission requirements](https://developer.android.com/privacy-and-security/minimize-permission-requests)
- [MapView lifecycle requirements](https://developers.google.cn/android/reference/com/google/android/gms/maps/MapView)
