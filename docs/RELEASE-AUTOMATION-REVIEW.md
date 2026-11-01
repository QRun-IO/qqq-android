# Release automation review — November 1, 2026

Independent read-only review of Android revision `5ba87f49d2fc5ab7f52566727d0d2d147d9b0c9f` found no actionable correctness or security finding in these three files. The review did not execute a production release or inspect GitHub environment settings.

| File | SHA-256 |
| --- | --- |
| `.github/workflows/release-scanner.yml` | `61D581A452D00E53D83F9A61AE1702C2BA349833481B9B23CDE955B2C204426A` |
| `.github/workflows/scanner.yml` | `E6F792B4C1CDDFD3621E1CFD2009EF7F610C3C546357F83428DE5703B0C289F0` |
| `scanner-host/build.gradle.kts` | `AAF0DEAE4B653E3455B83D2CAE190F7F06D100721CBB9A471F23586F0DB5EFC6` |

The reviewer checked reusable verification, native testing on tag pushes, read-only tokens until publication, environment-scoped signing inputs, main ancestry, monotonically increasing Android version codes, temporary keystore cleanup and complete Gradle signing configuration with no debug-signing fallback. Signed APK inspection checks certificate, package, version and non-debuggable status. Publication verifies asset hashes and resolves the remote tag to the verified source commit.

The tag check is point-in-time. Protected tags, authorized maintainers, environment reviewers and immutable-release settings require separate inspection/configuration; workflow YAML does not establish them. The signed release is rebuilt separately from the tested debug/E2E variants. Its lint and signature/manifest checks do not replace testing the actual signed APK, including preserving upgrades and device behavior.

Read-only GitHub inspection on November1 confirmed strict required checks on `main`, enforced for administrators, and disabled force pushes/deletions. Required pull-request reviews were absent. The visible ruleset list contained the main code-scanning rule and no tag ruleset; the `scanner-release` environment lookup returned404 to the current connection. Release-environment/protected-tag readiness therefore remains unproven and must be resolved before loading signing secrets.

Signing and distribution remain the owner's final decision. No key, secret, tag, GitHub protection, production release or handheld state changed during this review. See [release prerequisites](RELEASING.md).
