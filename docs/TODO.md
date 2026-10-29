# TODO

- [x] Complete Gradle unit tests, debug APK, signature, and lint verification (19 tests; zero lint errors).
- [ ] Run physical TC22 acceptance: DataWedge trigger, rotation/background lifecycle, duplicate retry, revoked device, bad credentials, sign-out and re-login.
- [ ] Verify release APK signing/distribution separately; current deliverable is a debug APK.
- [ ] Reconcile historical source license headers with the root license before publication.
- [ ] Configure/test production identity-provider integration if selected; version one deliberately supports TABLE_BASED sign-in only and rejects other providers with a clear message.

Offline queue/background synchronization is deliberately outside version one.
- [ ] Verify remote Scanner Android workflow after publication and run the optional emulator lane.
- [ ] Run the native sample instrumentation suite and full interactive scanner demo on an emulator.
