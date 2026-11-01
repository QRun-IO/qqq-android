# TODO

- [x] Verify current TC22 Code128 hardware trigger-to-save on the trusted EMDK path with operator Saved scan, matching backend source/model and distinct event identities; see [physical evidence](PHYSICAL-TC22-ACCEPTANCE.md).
- [ ] Finish current EMDK leading-zero QR, repeat-intent, lifecycle/ownership and retry/leave acceptance; historical DataWedge scans do not qualify these checks.

- [x] Verify the synchronized rotation/recreation assertions in both5ba GitHub native runs; one entire run passed and the other reached a later fresh-login timeout.
- [x] Verify final fresh-login readiness at229fce3 in both GitHub native runs:35 shared cases, PixelCopy and all seven semantic scenarios pass, including completed logout, enabled login action and a new session. All eleven hosted checks pass; the later portrait-default update requires its own fresh checks.
- [x] Make portrait the default for missing/invalid preferences while preserving explicit choices; four focused tests, lint/debug assembly and preserving TC22 install pass, with actual requested portrait orientation confirmed.
- [ ] Finish the operator walkthrough on the newly updated normal TC22 app; private data/enrollment and the development signing identity were preserved.
- [x] Compile the opt-in screen-consent QA harness and ordinary E2E tests; verify early opt-in/protected-port safety gates. Actual permission/display acceptance remains open.
- [x] Compile/review additive consent-based device-screen client and paired1.2 server:137 JVM, package/lint/APK inspection, ordinary-UID TC22 scan-input rejection,39 server/six demo/all15 browser cases and strict provenance pass. Actual projection acceptance remains separate.
- [ ] Execute opt-in actual Android consent, notification, Home/background pixels, Stop, lease expiry, lock/rotation and forced-session-end acceptance on the refined device-screen candidate.
- [ ] Resolve CircleCI job 140's three unfocused-Activity Back failures using the new bounded window-owner diagnostics; retain real Back and the five-second guard. CodeQL on `6845abc` passed, but native CI is not fully green.
- [ ] Repeat physical EMDK acceptance after the unsuccessful first-stage QA attempt preserved in `C:/Users/james/.cache/qqq-tc22-physical-attempt-20261101-0210`; no physical trigger proof is established. Preserve the 120-second operator stage bound and explicit timeout reporting.
- [x] Resolve normal backend ownership and perform the reviewed API 1.9 upgrade with fresh backup, original-column/data checks, recovery logins and live account validation; preserve the operator APK. Private verification confirms two devices and three scans retained.

- [x] Verify five client audit fixes: password/legacy callback origin binding, independent local Auth0 sign-out, sample release HTTPS and no-backup session storage. The batch passes 109 JVM tests and all 32 selected TC22 native cases; packaged release controls are inspected. See [review scope and evidence](SECURITY-REVIEW-2026-10-31.md).
- [ ] Close the remaining unauthenticated scanner-ingress finding using actual EMDK hardware/adversarial acceptance. The approved implementation passes 49 scanner JVM tests, lint and all package builds; no production input change is installed yet.
- [x] Correct the demo's mixed RC.20 runtime and test-engine dependencies; qualify exact-source core `ecf0f550a0357cec6387c2b54efca128a405a629` with server `b14d925b741e815d1cfa4f62301f19207f25b03f`. Fresh37 server/five demo, strict provenance and all15 nullable-admin/session-monitor/browser cases pass. Core full closure:4,507 discovered/16 existing skips/zero failures/errors.
- [ ] Diagnose hosted Back-test window-focus failures from bounded state flags while retaining real Back injection and the focus guard.
- [x] Qualify stable Auth03.0.0/Coil3.0.4, notice-generator incremental inputs and overlay/window-focus scan gating:127 JVM,34 shared TC22 cases, an additional eight-overlay native regression, ordinary-UID injection rejection and all package/lint/notice gates pass. See [candidate acceptance](TRUSTED-SCANNER-ACCEPTANCE.md).
- [ ] Resolve entire-handheld versus QQQ-app viewing scope. Current capture covers QQQ Mobile; it must not be represented as Android Home/other-app capture. Implement and qualify full-device consent/capture if that is required.

- [x] Verify the unconfirmed-scan exit correction: 46 mobile JVM tests and all 21 selected TC22 native cases pass, including failed cancellation and explicit Leave anyway with preserved receipt/retry identity and in-flight/photo guards.
- [ ] Finish the operator walkthrough of the unconfirmed-scan exit correction on the normal scanner installation.

- [x] Qualify semantic OAuth2 client protocol/policy and generated notices; all seven synthetic browser-result cases and the selected native navigation suite passed on TC22. See [acceptance evidence](NATIVE-AUTH-ACCEPTANCE.md).
- [ ] Complete actual configured Authentik acceptance: real browser/exported-callback round trip, authorization rotation/process death, and next-operator switching with prior browser SSO. See [authentication deployment](AUTHENTICATION.md).
- [x] Verify the single hamburger drawer correction: no duplicate Apps/Switch app controls, fixed Settings/Diagnostics, signed-out gating, drawer Back preserving drafts, and guarded Home/app switching with exactly one cancellation.

- [x] Verify persisted device Orientation choices on TC22, including process draft and pending-photo preservation across rotation.

- [x] Implement and verify [navigation UX](NAVIGATION-UX.md): primary Apps/app/process navigation, secondary Account/Settings/diagnostics, shared Back/leave policy, protected in-flight operations and sign-out lifecycle.
- [x] Prepare independent scanner release workflow and environment-only signing/version configuration; actionlint passes.
- [x] Independently review the exact release workflow, reusable verification and Gradle signing configuration; see [review scope and hashes](RELEASE-AUTOMATION-REVIEW.md). No actionable source finding; external protection settings are not verified by this review.
- [ ] Configure reviewed GitHub release protections and the organization signing identity, verify the actual signed APK and upgrade/migration, and publish only after all [release prerequisites](RELEASING.md) pass. Signing/distribution remains owner-deferred until the final step.

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
