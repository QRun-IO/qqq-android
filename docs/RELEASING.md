# Scanner APK release readiness

Status (November 4, 2026): **no production signing identity or distribution channel has been selected.** Per the owner's October 31 decision, signing and distribution are the final step. Do not create signing keys, populate secrets, create release tags or publish releases until the decisions below are made. Current installed demo APKs use a development certificate and are not production releases. Manual TC22 acceptance was reported all-pass by the operator on November 4 (operator-reported, not independently verified); the last recorded installed APK was development-signed, and the tested build was not independently inspected. It does not qualify a production-signed APK.

## What exists today

- **Gitea CI (actual).** `.gitea/workflows/qrun-android-release.yaml` runs on push to `main` (and manual dispatch) and calls the central `KofTwentyTwo/platform-ci` Android workflow at `qrun-release-v1`. Central main run 3634 passed and published the SDK AARs (the shared library). It is **not** a scanner-APK signing or release pipeline: it uses no scanner signing secrets and produces no production-signed scanner APK (any debug artifacts it builds can be development-signed).
- **Local Gradle signing (actual).** `scanner-host/build.gradle.kts` reads four environment variables: `SCANNER_KEYSTORE` (path to the keystore file), `SCANNER_STORE_PASSWORD`, `SCANNER_KEY_ALIAS`, `SCANNER_KEY_PASSWORD`.
  - All four set: the `release` build type is signed with them.
  - None set: `assembleRelease` produces an **unsigned** APK, for inspection only.
  - Some but not all set: the build fails immediately.
  - Debug, `e2e` and `qa` builds keep the development debug signing.
  - Version comes from `-PscannerVersionName` (semantic version, default `0.1.0`) and `-PscannerVersionCode` (1 to 2100000000, default `1`).
- **Identity.** Application ID `io.qrun.mobile.scanner`. One APK serves all customers; branding is applied at runtime from the selected server's metadata, so no per-customer key or build is needed by default.
- **Legacy GitHub workflows (historical only).** `.github/workflows/release-scanner.yml` and `scanner.yml` were a candidate design from before Gitea became the primary forge (GitHub is a shadow/archive). They are not selected, not the release path, and nothing here claims a configured `scanner-release` environment, protected secrets, tag rules or `SCANNER_CERTIFICATE_SHA256` / `SCANNER_VERSION_CODE` variables exist on any forge. Treat them as design notes; do not run or follow them. The [November 1 review](RELEASE-AUTOMATION-REVIEW.md) covered those files only, not any live configuration.

## What is needed for production signing

Android app signing uses a self-signed certificate that you own. It is not a Windows Authenticode or public-CA certificate, and nothing needs to be purchased. What matters is that the same signer is kept for every update. Reference: [Android app signing](https://developer.android.com/studio/publish/app-signing) and [Prepare for release](https://developer.android.com/studio/publish/preparing/).

Decisions the owner needs to make:

1. **Distribution channel.** Direct APK / MDM (Zebra or other managed rollout), or Google Play. This drives the key setup.
2. **Key ownership.** Use an existing organization-owned Android key or create a new one, owned by the organization rather than an individual.
3. **Secret storage and backup.** A managed secret store for the keystore and its passwords, plus a named backup/recovery owner. Losing a self-managed app signing key without a recoverable backup means existing installs cannot be updated in place. (A Google Play upload key, by contrast, can be reset.)
4. **CI placement.** Whether signed releases are built locally by the key owner or by a Gitea release workflow. The latter does not exist yet; its secret scoping and protection settings would need to be designed and reviewed on Gitea before any key is loaded.

Channel notes:

- **Direct APK / MDM:** sign with the organization key via the four `SCANNER_*` variables. That key is the app's permanent identity.
- **Google Play:** use Play App Signing with a separate upload key. If the same signature is required across Play and direct/MDM installs, choose an organization-owned app signing key that is compatible with that Play setup **before the first release**, since it cannot be changed casually afterward.

## Release requirements

- Preserve a compatible signer and a strictly increasing `versionCode` for every distributed build, including any distributed outside CI.
- Build a non-debuggable release APK (`lintRelease` then `assembleRelease`) and verify the signer's certificate fingerprint against the intended key's published SHA-256 before distribution.
- Test the **actual signed APK** on a device (install, enrollment, scanner path in [Zebra scanning](ZEBRA-SCANNING.md), Back/Apps navigation, update over a prior build) before any customer rollout. Checksums show integrity, not signer correctness.
- Never put passwords or keystores in Git, command lines, logs or committed Gradle properties. Supply them as environment variables from the secret store, and remove temporary keystore files afterward.
- Production builds must exclude the synthetic scan receiver and compile-only SDK implementation, as described in [Zebra scanning](ZEBRA-SCANNING.md).

Local release build, once a key is approved (keystore path and passwords provided via environment, never inline):

```text
./gradlew :scanner-host:lintRelease :scanner-host:assembleRelease -PscannerVersionName=<semver> -PscannerVersionCode=<increasing integer>
```

## Upgrading the existing TC22

Android updates in place only when the signer is compatible and `versionCode` does not decrease. The existing development-signed TC22 installation cannot be upgraded by an unrelated production key. Reinstalling would remove enrollment and session state. Preserve its test evidence, plan the migration explicitly, and never uninstall or reset the operator's device or application implicitly.

## Not yet established

Production key and its backup, channel choice, Gitea release workflow and secrets, a signed-APK device acceptance, signing-key rotation and Play or managed-enterprise configuration. SDK development publication is done and the completed SDK publication and security audits are not reopened here. Still pending: the companion standalone owner dependencies, production signing and channel selection, qualification of an actual new production APK, and the first release (see [TODO](TODO.md)).
