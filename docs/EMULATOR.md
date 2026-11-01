# Emulator testing

An Android emulator verifies login, enrollment, native navigation and manual barcode submission. The separate E2E variant also supports synthetic scan injection. An emulator does not reproduce Zebra's EMDK scan engine or physical trigger.

## Prepared Windows workstation

The `QQQ_Mobile_API35` virtual device is installed under `%LOCALAPPDATA%/Android/Avd`, with Android 15/API35 and WHPX acceleration. Local emulator testing is currently paused: the operator confirmed whole-workstation stuttering stopped when the emulator closed. Do not restart it as part of automatic local tests. The next deliberate diagnostic launch should compare software rendering while preserving the other settings:

```powershell
$env:ANDROID_AVD_HOME = Join-Path $env:LOCALAPPDATA 'Android/Avd'
& "$env:LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd QQQ_Mobile_API35 -gpu software -no-snapshot -no-audio
```

The previous host-GPU run logged OpenGL context restoration and external-memory import failures. Sampled host resources had ample headroom and WHPX was operational; graphics interoperability is the leading hypothesis, not a proven cause. The saved AVD now has `hw.gpu.mode=software`, with the previous config backed up locally; that renderer has not yet been tested. Software graphics retains WHPX CPU acceleration. A cold start avoids restoring old graphics state; `-no-snapshot` preserves installed apps and their data. The [Android acceleration guide](https://developer.android.com/studio/run/emulator-acceleration) recommends software rendering when host/auto graphics have problems. Preserve other settings during the comparison, observe desktop responsiveness as well as guest frame times, and stop if workstation stalls recur. Use the physical TC22 or hosted emulator CI for acceptance while local testing is paused.

The local demo uses port57832 on this workstation because other services occupy the conventional ports. Start it from the sibling `qbit-mobile-scanners` directory in another terminal, then forward the emulator port:

```powershell
pwsh -File scripts/Start-Demo.ps1 -SkipBuild -Port 57832
```

```powershell
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s emulator-5554 reverse tcp:8080 tcp:57832
```

Keep the Android server URL `http://127.0.0.1:8080/`. The browser administration URL is <http://127.0.0.1:57832/>. The demo's [local guide](https://github.com/QRun-IO/qbit-mobile-scanners/blob/develop/docs/LOCAL-DEMO.md) explains the `scanner`/`admin` accounts and password-copy helper. Open **QQQ Mobile** in the emulator. Tap the connection footer to view or copy diagnostics; it distinguishes server reachability from login and approval.

## Local interactive test

1. Create/start an API35 Android emulator in Android Studio, or use the SDK emulator installed on the development machine.
2. Build/install `scanner-host/build/outputs/apk/debug/scanner-host-debug.apk` with `adb install -r` targeting the emulator's serial if multiple devices are connected.
3. Start the companion [mobile scanner demo](https://github.com/QRun-IO/qbit-mobile-scanners/tree/main/demo) on port8080.
4. Run `adb reverse tcp:8080 tcp:8080` and use `http://127.0.0.1:8080/` in QQQ Mobile.
5. Sign in with the demo operator account. Approve the displayed device from the administrator session, then tap **Check approval**.
6. Select the mobile app, open its scan process, and submit a synthetic barcode manually. Verify **Saved scan**, and verify the server's stored record.
7. Revoke the device on the server and verify subsequent submissions are denied. Sign out and verify a new login is required.

Only the disposable E2E APK accepts synthetic broadcasts while its scanner screen is visible. Normal debug/release builds ignore this ingress and use EMDK or manual entry:

```text
adb shell am broadcast -a io.qrun.mobile.scanner.SCAN -c android.intent.category.DEFAULT -p io.qrun.mobile.scanner.e2e --es com.symbol.datawedge.data_string DEMO-001 --es com.symbol.datawedge.label_type LABEL-TYPE-CODE128
```

This validates intent parsing only; the server treats source/symbology as client-reported metadata.

## Automated checks

The GitHub **Scanner Android** workflow runs JVM tests, lint, APK assembly, the shared native UI suite, and the real-server scanner E2E on pushes and pull requests. The emulator job checks out exact core and companion qbit commits, verifies them in a fresh private Maven repository, and starts a fresh isolated demo database. [Fixture provenance](CI-FIXTURE.md) describes the source pins, runtime checks and distinction from published release dependencies. Sanitized instrumentation results are uploaded as workflow artifacts. Hosted execution is verified separately from a local run.

The scanner E2E installs **QQQ Mobile E2E**, a separate application (`io.qrun.mobile.scanner.e2e`). It drives server selection, username/password login, pending enrollment, approval, app selection, Scan Capture, manual barcode submission, server acknowledgment, independent persisted-record verification, and revocation of an already-open scan process. Administrative setup uses the real QQQ process endpoints; the companion qbit's browser suite covers their single-record and selected-bulk UI entry points. No repository or server mocks are used. Random test credentials travel through standard input into the test application's private files, are removed after reading, and are redacted from reports. The runner preserves the normal scanner package and its port forwarding.

The runner uses a test-only forwarding proxy that checks observed requests against the real server's published OpenAPI operations. It modifies only the version response for incompatible-major, missing-extension, missing-Android-channel and additive-future-version scenarios. Rejected servers must receive no credentials or requests beyond public discovery. The eight scenarios also cover the real scan flow, native administrative forms for approval/revocation/session termination/cancellation, session activity and actual screen mirroring with forced logout, and a PixelCopy pixel-content regression. Compatible scenarios use the real backend, processes and database. The audit report contains operation IDs and counts, never request headers or bodies. [SESSION-STATE.md](SESSION-STATE.md) records actual passing runs and revision boundaries. The runner wakes/unlocks the selected emulator, temporarily disables animation scales and restores their original values during cleanup. UI conditions have a 60-second deadline and instrumentation has a 300-second deadline. Set `E2E_SCENARIO=session-monitor` or `E2E_SCENARIO=pixel-copy` for focused verification.

The default suite also runs bundled-license rendering, local orientation policy checks and the diagnostics filtering/pause/clear/return UI test. Set `E2E_SCENARIO=licenses`, `E2E_SCENARIO=orientation` or `E2E_SCENARIO=diagnostics` to run one independently; these scenarios need only the scanner E2E APKs and do not start a server or establish USB forwarding. After installation and ordinary scenarios' data resets, the runner grants coarse and fine location permission only to `io.qrun.mobile.scanner.e2e`, keeping the default-on reporting permission dialog from interrupting unrelated scanner/UI automation. The session-monitor scenario instead explicitly revokes both permissions and seeds only the already-requested marker, preserving default-on reporting and its real denied-location telemetry assertion without an OS dialog. This fixture setup does not exercise the real Android permission prompt or user denial interaction and does not change permissions for the normal scanner or separate QA package.

To reproduce with Node 22+ and Java 21, first prepare the [source-pinned fixture](CI-FIXTURE.md). Use its qualified server directory below (the example assumes `.e2e-server`); an ordinary server build against the default RC.20 core does not include the nullable process fix:

```powershell
./gradlew.bat :scanner-host:assembleE2e :scanner-host:assembleE2eAndroidTest
$env:ANDROID_SERIAL = 'emulator-5554'
node scripts/ci/scanner-e2e.mjs .e2e-server
```

Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) if `adb` is not on PATH. Linux/macOS use `./mvnw`, `./gradlew`, and their shell's environment assignment syntax. The runner refuses physical devices, uses a free local port and temporary database, and cleans up its server, forwarding and both isolated test APKs. The older setup-only `scripts/ci/emulator-smoke.sh` is retained for disposable-emulator troubleshooting; it clears the normal scanner package and should not be used on your interactive demo emulator.

## Physical acceptance still required

Test the TC22 EMDK trigger, rotation/backgrounding, retry after a lost response, revoked approval, leading-zero barcodes and repeated physical scans. Confirm DataWedge scanning works again after QQQ releases ownership. An uncertain submission must retain its event ID; a repeated physical scan receives a new event ID. See [trusted scanner acceptance](ZEBRA-SCANNING.md). Never use sensitive production barcodes in test screenshots or CI artifacts.
