# Device-screen viewing acceptance

The optional `DEVICE_SCREEN` mode requires the additive mobile-scanners1.2 contract. `APP_CONTENT` remains the default for older servers. The device operator must explicitly enable notifications, accept the disclosure and approve Android's fresh screen-sharing dialog. No consent is persisted. The foreground notification and Android indicator allow stopping; sharing also stops on host disposal, lock, session loss or bounded lease/watchdog expiry. Remote revocation is learned through subsequent requests, rather than an instantaneous push.

The initial source checkpoint passes135 JVM tests with no failures/errors/skips, scanner debug/release lint, and debug/unsigned-release/E2E/QA plus native-test package builds. Its paired server passes39 tests and six real-HTTP demo tests using qualified core `ecf0f550`, with no artifact installation and unchanged historical1.0/1.1 API baselines. The frozen client diff received parent and fresh independent static review: all nine inventoried changed files inspected, no reportable findings. The sealed security scan is `ea8a20a8-8a16-48d8-9c58-a9376387fb60`; its tool reports4,884,517 aggregate tokens across two threads. This is a changed-file static review, not a new full-repository audit or device acceptance.

The initial candidate hashes are:

| APK | SHA-256 |
| --- | --- |
| Debug | `27A7B6D27A9B2439FE67BABB8950FA9E0EA13099D94383DBE340FB838BBA6DD2` |
| Unsigned release | `C80D2012096621D3654242EAB5B4119BF6DC9AF107EEA9FABF52505B5C957A70` |
| QA | `2822E45CA9FE39F47B7B620E630342C68E03E8753827CE04BBDD814370003C42` |
| E2E | `F6422F428511C7F1F6C8670D90CFEF20F0476696C21B8BD19948C762386F970B` |

Actual OS consent, notification visibility, fresh pixels on a static screen, other-app/background capture, lock, rotation/recreation, administrator disconnect and forced logout remain required device acceptance. A projection may exist locally for up to30seconds awaiting a viewer; uploads require a current matching-mode lease. Binding UI and frame-queue freshness refinements are separate follow-up work; later bytes require their own verification. These APKs are not a production release and have not replaced the operator's normal application.
