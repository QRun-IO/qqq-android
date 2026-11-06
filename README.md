# QQQ Android

Native Kotlin/Compose clients for QQQ. The `scanner-host` application ("QQQ Mobile", `io.qrun.mobile.scanner`) runs server-defined QQQ apps and processes on Android handhelds, currently Zebra TC22 and MC9400 class devices. It is online only.

Current progress and open items: [status](docs/SESSION-STATE.md), [checklist](docs/TODO.md), [1.0 finish ledger](docs/FINISH-1.0.md). Superseded status notes are kept under [docs/history](docs/history/README.md).

## Modules

| Module | Purpose |
| --- | --- |
| `qqq/android/core` | Metadata and process HTTP client (`QQQRepository` / `NetworkQQQRepository`). |
| `qqq/android/mobileapp` | Native app picker, authentication, process runner and widget renderer. |
| `scanner-host` | The scanner application: server configuration, device registration/approval gate, Zebra EMDK barcode capture, branding, diagnostics, settings. |
| `qqqsampleandroidmobileapp` | Historical example application and the shared native test host. |

The libraries support metadata and process execution. They do not implement a record CRUD/fluent API.

## What the scanner host does

- **Semantic API only.** All operations use the versioned UI API plus the registered `mobile-scanners` extension (device enrollment, branding, status, sessions). The host requires extension 1.1 or later within major 1 and checks `/qqq/v1/version` compatibility before sending credentials. Optional capabilities (for example raw scan bytes, mobile 1.5) are used only when the same server advertises them. See [UI API contract and supported components](docs/UI-API.md); web-client parity is not implied.
- **Server-defined screens.** The server supplies the app, native process steps and the `SCANNER` widget; the APK hard-codes no process name. Unknown required controls stop submission explicitly.
- **Device approval.** Each installation enrolls a device that a QQQ administrator must approve; the server enforces approval on every scan.
- **Zebra scanning.** Uses the device's EMDK service, acquired only while a scanner widget is visible and resumed, and released on leave. No DataWedge profile setup is needed and DataWedge settings are not persistently modified. Other Android devices use explicit manual entry. See [Zebra scanning](docs/ZEBRA-SCANNING.md).
- **Runtime branding.** The shared launcher stays "QQQ Mobile"; after connecting, the host reads public `GET /qqq/v1/qbit/mobile-scanners/branding` (`displayName` up to 80 printable characters, `primaryColor` as `#RRGGBB`, optional HTTPS `logoUrl`). Malformed values fall back to QQQ defaults; logos are fetched without session/device headers; branding resets when the server changes. No per-customer build.
- **Connection footer and diagnostics.** The footer shows Checking, Connected or Disconnected from `GET /qqq/v1/qbit/mobile-scanners/status` (reachability only, not sign-in or approval). Tap it, or use the menu, for a full-screen view of the latest 100 sanitized events with All/Problems filters, pause, copy/share/clear. The log holds fixed event codes, HTTP status and latency, never credentials, scan values, paths or response text.
- **Echo (local print workflow).** Echo is a server-side semantic process (printer, label size, document and optional copies per scan 1–100, chosen once per run). The client uses only generic native controls; printing happens on the server side and nothing printer-specific is installed on the scanner.
- **Settings.** Orientation (portrait by default), foreground location reporting (default on, Android permission required, scanning works without it), optional status wallpaper chooser, and About QQQ Mobile (version, public links, bundled open-source licenses).
- **Managed configuration and wallpaper.** The app declares Android app restrictions (`qqq_server_url`, `qqq_orientation`) and an optional live status wallpaper. See [device provisioning](docs/DEVICE-PROVISIONING.md); no company profile or MDM rollout has been verified.

### Online-only behavior

There is no offline queue. One submitted scan is held in memory until a complete receipt returns the matching `acknowledgedEventId`, `scanSaved: true` and a nonblank `scanId`. After an uncertain response, **Retry same request** keeps the event ID and original timestamp and the server deduplicates; further scans wait for confirmation or explicit abandonment. Leaving then warns that the scan may already be saved and attempts server cancellation, with **Leave anyway** as the explicit fallback. Sign-out clears the pending request; app termination loses in-memory state.

Administrators can inspect sessions, end them and request live viewing of the QQQ app. Viewing shows an informational indicator on the handheld; password screens and background apps cannot be mirrored; whole-device sharing needs Android consent. See [session behavior and limits](docs/UI-API.md#session-activity-and-screen-viewing).

Enrollment credentials are encrypted with the Android Keystore under `noBackupFilesDir`, scoped to the server, and backup is disabled. Redirects are disabled. **Release builds reject HTTP; only development builds allow it for local testing.**

## Prerequisites

- Java 21, Android SDK platform 35 with build tools 34/35, and the included Gradle 8.7 wrapper. Set `ANDROID_HOME` or an ignored `local.properties`.
- A QQQ server exposing the semantic UI API (core 1.8 or later within major 1; OAuth2 sign-in needs 1.10) and the `mobile-scanners` extension. The companion server/demo is `qbit-mobile-scanners` (its POM pins the QQQ source default, currently `4.1.0-RC.21`; a local server may run an older qualified core, which the host checks before login). Local demo authentication is TABLE_BASED username/password, exchanged through the native QQQ session endpoint; the password is not stored.

## Installing on a device

Two distinct paths. Do not mix their APKs or assumptions.

### Local demo (development-signed debug APK, HTTP, same workstation)

For trying the scanner against a local server. The debug APK uses the development certificate and is not a release.

1. Start the companion demo server and build the debug APK (below).
2. Install it with `adb install -r`, naming the device with `-s`.
3. Forward the device port to the server: `adb -s <device> reverse tcp:8080 tcp:<server port>`. This works over USB or paired wireless ADB and must be repeated after the connection drops; it is a workstation-attached demo, not autonomous LAN rollout.
4. Open QQQ Mobile, connect to `http://127.0.0.1:8080/`, and sign in.
5. An administrator approves the displayed device in QQQ (the mobile device approval process); tap **Check approval**, choose the server's mobile app, and open **Scan capture** or Echo.
6. Wait for **Saved scan** (or Echo's acknowledgment) before scanning again.

See [emulator testing](docs/EMULATOR.md) for an emulator demo, synthetic scans and the disposable E2E variant.

### Signed APK (direct install / MDM)

Requires an **HTTPS** server URL and the production-signed APK built with the QRun-owned key. Follow the [installation guide](docs/INSTALLATION.md) and [release readiness](docs/RELEASING.md). The published download remains the [signed 1.0.0 preview](https://git.kof22.com/QRun-IO/qqq-android/releases/tag/scanner-v1.0.0) (prerelease, forge sign-in required). The signed 1.0.1 candidate has passed trusted-HTTPS enrollment, physical scanning, navigation and an in-place update on the MC9400, preserving login and enrollment. Its publication, native Authentik qualification and final dependency-cohort checks remain open; a 1.0.2 (versionCode 4) candidate with company sign-out, user-name and heartbeat-version fixes is prepared but not yet built (see [CHANGELOG](CHANGELOG.md)). Development-signed installations require an explicit migration to the production signer.

## Build and test

```powershell
.\gradlew.bat :qqq:android:core:testDebugUnitTest :qqq:android:mobileapp:testDebugUnitTest :scanner-host:testDebugUnitTest :scanner-host:lintDebug :scanner-host:assembleDebug
```

Debug APK: `scanner-host/build/outputs/apk/debug/scanner-host-debug.apk`. `connectedDebugAndroidTest` needs a device or emulator. Scanner flow changes also use the separate E2E variant (`:scanner-host:assembleE2e :scanner-host:assembleE2eAndroidTest`, then `node scripts/ci/scanner-e2e.mjs ../qbit-mobile-scanners`) as described in [AGENTS.md](AGENTS.md) and [emulator testing](docs/EMULATOR.md). Device-level acceptance evidence is described in [device testing](docs/DEVICE-TESTING.md).

Limits: JVM, emulator and synthetic-scan tests do not prove physical trigger, lifecycle, label printing or provider behavior; those need hardware. Version-one scope excludes offline queueing and background synchronization.

## CI and automation

The primary forge is Gitea (`git.kof22.com/QRun-IO`); GitHub is a shadow/archive and receives no ordinary writes. Protected `main` runs [`.gitea/workflows/qrun-android-release.yaml`](.gitea/workflows/qrun-android-release.yaml) on push and manual dispatch; it calls the shared `KofTwentyTwo/platform-ci` Android workflow (`android.yaml@qrun-release-v1`). That workflow builds, verifies and publishes the SDK libraries; it is not a scanner-APK signing or release pipeline. Pull-request checks come from central Gitea policy. The managed CircleCI profile (`.qqq-ci.yml`) and its main-only library publication are retained. The `.github` workflows are legacy and not a release path. Shared CI design is out of scope for this repository's local use: the demo above needs none of it.

## Extension points

`LocalAuthenticatedContent` lets a host gate authenticated QQQ content. `LocalCustomWidgetRenderer` lets the host render additional metadata-defined widgets without replacing the standard renderer. `NetworkQQQRepository` accepts a server-scoped header provider; credentials never enter process values.

The scanner host supports TABLE_BASED authentication and server-advertised OAuth2 browser sign-in, including Authentik. OAuth2 needs UI API 1.10 native-session login and nonce-validation capabilities, the REQUIRED nonce policy and callback registration, and never infers support from the provider name; unsupported servers cannot downgrade. Real desktop Authentik acceptance has passed; native on-device provider acceptance is still open. See [authentication setup and verification](docs/AUTHENTICATION.md). Existing shared-library Auth0 support remains intact.

## Source map

- Managed configuration: [`ManagedConfiguration.kt`](scanner-host/src/main/java/io/qrun/mobile/scanner/ManagedConfiguration.kt), [`app_restrictions.xml`](scanner-host/src/main/res/xml/app_restrictions.xml)
- Status wallpaper: [`QqqStatusWallpaperService.kt`](scanner-host/src/main/java/io/qrun/mobile/scanner/QqqStatusWallpaperService.kt)
- Orientation policy: [`OrientationPolicy.kt`](scanner-host/src/main/java/io/qrun/mobile/scanner/OrientationPolicy.kt)
- Manifest: [`AndroidManifest.xml`](scanner-host/src/main/AndroidManifest.xml)
- Company profile generator: [`deployment/company-profile`](deployment/company-profile/), [COMPANY-PROFILE.md](docs/COMPANY-PROFILE.md)

See also [Contributing](CONTRIBUTING.md), [Security](SECURITY.md) and [Changelog](CHANGELOG.md).

## License

Licensed under Apache-2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE). Existing copyright ownership and third-party notices are preserved.
