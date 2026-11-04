# Scanner APK release readiness

Status (November 4, 2026): **a QRun-owned production signing key exists with a verified backup, and a locally signed, verified 1.0.0 (versionCode 2) APK exists. It has not been installed, qualified on a device, distributed or published.** The owner selected direct signed-APK / MDM distribution. The earlier deferral no longer applies. Current installed demo APKs use a development certificate and are not production releases. Manual TC22 acceptance was reported all-pass by the operator on November 4 (operator-reported, not independently verified) for the development-signed build; it does not qualify the production signer and is not requested again for that old build.

## Decisions (made)

- **Channel:** signed APK distributed directly / through MDM. Google Play is not part of this channel, and no purchased or public-CA certificate is required.
- **Key:** a new QRun-owned Android signing key, organization identity QRunIO, application ID `io.qrun.mobile.scanner`. Alias `qqq-mobile-release`, PKCS12, RSA 4096, valid through 2056-10-27.
- **Certificate SHA-256:** `BDDB2D2F45A5925C643A83ABAF616C1EAB2387DC5CDA7B645A6F4C0D5B17A9FF`. This is the fingerprint every distributed APK must show.
- **Storage and backup:** the keystore and its credentials are stored as a keystore Document (QQQ Mobile Android release keystore) and a Password item (QQQ Mobile Android release signing credentials) in the owner's Personal 1Password account, vault `QRun`. Recovery was verified on 2026-11-04: both items were recovered from the vault, the recovered file's SHA-256 matched, and the recovered keystore opened with a certificate matching the one above. Keys, passwords, item IDs and local private paths never belong in this repository.
- **Signing path:** build an unsigned APK, then sign it in isolation with `apksigner`, so the key is not passed to the application build. The Gradle environment-variable signing below remains supported but is not the selected local path.
- **CI:** no Infisical or CI activation. Only the shared CI/CD redesign, its workflows and supporting pipeline work (owned by the coordinator) are paused; no scanner-APK Gitea release workflow exists and none is being added here. Automated scanner-APK CI publication is pending and is not a blocker to direct installation.
- **Direct install:** see the [installation guide](INSTALLATION.md). Independently authorized app documentation, local signing and manual distribution continue; already-authorized publication needs no new approval flow.

## What exists today

- **Gitea CI (actual).** `.gitea/workflows/qrun-android-release.yaml` runs on push to `main` (and manual dispatch) and calls the central `KofTwentyTwo/platform-ci` Android workflow at `qrun-release-v1`. Central main run 3634 passed and published the SDK AARs. It is **not** a scanner-APK signing or release pipeline: it uses no scanner signing secrets and produces no production-signed scanner APK.
- **Gradle signing (supported, not selected).** `scanner-host/build.gradle.kts` reads four environment variables: `SCANNER_KEYSTORE`, `SCANNER_STORE_PASSWORD`, `SCANNER_KEY_ALIAS`, `SCANNER_KEY_PASSWORD`.
  - All four set: the `release` build type is signed with them.
  - None set: `assembleRelease` produces an **unsigned** APK, which is what the isolated signing step consumes.
  - Some but not all set: the build fails immediately.
  - Debug, `e2e` and `qa` builds keep the development debug signing.
  - Version comes from `-PscannerVersionName` (semantic version, default `0.1.0`) and `-PscannerVersionCode` (1 to 2100000000, default `1`).
- **Identity.** One APK serves all customers; branding is applied at runtime from the selected server's metadata, so no per-customer key or build is needed.
- **Legacy GitHub workflows (historical only).** `.github/workflows/release-scanner.yml` and `scanner.yml` predate Gitea as primary forge. They are not a release path; do not run or follow them. The [November 1 review](RELEASE-AUTOMATION-REVIEW.md) covered those files only.

## First release: 1.0.0 (versionCode 2)

Version `1.0.0`, `versionCode` 2 was built unsigned (`lintRelease` and `assembleRelease`: 0 errors, 0 warnings, 2 information) from source matching protected `main` `0ae2101f082c241ca6a70462dc9ec3057aa6c906`; only documentation differs. It was then signed in isolation with `apksigner` on 2026-11-04 (16:22:05 UTC).

**Signed artifact (local, complete):** `QQQ-Mobile-1.0.0.apk`, application ID `io.qrun.mobile.scanner`, SHA-256 `EE5AC896B8985AF6F11B2E883955925D6571A2913D74A4AACCDB564B79394D51`. Verification passed: signature schemes v2 and v3, exactly one signer whose certificate SHA-256 is `BDDB2D2F...5B17A9FF` (the fingerprint above), 16 KiB alignment, non-debuggable. The APK is not installed, published or production-qualified. The development-signed TC22 install and its data are untouched. The checksum shows integrity only, not signer correctness.

Remaining steps:

1. Qualify the **actual production-signed APK** on a device: install, enrollment, scanner path ([Zebra scanning](ZEBRA-SCANNING.md)), Back/Apps navigation, and update over a prior production build. Use a planned device migration or a different device; do not use the operator's normal device implicitly.
2. Provide an **HTTPS** server. Source release builds disallow cleartext; HTTP is limited to debug builds.
3. Use the first direct installation to confirm the device flow, then distribute the same APK to the remaining devices. MDM can be introduced later.
4. Automated Gitea scanner-APK release workflow and shared pipeline work are paused with the shared CI/CD redesign. This does not stop application release or manual distribution that is otherwise authorized; create no tags or releases through the paused pipeline until it resumes.

## Release requirements

- Keep this signer and a strictly increasing `versionCode` for every distributed build, including builds distributed outside CI.
- Never put passwords or keystores in Git, command lines, logs or committed Gradle properties. Recover them from the vault only for the signing step and remove temporary keystore copies afterward.
- Lint and build the release variant: `./gradlew :scanner-host:lintRelease :scanner-host:assembleRelease -PscannerVersionName=<semver> -PscannerVersionCode=<increasing integer>`, with no signing variables set for the isolated path.

## Updates, compatibility and recovery

Android updates in place only when the signer matches and `versionCode` does not decrease. Losing this key, or its backup, means installed copies can no longer be updated in place; they would have to be uninstalled and reinstalled, which removes enrollment and session state. Keep the vault backup recoverable and re-verify it (hash, open, certificate comparison) after any change to it.

The existing development-signed TC22 installation cannot be upgraded by the production key. Moving it requires an explicit migration: preserve its test evidence, plan re-enrollment, and never uninstall or reset the operator's device or application implicitly.

## Not yet established

Production-signed-APK device qualification, first distribution, an HTTPS production server, a Gitea scanner release workflow and secret handling (paused with the shared CI hold), and signing-key rotation planning. SDK publication is done and its completed audits are not reopened here. Other pending items are in [TODO](TODO.md).
