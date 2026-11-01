# Trusted scanner candidate acceptance

Current status: this page retains its dated/source-specific checkpoint. Current installed C891/earlierF3AF EMDK Code128 proof is in [physical acceptance](PHYSICAL-TC22-ACCEPTANCE.md); QR and actual projection remain pending. Android7de passes all eleven hosted checks. Older statements about then-pending trigger/CI/scope or unchanged normal APK are historical, not the current release gate. Normal server remains mobile1.1; optional DEVICE_SCREEN requires qualified1.2 source and actual device consent acceptance.

Verified October 31, 2026. This is a source-review and development-package checkpoint, not a production release or actual physical-trigger acceptance.

## Executed checks

- **127 JVM tests** pass: core30, mobile46, scanner51. Debug/release lint and debug, unsigned release, E2E, QA and instrumentation package gates pass.
- **34 native TC22 cases** pass in115.717 seconds, covering navigation, input, authentication lifecycle, host storage, and real PNG/SVG/error rendering.
- **One additional TC22 overlay case** passes in17.948 seconds through the actual Main and scanner widget. Drawer Back, Settings, Diagnostics, Account, Leave, Sign out, a separate window and a parent host gate reject hidden input; dismissal preserves the current draft and permits exactly one new event. The hardware driver in this case is synthetic. One real Android Back closes the drawer without opening Leave, changing the process or cancelling it.
- **A separate ordinary-UID injection case** passes against the production-path QA APK. A distinct-signer application with no permissions sends eight forged broadcasts. A temporary observer proves the actual scan action/payload route; after that observer is removed, no forged scan creates a process step or record. One explicit manual positive control receives a real server receipt. The EMDK driver reaches ready; no physical trigger is inferred.
- Every native request in that server acceptance matches the documented semantic API. The isolated fixture uses core `ecf0f550a0357cec6387c2b54efca128a405a629`, server `b14d925b741e815d1cfa4f62301f19207f25b03f`, UI API1.9 and mobile-scanners1.1. Its separate server37/demo5/browser15 and runtime-provenance gates pass.

Independent review of the final SDK/navigation source found no concrete defects requiring changes. The earlier deep audit remains a scoped historical report for `d1de0bf`; it does not certify every later change or external implementation.

## Inspected artifacts

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `89e74b7af682f199996322fd271115db1ffaa99e12c2b153a53815025b616e9a` |
| Unsigned release APK | `0e412ea62a8cbbb5f867b70eeff6a1ffb3b5bdab5199576e387684cb517f365b` |
| E2E APK | `80ef10d1109a355fba82f99b48a92d6b1b04907247adc093e0664b5e59f5b694` |
| Production-path QA APK | `cfff47b5a03d578a21fa69583111b23c225a4998f0cf795502d379dcb93ab017` |

All four actual APKs contain131 notice entries, nine complete license texts and the eight reviewed native libraries with unchanged hashes. Auth0 resolves to3.0.0 and all seven Coil modules to3.0.4; no Google identity or Credential Manager dependencies were introduced. Each packaged notice resource equals its generated report byte-for-byte.

Standard SDK DEX inspection finds no defined EMDK SDK implementation classes. Debug, release and QA exclude the synthetic scan action, source label and receiver definitions; E2E contains the expected positive control. All variants disable backup with ten cloud/transfer exclusions. Release also disables debugging and cleartext traffic. The vendor library remains optional and device-provided.

The notice collector now declares its resolved dependency coordinates as an incremental input. Changed versions regenerate standard notices; a subsequent unchanged QA build reuses collection/preparation while the strict runtime/version/license verifier still executes. No license allowlist or runtime-absence check was weakened.

## Remaining acceptance

Actual Code128/QR trigger-to-save, leading zeros, distinct repeated trigger events, recreation/background/resume and scanner-ownership restoration remain physical checks. Ordinary-UID rejection alone could not establish that the hardware feature works.

Configured Authentik, actual camera/GPS/customer assets, coherent optional Record Files deployment, final hosted-client checks and preserving operator upgrade remain separate gates. Current viewing captures QQQ Mobile; entire-handheld viewing scope is awaiting owner clarification. Production signing/distribution is deferred to the final step. Normal operator data and application were preserved during these disposable tests; the emulator remained off.
