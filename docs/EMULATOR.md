# Emulator testing

An Android emulator verifies login, enrollment, native navigation, manual barcode submission, and intent handling. It does not reproduce Zebra's scan engine, physical trigger, or DataWedge profile management.

## Prepared Windows workstation

The `QQQ_Mobile_API35` virtual device is already installed under `%LOCALAPPDATA%/Android/Avd`, with Android 15/API35 and WHPX acceleration. To reopen it in PowerShell:

```powershell
$env:ANDROID_AVD_HOME = Join-Path $env:LOCALAPPDATA 'Android/Avd'
& "$env:LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd QQQ_Mobile_API35 -gpu host -no-snapshot
```

This workstation uses the NVIDIA GPU with WHPX CPU acceleration. The AVD also has `hw.gpu.enabled=yes` and `hw.gpu.mode=host` persisted. A cold start avoids restoring old graphics state; `-no-snapshot` preserves installed apps and their data. The [Android acceleration guide](https://developer.android.com/studio/run/emulator-acceleration) documents graphics and VM acceleration separately. If graphics fail on another computer, select a supported renderer for that machine rather than copying the workstation setting blindly.

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

To exercise the same intent adapter used by DataWedge while the scanner screen is visible, send a synthetic development broadcast:

```text
adb shell am broadcast -a io.qrun.mobile.scanner.SCAN -c android.intent.category.DEFAULT -p io.qrun.mobile.scanner --es com.symbol.datawedge.data_string DEMO-001 --es com.symbol.datawedge.label_type LABEL-TYPE-CODE128 --es com.symbol.datawedge.source scanner
```

This validates intent parsing only; the server treats source/symbology as client-reported metadata.

## Automated checks

The GitHub **Scanner Android** workflow always runs JVM tests, lint, and APK assembly. Once its pull request is merged to the default branch, **Run workflow** becomes available; leave **emulator** enabled to run the existing native Compose authentication/process tests and `scripts/ci/emulator-smoke.sh` on a disposable API35 emulator. Reports, the setup-screen screenshot, and UI hierarchy are uploaded as workflow artifacts.

The smoke script refuses physical devices, installs the debug APK, clears the emulator's scanner application data, launches the real activity, and checks that **Connect QQQ Mobile** appears. It does not need a QQQ server. Full operator/device-approval/scan testing uses the companion demo as described above.

## Physical acceptance still required

Test the TC22 trigger and DataWedge profile, scan during rotation/backgrounding, retry after a lost response, revoked approval, leading-zero barcodes, and repeated physical scans. Confirm that an uncertain submission retains its event ID and a repeated physical scan receives a new event ID. Never use sensitive production barcodes in test screenshots or CI artifacts.
