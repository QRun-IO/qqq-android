# TODO

- [x] Correct false legacy network blocking while the scanner's server is reachable; preserve multi-network connectivity for shared clients.
- [x] Persist workstation host-GPU configuration and document cold-start commands.

- [x] Add runtime server branding and a foreground connection footer with bounded sanitized diagnostics.
- [x] Verify customer header/theme, diagnostics and connection loss/recovery on the emulator.
- [ ] Verify customer-specific logo assets and physical TC22 appearance.

- [x] Complete Gradle unit tests, debug APK, signature, and lint verification (26 tests; zero lint errors).
- [ ] Run physical TC22 acceptance: DataWedge trigger, rotation/background lifecycle, duplicate retry, revoked device, bad credentials, sign-out and re-login.
- [ ] Verify release APK signing/distribution separately; current deliverable is a debug APK.
- [ ] Reconcile historical source license headers with the root license before production distribution.
- [ ] Configure/test production identity-provider integration if selected; version one deliberately supports TABLE_BASED sign-in only and rejects other providers with a clear message.
- [ ] Negotiate the UI/UX API `/version` compatible-version range and required capabilities/features before loading native screens; `/qqq/v1` paths alone are not full protocol conformance.
- [ ] Broaden native component rendering and add conformance coverage for supported metadata/components, including unknown enum values/additive API changes; unsupported screens must remain explicit.

Offline queue/background synchronization is deliberately outside version one.
- [x] Verify remote Scanner Android workflow after publication.
- [ ] Verify the hosted native E2E lane after publication; it now runs on pushes and pull requests.
- [x] Run the native sample instrumentation suite (six passed on API35).
- [x] Add and run real-server scanner-host E2E: login, pending enrollment, approval, native app/process selection, manual scan persistence and revoked-device denial. Use an isolated app ID and database.
- [x] Run the native real-server E2E job on pushes and pull requests; CI configuration added, hosted execution remains to be checked after publication.
- [ ] Complete the interactive operator login/approval/scan walkthrough on the emulator; it is left ready for user sign-in.
