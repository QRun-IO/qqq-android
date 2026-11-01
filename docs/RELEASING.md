# Scanner APK releases

Status: release automation is prepared, but the signing identity, complete native acceptance, final security review and first release are not yet qualified. Current installed demo APKs use a development certificate. Do not label them production releases.

The [November 1 independent automation review](RELEASE-AUTOMATION-REVIEW.md) found no actionable finding in the exact reviewed workflow/build files. External GitHub protections, the production identity and actual signed-device acceptance remain unverified.

Owner decision, October 31: leave signing and distribution until the final step because the distribution channel has not been chosen. Do not generate signing keys, populate production signing secrets, create release tags or treat the candidate GitHub APK workflow as a selected distribution channel. Complete implementation, audits, tests, documentation and ordinary CI first; then return to the owner for the distribution decision.

The shared library keeps its existing main-only CircleCI publication. The scanner application uses a separate `scanner-v<semantic-version>` tag and GitHub release. One APK serves customers through the selected server's runtime branding. The application ID and launcher identity remain `io.qrun.mobile.scanner` / QQQ Mobile.

## Release prerequisites

- Merge only after the exact source passes the shared library, host, real-server native and companion browser tests, plus the independent security review. Update the immutable companion-server revision in `scanner.yml` to the qualified server commit first.
- Complete the navigation and physical TC22 acceptance matrix, camera/Record Files integration qualification, dependency attribution and production HTTPS/authentication/storage checks documented in the project TODO and companion readiness report.
- Establish an organization-owned release keystore and backed-up recovery process outside Git. Keep that signing identity for upgrades. Configure the `scanner-release` GitHub environment before adding secrets: restrict deployment tags to `scanner-v*`, protect release-tag creation/update/deletion with repository rules, and limit authorized release maintainers. Store `SCANNER_KEYSTORE_BASE64`, `SCANNER_STORE_PASSWORD`, `SCANNER_KEY_ALIAS`, and `SCANNER_KEY_PASSWORD` in that environment, not repository-wide secrets. A workflow environment declaration alone does not create these protection policies; actual GitHub configuration must be reviewed before loading production keys. Never place secrets in Gradle properties committed to source or pass passwords on the command line.
- Set repository variable `SCANNER_CERTIFICATE_SHA256` to the intended certificate's SHA-256 digest, 64 hex characters without separators. The build compares the resulting APK signer to this independently configured public fingerprint.
- Set repository variable `SCANNER_VERSION_CODE` to a positive integer greater than every previously distributed scanner version code. Android's maximum is 2100000000. Release workflows are serialized across tags and compare the selected code with every published `scanner-v*` release's `BUILD.txt`, including prereleases. Missing or malformed prior build evidence fails closed. Account for any APK distributed outside GitHub manually as well; changing the display version alone is insufficient.

## Build and publication

Create the immutable `scanner-v1.0.0` tag only after the prerequisites pass. Use a prerelease suffix for a qualified release candidate, for example `scanner-v1.0.0-rc.1`. Do not move a published tag or replace released APK bytes.

Push one release tag at a time and wait for its workflow to finish. GitHub's default concurrency group serializes execution but retains only one pending run; it is not a durable multi-release queue. The workflow reuses the validated version code during signing and rechecks the remote tag's commit immediately before publication. Immutable tag protection remains necessary to close concurrent tag-change races.

The tag workflow first calls the existing scanner verification workflow, including native emulator and real-server tests. Before signing, it verifies the tag's commit is an ancestor of protected `main` and enforces the increasing version code. It then builds a non-debuggable release APK, runs release lint, verifies its signature against the expected certificate, verifies package/version metadata and writes checksums and the source commit. Only the final publication job receives repository write permission. A failed verification or absent signing configuration prevents publication. The temporary keystore is removed at the end of the signing step. Signing passwords stay in masked environment variables and never become workflow outputs or artifacts. Repository/environment protection is essential because a maintainer able to change trusted workflow code could otherwise remove its in-workflow checks.

The candidate APK release contains `qqq-mobile-<version>.apk`, `SHA256SUMS`, `signature.txt`, `BUILD.txt`, `aboutlibraries.json`, `LICENSE` and `NOTICE`. The notices are generated from the release runtime and also packaged for offline access in Settings. Download and verify these assets, then qualify the actual signed bytes on a device before customer rollout. Checksums establish artifact integrity; they do not replace signer verification or functional acceptance. Signing/distribution remains deferred by the owner until the final step.

For local release builds, supply the four `SCANNER_*` signing environment variables (use `SCANNER_KEYSTORE` for the local keystore path, not the CI-only base64 variable), then run:

```text
./gradlew :scanner-host:lintRelease :scanner-host:assembleRelease -PscannerVersionName=1.0.0-rc.1 -PscannerVersionCode=2
```

Without signing variables, local release assembly produces an unsigned APK for inspection. Partial signing configuration fails immediately. Debug/E2E builds retain their existing development signing and default version unless explicitly overridden. Unsigned and debug APKs must not be distributed as production releases.

## Upgrade and Zebra rollout

Android accepts an in-place application update only with a compatible signing identity and version code. The existing development-signed TC22 installation cannot simply be replaced by an unrelated production certificate. Preserve its test evidence and explicitly plan that migration: reinstalling removes private enrollment/session state and requires enrollment/approval again. Do not silently uninstall the operator's application to make a release install work.

For Zebra devices, qualify the device-provided EMDK scanner path described in [Zebra scanning](ZEBRA-SCANNING.md). Production must exclude the synthetic scan receiver and compile-only SDK implementation. Verify actual trigger capture, repeated and empty reads, rotation/reacquisition, background/leave/logout release, and restoration of scanner ownership to other applications. The production path requires no DataWedge profile changes; do not persistently disable DataWedge or treat its recipient signature check as sender authentication. Complete the separately signed ordinary-UID broadcast rejection test against the isolated production-path QA build, then qualify Back/Apps navigation, session termination, reconnect behavior and photo capture using the actual signed release APK. Subsequent customer updates should preserve server selection and device enrollment and keep the same signing identity. Customer branding comes from server metadata and does not require rebuilding the APK.

GitHub APK delivery supports manual or MDM distribution. Play distribution, managed enterprise rollout, signing-key rotation and a production backend deployment need their own qualified configuration; this workflow does not claim to establish them.
