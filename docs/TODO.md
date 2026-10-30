# TODO

- [x] Correct false legacy network blocking while the scanner's server is reachable; preserve multi-network connectivity for shared clients.
- [x] Persist workstation host-GPU configuration and document cold-start commands.

- [x] Add runtime server branding and a foreground connection footer with bounded sanitized diagnostics.
- [x] Verify customer header/theme, diagnostics and connection loss/recovery on the emulator.
- [ ] Verify customer-specific logo assets and physical TC22 appearance.

- [x] Complete Gradle unit tests, debug APK, signature, and lint verification (26 tests; zero lint errors).
- [ ] Run physical TC22 acceptance: DataWedge trigger, rotation/background lifecycle, duplicate retry, revoked device, bad credentials, sign-out and re-login.
- [x] Verify TC22 physical Code128 and QR triggers through DataWedge intent output, visible Saved scan, preserved leading-zero QR data and independently inspected backend provenance; two Code128 triggers plus one QR trigger produced exactly three records.
- [ ] Verify release APK signing/distribution separately; current deliverable is a debug APK.
- [ ] Reconcile historical source license headers with the root license before production distribution.
- [ ] Configure/test production identity-provider integration if selected; version one deliberately supports TABLE_BASED sign-in only and rejects other providers with a clear message.
- [x] Implement public `/qqq/v1/version` compatibility checks before credentials or native screens, including required features and the registered mobile-scanners extension.
- [x] Implement semantic process completion, field/step updates, editable/view forms, backend cancellation and explicit unsupported-component blocking.
- [x] Verify semantic migration JVM suites: 53 tests passed (core 21/mobileapp 23/scanner 9), including hidden fields, explicit null clearing, generic event ID isolation and completion when the server omits `nextStep`.
- [x] Verify companion browser E2E after the semantic migration: six scenarios passed in 2.9 minutes.
- [x] Require Android client-channel discovery; companion real HTTP tests use the Android header on every request and pass.
- [x] Complete final JVM/lint/build verification of this semantic API migration: 53 tests, zero lint errors/six warnings, normal debug and isolated E2E/test APKs.
- [x] Verify all six native scenarios across focused runs and the OpenAPI request audit: incompatible core, missing extension, missing Android channel, baseline/additive scans and native administrative forms.
- [x] Identify the TC22 through ADB, authorize USB debugging, install the latest normal APK with login/enrollment preserved, approve its device record and open native Scan Capture with Connected status. Initial Code128 decoding passed; the remaining physical matrix is still pending.
- [ ] Extend the [documented native component boundary](UI-API.md) with remote possible values, record lists, specialized forms and table/report/download screens as required; add conformance and rendered E2E coverage for each new capability.

Offline queue/background synchronization is deliberately outside version one.
- [x] Verify remote Scanner Android workflow after publication.
- [ ] Verify the hosted native E2E lane after publication; it now runs on pushes and pull requests.
- [x] Run the native sample instrumentation suite (six passed on API35).
- [x] Add and run real-server scanner-host E2E: login, pending enrollment, approval, native app/process selection, manual scan persistence and revoked-device denial. Use an isolated app ID and database.
- [x] Run the native real-server E2E job on pushes and pull requests; CI configuration added, hosted execution remains to be checked after publication.
- [ ] Complete the interactive operator login/approval/scan walkthrough on the emulator; it is left ready for user sign-in.
