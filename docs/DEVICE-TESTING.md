# Isolated TC22 acceptance

The emulator-only CI entry point continues to refuse physical devices. The explicit physical entry point is limited to an attached Zebra TC22 and requires its serial and one named scenario:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
node scripts/ci/scanner-device-e2e.mjs --serial TC22_SERIAL --scenario session-monitor ../qbit-mobile-scanners
node scripts/ci/scanner-device-e2e.mjs --serial TC22_SERIAL --scenario navigation
node scripts/ci/scanner-device-e2e.mjs --serial TC22_SERIAL --scenario licenses
node scripts/ci/scanner-device-e2e.mjs --serial TC22_SERIAL --scenario orientation
```

Build the matching APKs first, using Java 21 and one bounded Gradle invocation (`--max-workers=2 --no-parallel`). Scanner scenarios need `:scanner-host:assembleE2e :scanner-host:assembleE2eAndroidTest` and the companion demo's installed artifact/classes/test classpath. The navigation scenario needs `:qqqsampleandroidmobileapp:assembleDebug :qqqsampleandroidmobileapp:assembleDebugAndroidTest`.

The handheld must already be unlocked. This runner does not change power, lock, animation, developer, DataWedge or ordinary scanner settings. It refuses existing disposable test packages, installs only separate test applications, and removes only applications installed by that run. The ordinary `io.qrun.mobile.scanner` installation and its data are never cleared or replaced. Scanner scenarios use a temporary local server/database and one new non-rebinding USB reverse, which is removed on cleanup. Existing reverses, including the operator's ordinary server connection, remain in place.

`session-monitor` exercises real login/enrollment/approval, process activity, recreation, actual PixelCopy upload, background/paused-frame clearing, administrator session termination and fresh login. `navigation` runs the shared native navigation, input and account suites with synthetic repository data and verifies request counts/draft preservation. It installs the sample app temporarily and refuses to replace any existing sample installation or retained app data. `licenses` opens the bundled notice list and renders the Google artwork license; `orientation` checks the app's orientation policy. These two scenarios use only the isolated scanner APK/test APK and need no demo server or USB reverse. Other scanner scenario names are the same as [EMULATOR.md](EMULATOR.md).

Evidence is written under `scanner-host/build/native-device-e2e`. Request audits contain operation IDs/status/timing, not credentials or bodies. Do not capture the normal app's screens or customer data as test evidence. Automatic camera transport tests must use synthetic originals; a real camera shot requires the operator to aim at an agreed non-sensitive target. The current implementation and test outcomes are recorded separately in [SESSION-STATE.md](SESSION-STATE.md).

The standalone `orientation` and `licenses` scenarios need only the isolated scanner APKs and no server. Orientation acceptance checks saved policy, actual portrait/landscape configuration, recreation, and a fresh Activity launch for each choice. The session and photo scenarios separately verify that a rotation retains process input and a pending original.

Photo acceptance requires the companion optional Record Files integration's successfully verified isolated fixture. Start `integrations/record-files/Start-ConsumerFixture.ps1` in that repository, keep its returned credential file private, and run:

```powershell
node scripts/ci/scanner-device-photo-e2e.mjs --serial TC22_SERIAL --fixture PRIVATE_CREDENTIAL_FILE
```

This runner accepts only that loopback fixture's expected account/process/parent contract. It does not start, stop or delete the external fixture. Its test intercepts Android camera/picker activity results with a generated image, verifies automatic camera upload and retained server association/hash, then checks pending gallery-original retention across rotation and cleanup on administrator-forced logout. No surroundings are photographed.

Device Settings includes **Orientation**: System default, Portrait, Landscape, Reverse portrait and Reverse landscape. The choice is local to this installation and survives sign-out, server changes and restarts; it does not modify Android's global auto-rotate preference. Android can recreate the Activity when applying a choice. The host retains process ViewModels and saves unsubmitted manual scanner text across that recreation. Pending photo originals remain with the process's retained ViewModel.

The TC22 test target runs Android 14. Android's [requested-orientation API](https://developer.android.com/reference/android/app/Activity#setRequestedOrientation(int)) permits manufacturer overrides; for apps targeting API36 or later, Android16 [ignores orientation restrictions on displays whose smallest width is at least 600dp](https://developer.android.com/about/versions/16/behavior-changes-16#adaptive-layouts). The setting requests the platform policy and does not force transforms or work around those system restrictions.
