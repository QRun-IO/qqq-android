# Session state

2026-10-29: Implementing QQQ Mobile scanner host and native host extensions. First target is Zebra TC22, online only. Backend companion is `qbit-mobile-scanners`; metadata-defined apps/processes remain the native execution model. Device approval is mandatory. Local demo uses QQQ TABLE_BASED auth on QQQ4.1.0-RC.20.

Implemented server selection, per-server enrollment credentials, approval gate, SCANNER widget DataWedge input, explicit same-event retry, native password login, host extension points, and removal of session/token logging from touched authentication paths. No commit or push.

Validation passed: 19 JVM tests (core12, mobileapp4, scanner3), debug APK assembly, APK signature verification, and lint with zero errors. Five lint warnings remain: three inherited Navigation custom-check compatibility warnings and two dependency/catalog notices. Real sanitized demo HTTP metadata/process/init fixtures deserialize through the native DTOs. Server logout, HttpOnly-cookie login, and per-origin header isolation have regression tests. Physical TC22 acceptance remains required.

Final debug APK SHA256: B3E160CE3279C369CEDB425AC6E777A2729CE8A2E430E7D6F46D35C6376F8214.

Repository readiness added: pinned GitHub scanner tests/lint/APK artifacts and optional emulator workflow; CODEOWNERS, contribution/security/conduct guidance, issue/PR templates, Dependabot, changelog, editor/line-ending policy, and scoped agent instructions. `actionlint`, `shellcheck`, `shfmt`, and `git diff --check` passed. Upstream main `63fca89` was fast-forwarded and all implementation files restored without conflicts; the managed CircleCI Android profile and QRun-IO Maven registry updates are preserved. The full Gradle verification command passed again after reconciliation (16 executed tasks, remaining unchanged tasks up to date). Remote CI and emulator acceptance have not yet been recorded here.
