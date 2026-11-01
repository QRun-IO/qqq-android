# Disposable scan injection probe

This test application has no network permission, target permissions, Activity, shared UID or external runtime dependency. Its exported control receiver creates new DataWedge-shaped intents under its own ordinary Android UID. It only accepts the disposable `.qa` and `.e2e` scanner package names and synthetic `probe-...` markers. It cannot target the normal scanner package.

Accepted target requests select explicit compile-time package literals before any intent is constructed. The caller's package string is never forwarded to `setPackage`; every other target returns without sending.

Build the QA target against the production factory, its test APK, and the probe:

```text
gradlew --max-workers=2 --no-parallel -PscannerTestBuildType=qa :scanner-host:assembleQa :scanner-host:assembleQaAndroidTest :scanner-security-probe:assembleDebug
```

The ordinary debug key is used only to compile the probe. Before physical acceptance, the operator must re-sign a copy with a different **throwaway test-only key**. The native test rejects equal signing certificates and equal app UIDs. Do not change the normal scanner signing key, generate a release key for this test, or commit any private key/password.

Root coordinates the serial build/device lane. The physical harness requires a private JSON file with `purpose: "scanner-injection-qa"`, an explicit disposable loopback `baseUrl`, and random `scannerPassword`/`adminPassword` values. It does not create or reconfigure the server. The fixture must contain the ordinary scanner demo metadata/processes and an empty dedicated database.

```text
node scripts/ci/scanner-injection-device.mjs <TC22-serial> <private-fixture.json> <separately-signed-probe.apk>
```

The runner refuses existing QA/test/probe installations, installs only those three packages, adds one new non-rebinding reverse and removes only its own installations/reverse. It never installs, clears, targets or changes the normal scanner app or the operator's existing E2E visual demo. It does not wake/unlock the handheld or change scanner profiles, animation settings, USB debugging or Android keys.

The native test requires the real scanner to report ready, keeps the Activity resumed/focused/unlocked, and saves one manual positive control through the real semantic API. A brief instrumentation-only observer proves the exact scan action and typed payload reached the QA package; it cannot submit a scan and is unregistered before the negative attempt. Eight forged intents must produce zero additional server records and zero additional native semantic process-step requests. No screenshots or customer barcodes are captured.

Evidence is written under `scanner-host/build/native-device-qa-injection`: sanitized instrumentation, semantic operation audit and APK hashes. Without `expectedBarcode`, this is an injection-only proof and explicitly records `physicalTriggerVerified: false`.

To add real hardware acceptance in the same signed-in session, add an `expectedBarcode` string to the private fixture JSON. Use a known non-sensitive printed barcode; leading zeroes and spaces are preserved exactly. The runner prints `hardware-initial-ready`, `hardware-recreated-ready`, and `hardware-resumed-ready` status lines. At each line, the operator has up to 120 seconds to aim at that barcode and press the physical trigger **once**. No fake hardware input is injected. The second phase uses Activity recreation; the third stops/resumes only the disposable QA Activity. This does not claim a global screen-orientation/MDM test or change any system setting.

Each phase requires exactly one new `zebra-emdk` server record with the exact configured barcode, a new event UUID, the approved device/operator and the corresponding UI receipt. Full mode requires exactly four native process-step requests: one manual control and three real scans. `physicalTriggerVerified: true` is written only after all three phases and the server/API checks pass. The proof stores the expected barcode's SHA-256 instead of its raw text. Root remains responsible for preparing the non-sensitive target and operator prompts.
