# Current status

As of 2026-11-04. Scope: the scanner host and the local Echo workflow only. This is the authoritative current view; earlier rolling notes are historical and kept in [docs/history](history/README.md). Nothing here claims a stable or fully qualified 1.0; a signed preview is published. Checklist: [TODO](TODO.md). Ledger: [FINISH-1.0](FINISH-1.0.md).

## Source

- Protected `main` contains all application work (PR 8 and PR 10 merged). Application source is unchanged by the documentation and signing work after that merge.
- The Gitea workflow `.gitea/workflows/qrun-android-release.yaml` exists on `main`; it calls the shared platform Android workflow and is not a scanner-APK release pipeline (see [RELEASING](RELEASING.md)).
- Documentation is published separately (signing guide and this reconciliation). Shared CI/CD redesign is outside this repository's local use and does not gate the demo.

## Local demo: accepted

- Local server on the workstation (port 57832): public QQQ 4.1.0-RC.21 plus released Printing 1.0 and qualified document, files and scripting owner inputs. UI API 1.10, mobile-scanners 1.5, printing 2.3, mobile-record-files 1.0.
- TC22 runs the development-signed debug APK (version 0.1.0, versionCode 1, SHA-256 `38A11155BF6A9C089A1EE6D9E44401E8FF39497B7150B51A85F1F916148BE60D`), reaching the server through device port 8080 forwarded to the workstation.
- The operator reported the outstanding manual TC22 acceptance group ALL PASS on 2026-11-04, including label count/fidelity, camera and GPS, and screen viewing. This is operator-reported acceptance, not instrumented or per-case evidence; no per-case artifacts are recorded and the checks are not requested again.
- Desktop-browser Authentik acceptance passed with strict TLS (nonce rejection, login, company logout, fresh second operator).

## MC9400 (second device)

- A production-signed 1.0.0 (versionCode 2) APK was installed on a fresh MC9400 (Android 13, EMDK present) and launched; installed bytes matched the signed APK hash. No fatal crash at startup.
- At the user's request the MC9400 now runs the same development APK as the TC22 in demo mode, with device port 8080 forwarded to the workstation server over paired wireless ADB. It signed in and was approved through the ordinary mobile device approval process (status APPROVED).
- **Remaining check:** a physical MC9400 trigger scan confirmed in the backend. Not yet verified; no scan claim is made.

## Signed release

- Direct signed-APK / MDM distribution was chosen. A QRun-owned key exists (alias `qqq-mobile-release`, certificate SHA-256 `BDDB2D2F45A5925C643A83ABAF616C1EAB2387DC5CDA7B645A6F4C0D5B17A9FF`) with a verified backup. Key, passwords and vault details are not in this repository.
- Signed `QQQ-Mobile-1.0.0.apk` (versionCode 2, SHA-256 `EE5AC896B8985AF6F11B2E883955925D6571A2913D74A4AACCDB564B79394D51`) exists locally, built from `main` application source: lint 0 errors/0 warnings, v2/v3 signature with the exact certificate, alignment and non-debuggable verified.
- It is published as a **signed preview**: Gitea prerelease [scanner-v1.0.0](https://git.kof22.com/QRun-IO/qqq-android/releases/tag/scanner-v1.0.0) (ID 1361, signed tag on protected `main` `3eef6431ca39b6450cf764424d921a02b2afb9ab`; app source matches `0ae2101f`, the tag adds documentation only). Hosted assets are the APK (14933499 bytes) and `SHA256SUMS` (88 bytes), both downloaded back and matching the hash above. The local installation kit ZIP was not published. Forge sign-in is required. This is not a stable release. No automatic signing CI is configured, and SDK CI publication is a separate path.
- Production qualification (still pending) needs an HTTPS server and a full device pass of the signed build ([RELEASING](RELEASING.md), [INSTALLATION](INSTALLATION.md)). The local demo does not need HTTPS; release builds reject HTTP.

## Open (not claimed)

- Release qualification of the signed APK end to end (HTTPS server, enrollment, scan, navigation, update).
- Actual on-device Authentik callback: unverified; no separate evidence, remains production qualification.
- Dependency publication: the Generation, Record Files and Scripting owner inputs behind the local server are qualified inputs, not a fully public, reproducible or released cohort.
- Company profile / MDM rollout: nothing applied or verified on a device ([DEVICE-PROVISIONING](DEVICE-PROVISIONING.md)).
