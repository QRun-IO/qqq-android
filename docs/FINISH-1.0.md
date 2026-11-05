# Finish ledger for 1.0

As of 2026-11-04. A signed 1.0.0 preview is published (prerelease `scanner-v1.0.0`); there is no stable or fully qualified 1.0. Older ledgers are in [docs/history](history/README.md); the checklist is [TODO](TODO.md).

| Area | State | Evidence / limit |
| --- | --- | --- |
| Source | Merged to protected `main` | Application source for the tested APKs; Gitea workflow present |
| Local demo | Accepted | Server on port 57832; TC22 development APK; operator-reported manual acceptance ALL PASS 2026-11-04 incl. label count/fidelity, camera, GPS, screen viewing (not instrumented, no per-case artifacts) |
| Echo | Local semantic flow qualified | Printing and inputs simulated in disposable runs; physical label count/fidelity operator-reported pass (not instrumented) |
| MC9400 | Demo-configured and approved | Physical trigger scan to backend not yet verified |
| Signed APK | Published as signed preview | [scanner-v1.0.0](https://git.kof22.com/QRun-IO/qqq-android/releases/tag/scanner-v1.0.0), 1.0.0 / versionCode 2; APK and `SHA256SUMS` only, download-verified; installed and launched once on an MC9400 before the demo switch; no end-to-end qualification |
| Release | Preview only, not stable | Needs HTTPS server and device qualification (enrollment, scanning, in-place update) before stable; no signing CI |
| Authentik | Desktop passes; on-device callback unverified | No separate native provider/callback evidence |
| Dependencies | Qualified inputs, not a public cohort | Generation, Record Files, Scripting owner inputs behind the local server |
| MDM / company profile | Prepared, unverified | Nothing applied on a device |

## Pointers

Release procedure and key custody (no secrets): [RELEASING](RELEASING.md). Installation: [INSTALLATION](INSTALLATION.md). Authentik: [REAL-AUTHENTIK-ACCEPTANCE](REAL-AUTHENTIK-ACCEPTANCE.md). Physical scanner evidence: [PHYSICAL-TC22-ACCEPTANCE](PHYSICAL-TC22-ACCEPTANCE.md).
