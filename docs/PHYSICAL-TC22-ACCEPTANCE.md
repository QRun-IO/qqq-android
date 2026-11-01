# Physical TC22 acceptance — November 1, 2026

This records current development-package evidence, not production release acceptance. The normal application was updated in place after signer verification and private-data backup. The emulator remains off.

| Component | Exact tested checkpoint |
| --- | --- |
| Development APK SHA-256 | `F3AF638E34AA0B6FB44D789362BED2838202EDAF681DF775F2A5FAD3AB3E9FAA` |
| Production source checkpoint | `e04fdb2d21d4f84253f12137af106bbcb0344a19` |
| Backend core | `ecf0f550a0357cec6387c2b54efca128a405a629` |
| Running scanner server | `b14d925b741e815d1cfa4f62301f19207f25b03f`, UI API1.9/mobile-scanners1.1 |

Direct display inspection confirmed sign-in, the Apps menu, Scan Capture and “Scanner ready.” The operator then reported “Saved scan.” A direct, ownership-guarded backend query verified eight new `QQQ-TEST-123456` records with `scanSource=zebra-emdk`, `symbology=CODE128`, TC22 model attribution and eight distinct event identities. Their device capture times run from `2026-11-01T11:02:17.572512Z` through `2026-11-01T11:02:40.208343Z`. Device capture and server receipt times are separate clock observations.

This proves a trusted hardware trigger-to-save positive control. The operator confirmed the captures came from multiple deliberate trigger pulls. Historical DataWedge records were excluded from this result. The normal database has eleven records at the observation, including its three preserved historical scans; no disposable instrumentation was run over this walkthrough.

| Required behavior | Current proof |
| --- | --- |
| Code128 trigger to durable scan | Verified on the current EMDK path, with operator Saved scan and matching backend records. |
| Leading-zero QR `001234567890` | Current QR displayed; operator/backend confirmation pending. Historical DataWedge QR does not satisfy this gate. |
| Separate repeated trigger intent | Eight distinct events observed; operator confirmed multiple deliberate pulls. |
| Rotation/recreation, background/resume, scanner ownership restoration | Physical matrix pending; native synthetic/hosted evidence is separate. |
| Connection failure, uncertain receipt/retry, leave/cancel and switching apps | Operator walkthrough pending; automated regressions remain separately scoped. |
| Remote termination and fresh login | Final hosted verification and current operator checks pending. |
| Actual projection, camera/gallery and GPS | Separate consent/provider/runtime gates; not implied by barcode capture. |

The reported disappearance before sign-in was inspected directly: the process remained alive/resumed/focused, the crash buffer was empty and the display showed connected sign-in. No app crash or product fix is established by that observation. The desktop mirror was black; direct local ADB screenshots provided the usable display evidence.

The later portrait-default update produced development APK `C89161C522AA7DFFE09C009ABDE769C1BADB7E6C64BFCE699857062A1CEC6340`. Four focused policy tests, debug lint (zero errors/warnings) and debug assembly pass. It was installed in place with a matching signer and another private-data backup. Direct Android inspection confirms the normal scanner Activity resumed with `SCREEN_ORIENTATION_PORTRAIT`; the display shows its signed-in Apps menu. Missing/invalid settings now default to portrait; explicit saved choices remain intact. The barcode observations above belong to the earlier exact APK, and current QR/lifecycle checks remain pending after this update.
