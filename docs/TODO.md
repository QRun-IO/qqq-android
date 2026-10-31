# TODO

- [ ] Qualify semantic Authentik/OAuth2 sign-in: API1.9 advertisement and native marker, browser/PKCE/state/prompt, callback/lifecycle/cookie cleanup, new native tests, new dependency notices and actual configured-provider/operator-switch acceptance. See AUTHENTICATION.md.

- [ ] Qualify [semantic OAuth2/Authentik browser authentication](AUTHENTICATION.md): API1.9 native-session advertisement, PKCE callback/lifecycle tests, generated notices, and real configured Authentik acceptance.
- [ ] Verify the single hamburger drawer correction: no duplicate Apps/Switch app controls, fixed Settings/Diagnostics, prelogin/approval gating, drawer Back preserving drafts, and guarded Home/app switching with exactly one cancellation.

- [x] Verify persisted device Orientation choices on TC22, including process draft and pending-photo preservation across rotation.

- [x] Implement and verify [navigation UX](NAVIGATION-UX.md): primary Apps/app/process navigation, secondary Account/Settings/diagnostics, shared Back/leave policy, protected in-flight operations and sign-out lifecycle.
- [x] Prepare independent scanner release workflow and environment-only signing/version configuration; actionlint passes.
- [ ] Independently review release automation, configure the organization signing identity, verify actual signed APK and upgrade/migration, and publish only after all [release prerequisites](RELEASING.md) pass.

- [x] Correct false legacy network blocking while the scanner's server is reachable; preserve multi-network connectivity for shared clients.
- [x] Persist workstation host-GPU configuration and document cold-start commands.

- [x] Add runtime server branding and a foreground connection footer with bounded sanitized diagnostics.
- [x] Verify customer header/theme, diagnostics and connection loss/recovery on the emulator.
- [ ] Verify customer-specific logo assets and physical TC22 appearance.

- [x] Complete Gradle unit tests, debug APK, signature, and lint verification (26 tests; zero lint errors).
- [ ] Run physical TC22 acceptance: DataWedge trigger, rotation/background lifecycle, duplicate retry, revoked device, bad credentials, sign-out and re-login.
- [x] Verify TC22 physical Code128 and QR triggers through DataWedge intent output, visible Saved scan, preserved leading-zero QR data and independently inspected backend provenance; two Code128 triggers plus one QR trigger produced exactly three records.
- [ ] Verify release APK signing/distribution separately; current deliverable is a debug APK.
- [x] Reconcile exact historical project-owned source license boilerplate and the IDE template with the existing Apache-2.0 migration; preserve copyright ownership and other notices.
- [ ] Verify third-party asset/dependency attribution and signed release/upgrade delivery before production distribution.
- [ ] Configure and test production identity-provider integration; TABLE_BASED remains supported, and native OAuth2 requires explicit server advertisement and callback registration.
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
- [x] Implement semantic mobile session activity, server termination, demand-based own-app screen viewing and optional foreground location.
- [x] Add capture lifetime/lease/race tests, complete scanner receipt checks, strict maxLength and NavController regression coverage.
- [x] Verify short-viewport login and focused long-form submission after IME Done and dismissal handling.
- [x] Complete integrated session/native administrator termination E2E and all 12 shared TC22 navigation/input/account tests; local emulator remains closed.
- [ ] Freeze integrated revisions and complete security/architecture review before publishing session feature.
- [x] Integrate camera/gallery with Record Files typed routes and guarded optional bridge fixture; synthetic native E2E verifies persisted receipt, rotation and forced-logout cleanup.
- [ ] Complete actual camera-lens/operator acceptance after final runtime installation; synthetic activity results do not prove the OEM camera application flow.
- [ ] Verify fresh hosted Scanner Android checks for the October 31 review checkpoint; earlier hosted results do not qualify this head.
- [ ] Verify the hosted native E2E lane after publication; it now runs on pushes and pull requests.
- [x] Run the native sample instrumentation suite (six passed on API35).
- [x] Add and run real-server scanner-host E2E: login, pending enrollment, approval, native app/process selection, manual scan persistence and revoked-device denial. Use an isolated app ID and database.
- [x] Run the native real-server E2E job on pushes and pull requests; CI configuration added, hosted execution remains to be checked after publication.
- [ ] Complete the final operator walkthrough on TC22 after updating the preserved demo/runtime, including outage/reconnect and real camera capture.
