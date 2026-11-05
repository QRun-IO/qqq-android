# Checklist

As of 2026-11-04, scanner host and local Echo scope only. Narrative: [SESSION-STATE](SESSION-STATE.md). Completed and superseded items are kept in [docs/history](history/README.md).

## Local demo (accepted)

- [x] Application work merged to protected `main`; `.gitea/workflows/qrun-android-release.yaml` present.
- [x] Local server on port 57832 (QQQ 4.1.0-RC.21, Printing 1.0, qualified owner inputs) running UI API 1.10, mobile 1.5, printing 2.3, record-files 1.0.
- [x] TC22 on the development APK against that server; operator reported the outstanding manual acceptance group ALL PASS (2026-11-04), including label count/fidelity, camera, GPS and screen viewing; operator-reported, not instrumented or per-case evidence.
- [x] Echo setup (printer, label size, document, copies 1–100) and semantic native flow qualified, with simulated inputs/printing in disposable runs.
- [x] Real desktop Authentik acceptance with strict TLS.

## MC9400 remaining check

- [x] Production-signed APK installed and launched on a fresh MC9400; hash read back.
- [x] Switched to the TC22 development APK demo configuration at the user's request; signed in and approved (APPROVED).
- [ ] Physical MC9400 trigger scan reaches the backend (Saved scan and a matching server record).

## Release qualification

- [x] Signed-APK/MDM channel chosen; QRun-owned key created with verified backup; signed 1.0.0 (versionCode 2) built and verified locally.
- [x] Direct-install guide written ([INSTALLATION](INSTALLATION.md)).
- [ ] Qualify the production-signed APK on a device against an HTTPS server (enrollment, scanner path, navigation, update).
- [x] Signed preview published: Gitea prerelease `scanner-v1.0.0` (APK and `SHA256SUMS` only; local install kit not hosted).
- [ ] Distribution to the remaining devices after qualification; MDM later. Promotion to a stable release needs the qualification above.
- [ ] Automatic signing/publication pipeline: not configured; owned by the shared CI design, not needed for manual distribution.
- [ ] Actual on-device Authentik callback (unverified; no separate evidence).

## Dependencies and provisioning

- [ ] Public, reproducible publication of the Generation, Record Files and Scripting owner inputs (currently qualified inputs).
- [ ] Company profile / EHS / OEMConfig / MDM deployment verified on a device ([DEVICE-PROVISIONING](DEVICE-PROVISIONING.md), [COMPANY-PROFILE](COMPANY-PROFILE.md)).
- [ ] Legacy source follow-ups ([LEGACY-SOURCE-FOLLOWUPS](LEGACY-SOURCE-FOLLOWUPS.md)).
- [ ] Extend the [native component boundary](UI-API.md) (possible values, record lists, specialized forms, reports) with conformance and E2E coverage as needed.

Offline queueing and background synchronization are outside version one.
