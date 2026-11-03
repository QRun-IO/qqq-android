# Finish ledger for 1.0 (current: November 3, 2026)

Nothing here is a 1.0 release. History is in SESSION-STATE.md.

## Source published (not merged, not released)

- Android `f72b90d08cf29d2785cef1d5c5e7eeaf75f9afd2`: signed application code published in PR 10 (read back over SSH before this documentation update). Gitea CI 3480 succeeded: unit-test, device-test, package, secrets, gate. A clean git merge-tree with `main` b75fdac was verified; nothing was merged. Pull requests #8 and #10 remain open. This documentation update leaves runtime code unchanged.
- Server `c0e27b36fd16c00eaca3451af3df43f4deedada1`: signed and published in Gitea PR 7, including the verified QQQ 4.1.0-RC.21 dependency update and Record Files test fix.
- Generation copies: signed `aac68907e67906aeef4e0cd687d28bc98b738f8c` on `feature/echo-generation-copies` (base PR 343 head `8f473fa`), 13 paths, 21/21 manifest match, real PostgreSQL 17 migration passes. Owner integration and publication are not acknowledged.

## Implemented and installed (normal TC22 and local server 57832)

- APK `4AAB6951D3B0ABDDD4F5A8DC6790DCD84233EE08179B68DB108BF77909AB850E`: same signer, no data clear. Connected, Apps/Settings, portrait, location on and the brand-only wallpaper preview were verified. No wallpaper or company profile was applied. The device shows Echo setup, Copies 1.
- Server on 57832: UI API 1.10, mobile-scanners 1.5, printing 2.3, mobile-record-files 1.0, with the original database, accounts, approval and printer. Echo Copies per scan (default 1, range 1-100, one job per scan). Its core is not upgraded to RC.21.

## Tested

- Android: 222 JVM tests, lint 0 errors/0 warnings (2 info), debug/E2E/test APK builds, 23 Node tests, 255-file standards.
- Disposable TC22 Echo ten stages, 109/109 semantic requests; inputs and printing are simulated.
- Server RC.21 gate PASS: 377 unique tests (Generation 179, Printing bridge 16, scanner 53, demo 8, mobile Record Files 18, Echo 68, Echo demo 35), zero failures/errors/skips, Checkstyle PASS, plus 15 real admin-browser E2E on public RC.21. Earlier MySQL timeouts did not recur. Evidence: `C:/Users/james/.cache/qqq-rc21-copies-gate-076d059d8d0641d9a85874ec64008def/verified-summary.json`.
- Honest scope: this is public core RC.21 plus qualified owner inputs. The Printing-bridge tests still resolve three private `pu507` libraries through the owner Printing POM, and other owner qbits are private inputs. It is not a fully public or released cohort.
- Real Authentik desktop acceptance passes with strict TLS (nonce, A, logout, fresh B). The native harness is compiled with 8 launcher guards passing.

## Still open (none of these is claimed done)

- Generation: canonical owner integration, then public reproducible inputs for the owner qbits and a coherent published cohort.
- Source: primary CI on the final source, merge, review completion and release.
- Manual on the TC22 (operator remote): physical trigger, scan value/type, AIM, label count, rescan, photo capture/upload, GPS, projection, company profile/wallpaper deployment.
- Native Authentik on the device: temporary CA installation, PIN interaction and the real callback. No trust bypass.
- Signing and distribution: explicitly last, by user decision.

## Pointers

Runtime `C:/Users/james/.cache/qqq-local-all-qbits-current.txt`; native proof `scanner-host/build/native-device-e2e/echo-proof.json`; Generation handoff `C:/Users/james/.cache/qqq-generation-copies-handoff.md`; Authentik `docs/REAL-AUTHENTIK-ACCEPTANCE.md`.
