# TODO

- [x] Add runtime server branding and a foreground connection footer with bounded sanitized diagnostics.
- [x] Verify customer header/theme, diagnostics and connection loss/recovery on the emulator.
- [ ] Verify customer-specific logo assets and physical TC22 appearance.

- [x] Complete Gradle unit tests, debug APK, signature, and lint verification (26 tests; zero lint errors).
- [ ] Run physical TC22 acceptance: DataWedge trigger, rotation/background lifecycle, duplicate retry, revoked device, bad credentials, sign-out and re-login.
- [ ] Verify release APK signing/distribution separately; current deliverable is a debug APK.
- [ ] Reconcile historical source license headers with the root license before production distribution.
- [ ] Configure/test production identity-provider integration if selected; version one deliberately supports TABLE_BASED sign-in only and rejects other providers with a clear message.

Offline queue/background synchronization is deliberately outside version one.
- [x] Verify remote Scanner Android workflow after publication.
- [ ] Run the optional hosted emulator lane after its workflow is merged to the default branch.
- [x] Run the native sample instrumentation suite (six passed on API35).
- [ ] Complete the interactive operator login/approval/scan walkthrough on the emulator; it is left ready for user sign-in.
