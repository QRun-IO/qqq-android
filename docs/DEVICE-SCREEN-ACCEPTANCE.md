# Device-screen viewing acceptance

The optional `DEVICE_SCREEN` mode requires the additive mobile-scanners1.2 contract. `APP_CONTENT` remains the default for older servers. The device operator must explicitly enable notifications, accept the disclosure and approve Android's fresh screen-sharing dialog. No consent is persisted. The foreground notification and Android indicator allow stopping; sharing also stops on host disposal, lock, session loss or bounded lease/watchdog expiry. Remote revocation is learned through subsequent requests, rather than an instantaneous push.

The initial source checkpoint passes135 JVM tests with no failures/errors/skips, scanner debug/release lint, and debug/unsigned-release/E2E/QA plus native-test package builds. Its paired server passes39 tests and six real-HTTP demo tests using qualified core `ecf0f550`, with no artifact installation and unchanged historical1.0/1.1 API baselines. The frozen client diff received parent and fresh independent static review: all nine inventoried changed files inspected, no reportable findings. The sealed security scan is `ea8a20a8-8a16-48d8-9c58-a9376387fb60`; its tool reports4,884,517 aggregate tokens across two threads. This is a changed-file static review, not a new full-repository audit or device acceptance.

The refined candidate passes137 JVM tests, debug/release lint and all package builds. A separate independent review covered its four-file image-buffer/binding follow-up, with no concrete issue found; the original sealed scan remains unchanged and describes only its original bytes. The TC22 ordinary-UID/distinct-signer regression passes on the refined QA APK: eight forged broadcasts add zero mutations while one manual positive control saves. Physical trigger and projection consent remain unverified.

All four refined APKs pass fresh SDK DEX/manifest/resource inspection:131 notice entries/nine full license texts, unchanged reviewed native binaries, zero bundled EMDK implementation classes, test-only ingress excluded from production variants, backup exclusions intact, and unsigned release cleartext/debugging disabled. The paired published server `10416938724c9663d66f7848ff2b1a63fe635794` also passes a fresh isolated consumer qualification:195 hashed runtime JARs,2,441 unique QQQ classes, zero duplicates,39 server/six demo tests and all15 record/bulk/admin browser tests, with no shared Maven replacement. Hosted client CI is pinned to that exact source.

The refined candidate hashes are:

| APK | SHA-256 |
| --- | --- |
| Debug | `F3AF638E34AA0B6FB44D789362BED2838202EDAF681DF775F2A5FAD3AB3E9FAA` |
| Unsigned release | `69FF9A2F1D7A0A766682994E393D1DE9E3B10CA28D1ECD7239D43E7FB8C57B8C` |
| QA | `8F44EFB9A025F0E9457057E25844852BC83B0823B87B5D830AB93BD7962B80EB` |
| E2E | `CCB447F8F4121E0FBCF6B8DD5A82784DACCBAF2AF839A10127B6E4CE517CDFBF` |

Actual OS consent, notification visibility, fresh pixels on a static screen, other-app/background capture, lock, rotation/recreation, administrator disconnect and forced logout remain required device acceptance. A projection may exist locally for up to30seconds awaiting a viewer; uploads require a current matching-mode lease. Binding stays disabled through service startup; continuous draining retains only one latest image, released on replacement/resize/stop. These APKs are not a production release.

November1 operator update: the debug APK above was installed in place into the normal TC22 app after matching its signing identity and backing up private data. Login and Apps/Scan Capture navigation succeeded; EMDK reports ready. Normal server remains API1.9/mobile1.1 and does not advertise whole-device mode. This update does not establish projection acceptance or qualify historical DataWedge scans as EMDK scans.
