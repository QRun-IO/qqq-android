# Repository guidance

Read `README.md`, `docs/SESSION-STATE.md`, and `docs/TODO.md` for current scope. Preserve unrelated changes and follow the user's current authorization for commits, publication, and deployment.

## Build and tests

Use the included Gradle wrapper, Java21, and Android SDK35. `local.properties`, build outputs, SDK paths, and credentials are local-only.

```text
./gradlew :qqq:android:core:testDebugUnitTest :qqq:android:mobileapp:testDebugUnitTest :scanner-host:testDebugUnitTest :scanner-host:lintDebug :scanner-host:assembleDebug
```

Run `:qqqsampleandroidmobileapp:connectedDebugAndroidTest` on an emulator for changes to native authentication/navigation/process rendering. `scripts/ci/emulator-smoke.sh` deliberately refuses physical devices and clears only the disposable emulator's scanner application data. Physical TC22 tests remain necessary for DataWedge integration.

All mobile processes require end-to-end coverage. For scanner flow changes, build `:scanner-host:assembleE2e :scanner-host:assembleE2eAndroidTest` and run `node scripts/ci/scanner-e2e.mjs ../qbit-mobile-scanners` against an emulator after building the companion demo/classpath as documented in `docs/EMULATOR.md`. This drives the real host and real server in the separate `io.qrun.mobile.scanner.e2e` application with a temporary database; never clear the operator's normal scanner application to run it. Server record actions must also be covered through both single-record and selected-bulk UI paths in the companion qbit's browser suite.

## Design and safety

Reuse the existing `core` and `mobileapp` libraries and native QQQ metadata/process contracts. The scanner host adds enrollment and scanner widgets; do not introduce a competing screen engine. Preserve raw barcode strings, event identity across retries, server acknowledgments, and server-side device authorization.

Never log or place passwords, session cookies, or device tokens in process values, test fixtures, or documentation. Keep enrollment credentials and sessions isolated by server. Keep HTTP support limited to debug builds. Test resources under `mobile-contract` are sanitized real demo responses.

## Repository automation

CircleCI is managed by `kingsrook/qqq-orb` using `.qqq-ci.yml`; preserve that lane and its main-only library publication. GitHub `Scanner Android` checks build and deliver a debug APK artifact and never publish libraries or production releases. Do not replace managed CI with a stale local template.
