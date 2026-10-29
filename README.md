# QQQ Android

[![Scanner Android](https://github.com/QRun-IO/qqq-android/actions/workflows/scanner.yml/badge.svg)](https://github.com/QRun-IO/qqq-android/actions/workflows/scanner.yml)

Native Kotlin/Compose clients for QQQ. The new `scanner-host` application runs server-defined QQQ apps and processes on Android handhelds, initially Zebra TC22.

## Modules

- `qqq/android/core`: metadata and process HTTP client (`QQQRepository` / `NetworkQQQRepository`).
- `qqq/android/mobileapp`: native app picker, authentication, process runner, and widget renderer.
- `scanner-host`: server configuration, device registration/approval gate, and DataWedge barcode capture.
- `qqqsampleandroidmobileapp`: historical example application.

The client supports metadata and process execution. It does not implement the record CRUD/fluent API previously described by this README.

## First scanner application

Use the companion `qbit-mobile-scanners` server/demo with QQQ `4.1.0-RC.20`. The demo uses QQQ TABLE_BASED username/password authentication. Credentials are exchanged through the native QQQ session endpoint; the password is not stored.

1. Start the companion demo and install the debug APK.
2. Run `adb reverse tcp:8080 tcp:8080` for a USB-connected TC22.
3. Open QQQ Mobile, use `http://127.0.0.1:8080/`, and sign in.
4. An administrator approves the displayed device ID in QQQ.
5. Tap **Check approval**, select the server-provided mobile app, and open **Scan capture**.
6. Scan a barcode and wait for **Saved scan** before scanning again. Manual entry is available for development and non-Zebra devices.

The server defines the app, native process steps, and `SCANNER` widget; the APK does not hard-code a scan process name. Existing native widget types continue to work.

## Zebra DataWedge profile

Create a profile associated with `io.qrun.mobile.scanner`, activity `*`:

- Enable Barcode input and single-barcode decoding.
- Disable Keystroke output.
- Enable Intent output with action `io.qrun.mobile.scanner.SCAN`.
- Set category `android.intent.category.DEFAULT` and delivery to Broadcast.
- Target package `io.qrun.mobile.scanner` when package targeting is available.

The receiver is active only while a scanner widget is visible and its activity is resumed. It reads `com.symbol.datawedge.data_string`, `label_type`, and `source`. Multi-decode payloads are rejected. Symbology/source are client-reported metadata, not proof of scanner hardware identity. See [Zebra Intent output](https://techdocs.zebra.com/datawedge/latest/guide/output/intent/).

## Online-only behavior

There is no offline queue. One submitted scan is retained in memory until its `acknowledgedEventId` is returned. If a response is uncertain, **Retry same request** preserves its event ID and original timestamp; the server deduplicates it. Further scans are blocked until the pending request is resolved. Sign-out/reset clears the pending request; app termination loses in-memory state. Repeated physical scans deliberately receive distinct event IDs.

Enrollment credentials are encrypted with Android Keystore and stored under `noBackupFilesDir`, scoped to the configured server. The app disables backup. Device headers accompany native QQQ requests, and the server enforces approval on every scan. Release builds reject HTTP; only the development build permits it for local testing. The host disables HTTP redirects to avoid forwarding credentials to another origin.

## Build

Requirements: Java 17 or compatible newer runtime, Android SDK platform35, build tools34/35, and the included Gradle8.7 wrapper. Configure `ANDROID_HOME` or ignored `local.properties`.

```powershell
.\gradlew.bat :scanner-host:testDebugUnitTest :scanner-host:assembleDebug :scanner-host:lintDebug
```

APK: `scanner-host/build/outputs/apk/debug/scanner-host-debug.apk`.

The existing libraries also have unit and emulator tests. `testDebugUnitTest` runs JVM tests; `connectedDebugAndroidTest` needs a connected device/emulator. Physical TC22 trigger/intent behavior must still be verified on hardware.

See [emulator testing](docs/EMULATOR.md) for a local interactive demo, synthetic scanner broadcasts, and the disposable-emulator smoke check.

## CI and delivery

The managed CircleCI Android profile owns shared-library verification and its existing main-only Maven publication. The GitHub **Scanner Android** workflow adds shared/scanner JVM tests, scanner lint, debug APK assembly, SHA256, and downloadable artifacts for pull requests and supported branches. Its manual **Run workflow** option adds native UI tests and a scanner launch smoke on an API35 emulator.

The GitHub workflow does not publish libraries or production releases. Signed APK distribution remains a separate release task. See [Contributing](CONTRIBUTING.md), [Security](SECURITY.md), and [Changelog](CHANGELOG.md).

## Extension points

`LocalAuthenticatedContent` allows a host to gate authenticated QQQ content. `LocalCustomWidgetRenderer` lets the host render additional metadata-defined widgets without replacing the standard renderer. `NetworkQQQRepository` accepts a server-scoped header provider; credentials never enter process values.

The first scanner host supports TABLE_BASED authentication only and shows an explicit unsupported-provider message for other authentication types. Existing shared-library Auth0 support remains intact; production identity-provider integration requires callback configuration and verification before enabling it in this host.

## License

Root license: Apache-2.0. Historical source files still carry older license headers; the repository's pre-existing license-header inconsistency has not been resolved by this implementation.
