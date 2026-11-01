# QQQ Android

[![Scanner Android](https://github.com/QRun-IO/qqq-android/actions/workflows/scanner.yml/badge.svg)](https://github.com/QRun-IO/qqq-android/actions/workflows/scanner.yml)

Native Kotlin/Compose clients for QQQ. The new `scanner-host` application runs server-defined QQQ apps and processes on Android handhelds, initially Zebra TC22.

## Modules

- `qqq/android/core`: metadata and process HTTP client (`QQQRepository` / `NetworkQQQRepository`).
- `qqq/android/mobileapp`: native app picker, authentication, process runner, and widget renderer.
- `scanner-host`: server configuration, device registration/approval gate, and Zebra EMDK barcode capture.
- `qqqsampleandroidmobileapp`: historical example application.

The client supports metadata and process execution. It does not implement the record CRUD/fluent API previously described by this README.

The scanner host uses the versioned semantic UI API for app discovery, native process screens and actions, plus the registered `mobile-scanners` extension for device enrollment, branding, connectivity and session administration. It requires extension 1.1 or later within major 1 and checks compatibility before login. See [the client contract and supported components](docs/UI-API.md); full web-client feature coverage is not implied.

## First scanner application

Use the companion `qbit-mobile-scanners` server/demo with QQQ `4.1.0-RC.20`. The demo uses QQQ TABLE_BASED username/password authentication. Credentials are exchanged through the native QQQ session endpoint; the password is not stored.

1. Start the companion demo and install the debug APK.
2. Run `adb reverse tcp:8080 tcp:8080` for a USB-connected TC22.
3. Open QQQ Mobile, use `http://127.0.0.1:8080/`, and sign in.
4. An administrator approves the displayed device ID in QQQ.
5. Tap **Check approval**, select the server-provided mobile app, and open **Scan capture**.
6. Scan a barcode and wait for **Saved scan** before scanning again. Manual entry is available for development and non-Zebra devices.

The server defines the app, native process steps, and `SCANNER` widget; the APK does not hard-code a scan process name. Existing native widget types continue to work.

## Zebra hardware scanning

The normal debug and release APKs use the device-provided Zebra EMDK service. They do not accept DataWedge scan broadcasts. No DataWedge profile setup is required. The scanner is acquired only while a scanner widget is visible and the activity is resumed; leaving or backgrounding releases it. EMDK temporarily takes scanner ownership from DataWedge and restores it on release. The app does not persistently modify DataWedge settings.

The same APK runs on other Android devices with explicit manual entry. When the required system library/service is missing or unavailable, the screen explains that hardware scanning is unavailable. [Scanner integration and SDK provenance](docs/ZEBRA-SCANNING.md) describes the lifecycle, compile-only SDK and verification gates. Synthetic broadcasts are accepted only by the separate disposable E2E variant.

## Customer branding and diagnostics

The shared launcher name/icon remain QQQ Mobile. On connecting to a server, the host reads public `GET /qqq/v1/qbit/mobile-scanners/branding` (`displayName`, `primaryColor`, optional `logoUrl`). The name and Material theme apply to login, device approval, and native app screens. Names are limited to 80 printable characters and colors to `#RRGGBB`; malformed values fall back to QQQ defaults. Button foregrounds choose readable black/white contrast. Optional logos must use HTTPS, are fetched without session/device headers or cookies, and cannot prevent login if unavailable. Responses are size-limited, redirects are disabled, and branding is reset when changing servers. Reconnect to pick up server branding changes; no APK rebuild is needed.

The persistent footer shows **Checking**, **Connected**, or **Disconnected** with the last check time. It checks `GET /qqq/v1/qbit/mobile-scanners/status` every five seconds while foregrounded and on network changes; `Connected` means a valid `{ "status": "ok" }` response, not sign-in or device approval. Tap the footer for **Copy**, **Clear**, and **Close** diagnostics. The in-memory log holds the latest 100 fixed event codes with timestamps, HTTP status, and latency for branding, connection checks, native QQQ calls (including login/process requests), and device enrollment/status requests. A transport failure has no HTTP status; an HTTP response retains its status, including 401/403/500 errors. The log contains no credentials, scan values, request paths, queries, raw response text, or exception messages. Changing servers clears the displayed diagnostics. Monitoring never queues or retries a scan.

## Online-only behavior

QQQ administrators can inspect session activity, end sessions, and request live viewing of the QQQ app from a session record. Viewing displays an indicator with **Stop viewing** on the handheld. Password/login screens and background apps cannot be mirrored. Foreground location reporting defaults on and can be disabled in Settings. It requires Android permission and enabled device location providers; scanning works without it. See [session behavior and limits](docs/UI-API.md#session-activity-and-screen-viewing).

There is no offline queue. One submitted scan is retained in memory until a complete receipt returns the matching `acknowledgedEventId`, `scanSaved: true`, and a nonblank `scanId`. If a response is uncertain, **Retry same request** preserves its event ID and original timestamp; the server deduplicates it. Further scans wait for confirmation or explicit abandonment. Leaving after an uncertain response warns that the scan may already be saved and attempts server cancellation; if cancellation fails, **Leave anyway** lets the operator exit explicitly. Active requests and pending photos remain protected. Sign-out/reset clears the pending request; app termination loses in-memory state. Repeated physical scans deliberately receive distinct event IDs.

Enrollment credentials are encrypted with Android Keystore and stored under `noBackupFilesDir`, scoped to the configured server. The app disables backup. Device headers accompany native QQQ requests, and the server enforces approval on every scan. Release builds reject HTTP; only the development build permits it for local testing. The host disables HTTP redirects to avoid forwarding credentials to another origin.

## Build

Requirements: Java 17 or compatible newer runtime, Android SDK platform35, build tools34/35, and the included Gradle8.7 wrapper. Configure `ANDROID_HOME` or ignored `local.properties`.

```powershell
.\gradlew.bat :scanner-host:testDebugUnitTest :scanner-host:assembleDebug :scanner-host:lintDebug
```

APK: `scanner-host/build/outputs/apk/debug/scanner-host-debug.apk`.

The existing libraries also have unit and emulator tests. `testDebugUnitTest` runs JVM tests; `connectedDebugAndroidTest` needs a connected device/emulator. Physical TC22 trigger, lifecycle and scanner-ownership behavior must still be verified on hardware.

See [emulator testing](docs/EMULATOR.md) for a local interactive demo, synthetic scanner broadcasts, and the disposable-emulator smoke check.

## CI and delivery

The managed CircleCI Android profile owns shared-library verification and its existing main-only Maven publication. The GitHub **Scanner Android** workflow adds shared/scanner JVM tests, scanner lint, debug APK assembly, SHA256, and downloadable artifacts for pull requests and supported branches. The API35 emulator lane runs the shared native UI tests and real-server scanner E2E on pushes and pull requests. See [emulator testing](docs/EMULATOR.md) for the isolated test package and contract checks.

The GitHub workflow does not publish libraries or production releases. Signed APK distribution remains a separate release task. See [Contributing](CONTRIBUTING.md), [Security](SECURITY.md), and [Changelog](CHANGELOG.md).

## Extension points

`LocalAuthenticatedContent` allows a host to gate authenticated QQQ content. `LocalCustomWidgetRenderer` lets the host render additional metadata-defined widgets without replacing the standard renderer. `NetworkQQQRepository` accepts a server-scoped header provider; credentials never enter process values.

The scanner host supports TABLE_BASED authentication and server-advertised OAuth2 browser sign-in, including Authentik. Updated OAuth2 requires UI API1.10 native-session login and nonce-validation capabilities, the explicit REQUIRED nonce policy and callback registration; it is being qualified and does not infer support from the provider name. Unsupported servers cannot downgrade browser login; TABLE sign-in is unchanged. See [authentication setup and verification](docs/AUTHENTICATION.md). Existing shared-library Auth0 support remains intact.

## License

Licensed under Apache-2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE). Historical project-owned source headers and the IDE copyright template have been reconciled with the repository's January 2026 license migration. Existing copyright ownership and third-party notices are preserved.
