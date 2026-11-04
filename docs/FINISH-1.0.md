# Finish ledger for 1.0

As of 2026-11-04. This is not a release and no 1.0 is published. Older ledgers are in [docs/history](history/README.md); the checklist is [TODO](TODO.md).

| Area | State | Evidence / limit |
| --- | --- | --- |
| Source | Merged to protected `main` | Application source for the tested APKs; Gitea workflow present |
| Local demo | Accepted | Server on port 57832; TC22 development APK; operator-reported manual acceptance ALL PASS 2026-11-04 incl. label count/fidelity, camera, GPS, screen viewing (not instrumented, no per-case artifacts) |
| Echo | Local semantic flow qualified | Printing and inputs simulated in disposable runs; physical label count/fidelity operator-reported pass (not instrumented) |
| MC9400 | Demo-configured and approved | Physical trigger scan to backend not yet verified |
| Signed APK | Built and verified locally, not distributed | 1.0.0 / versionCode 2; installed and launched once on an MC9400 before the demo switch; no end-to-end qualification |
| Release | Not done | Needs HTTPS server, device qualification and distribution; no tag, no publication, no signing CI |
| Authentik | Desktop passes; on-device callback unverified | No separate native provider/callback evidence |
| Dependencies | Qualified inputs, not a public cohort | Generation, Record Files, Scripting owner inputs behind the local server |
| MDM / company profile | Prepared, unverified | Nothing applied on a device |

## Pointers

Release procedure and key custody (no secrets): [RELEASING](RELEASING.md). Installation: [INSTALLATION](INSTALLATION.md). Authentik: [REAL-AUTHENTIK-ACCEPTANCE](REAL-AUTHENTIK-ACCEPTANCE.md). Physical scanner evidence: [PHYSICAL-TC22-ACCEPTANCE](PHYSICAL-TC22-ACCEPTANCE.md).
