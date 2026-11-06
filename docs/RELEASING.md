# Scanner APK release readiness

## Current status (November 5, 2026)

- **Published:** the only published release is still [scanner-v1.0.0](https://git.kof22.com/QRun-IO/qqq-android/releases/tag/scanner-v1.0.0), a **signed preview** (Gitea prerelease, not a stable release). It is unchanged.
- **Candidate, not published:** QQQ Mobile **1.0.1 (versionCode 3)** is a signed candidate. Its release qualification is incomplete, and no tag or download link is available yet.
- **Signer:** the candidate is signed with the existing permanent QRun key (certificate SHA-256 `BDDB2D2F45A5925C643A83ABAF616C1EAB2387DC5CDA7B645A6F4C0D5B17A9FF`). No new or replacement key was created; the existing recovery backup was verified earlier.
- **Candidate APK:** SHA-256 `5124716B59CAD303717E7834FE64DDC1537E33A25C9AB75A6EA1ACF03AC2717F`, 15015419 bytes.
- **Source:** Android PR 15 `ab6e44c`, merged as `c782aaaaba3f62422232436a9000af9d4b38fc82` with the same app-source tree. Installed APK bytes, signature, non-debuggable flag and 16 KiB alignment were verified. `lintRelease` reported 0 errors, 0 warnings and 5 informational findings; the release build passed.
- **Distribution:** maintainers use the existing manual signing path for direct APK/MDM distribution. Automated signing CI is optional.
- **Next candidate:** **1.0.2 (versionCode 4)** is prepared but not built; see [Next candidate](#next-candidate-102-versioncode-4).
- **Open:** see [Qualification status](#qualification-status) and [TODO](TODO.md).

## Next candidate: 1.0.2 (versionCode 4)

Prepared on 2026-11-06 on branch `release/scanner-1.0.2` (Android PRs 17 and 18 merged locally plus these notes). **Nothing is built, signed, tagged, installed or published for 1.0.2.** The signed 1.0.1 candidate stays unpublished and is superseded; 1.0.2 updates it in place with the same signer and a higher `versionCode`.

Changes since 1.0.1 (details in [CHANGELOG](../CHANGELOG.md#102-versioncode-4-candidate)): company sign-out now completes (the device-session end ran before the provider logout, so no `nativeLogoutData` came back and the browser never opened on the MC9400); the user name is shown after browser sign-in; app/OS version are reported on every heartbeat (effective with mobile-scanners server 1.6.0).

Build only after PRs 17 and 18 are merged to `main` and this branch has been rebased onto that `main`, from the exact merged commit, with no `SCANNER_*` signing variables set:

```
./gradlew --no-daemon --console=plain :scanner-host:lintRelease :scanner-host:assembleRelease -PscannerVersionName=1.0.2 -PscannerVersionCode=4
```

Then sign `scanner-host/build/outputs/apk/release/scanner-host-release-unsigned.apk` in isolation with `apksigner` using the existing permanent key (certificate SHA-256 `BDDB2D2F...5B17A9FF`), verify v2+v3 with exactly one signer, 16 KiB alignment, `versionCode='4' versionName='1.0.2'` and non-debuggable, and write `QQQ-Mobile-1.0.2.apk`, `SHA256SUMS`, `signature-verification.txt`, `apk-metadata.txt` and `release-receipt.json` next to each other as for 1.0.1. Record the resulting hashes here, in [INSTALLATION](INSTALLATION.md) and in [SESSION-STATE](SESSION-STATE.md) before any tag or release.

## Historical status (November 4, 2026, superseded where noted)

Dated evidence below is preserved. As of November 4: a QRun-owned production signing key existed with a verified backup, and the signed 1.0.0 (versionCode 2) APK was published as a signed preview: [scanner-v1.0.0](https://git.kof22.com/QRun-IO/qqq-android/releases/tag/scanner-v1.0.0), release ID 1361, tag on protected `main` `3eef6431ca39b6450cf764424d921a02b2afb9ab`. It had been installed and launched once on a fresh MC9400; at that time enrollment, scanning and update were unqualified. *(Superseded November 5: the MC9400 was migrated to the signed build; see below. The statement that the MC9400 ran the development-signed debug APK is no longer current. The TC22 still runs its original development-signed installation.)* Manual TC22 acceptance was reported all-pass by the operator on November 4 (operator report, no per-case artifacts) for the development-signed build; it does not qualify the production signer and is not rerun.

## Decisions (made)

- **Channel:** signed APK distributed directly / through MDM. Google Play is not part of this channel, and no purchased or public-CA certificate is required.
- **Key:** a new QRun-owned Android signing key, organization identity QRunIO, application ID `io.qrun.mobile.scanner`. Alias `qqq-mobile-release`, PKCS12, RSA 4096, valid through 2056-10-27.
- **Certificate SHA-256:** `BDDB2D2F45A5925C643A83ABAF616C1EAB2387DC5CDA7B645A6F4C0D5B17A9FF`. This is the fingerprint every distributed APK must show.
- **Storage and backup:** the keystore and its credentials are stored as a keystore Document (QQQ Mobile Android release keystore) and a Password item (QQQ Mobile Android release signing credentials) in the owner's Personal 1Password account, vault `QRun`. Recovery was verified on 2026-11-04: both items were recovered from the vault, the recovered file's SHA-256 matched, and the recovered keystore opened with a certificate matching the one above. Keys, passwords, item IDs and local private paths never belong in this repository.
- **Signing path:** build an unsigned APK, then sign it in isolation with `apksigner`, so the key is not passed to the application build. The Gradle environment-variable signing below remains supported but is not the selected local path.
- **CI:** no signing secrets or CI signing activation exist. Shared CI/CD redesign is owned elsewhere; no scanner-APK Gitea release workflow exists and none is being added here. Automated scanner-APK signing/publication is not configured and is not needed for direct installation or the local demo.
- **Direct install:** see the [installation guide](INSTALLATION.md). Local signing and manual distribution do not depend on the shared CI work.

## What exists today

- **Gitea CI (actual).** `.gitea/workflows/qrun-android-release.yaml` is in the repository, runs on push to `main` (and manual dispatch) and calls the central `KofTwentyTwo/platform-ci` Android workflow at `qrun-release-v1`. A central main run (3634) passed and published the SDK AARs. It is **not** a scanner-APK signing or release pipeline: it uses no scanner signing secrets and produces no production-signed scanner APK.
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

**Signed artifact (local, complete):** `QQQ-Mobile-1.0.0.apk`, application ID `io.qrun.mobile.scanner`, SHA-256 `EE5AC896B8985AF6F11B2E883955925D6571A2913D74A4AACCDB564B79394D51`. Verification passed: signature schemes v2 and v3, exactly one signer whose certificate SHA-256 is `BDDB2D2F...5B17A9FF` (the fingerprint above), 16 KiB alignment, non-debuggable. The checksum shows integrity only, not signer correctness.

**Published preview (2026-11-04):** release [scanner-v1.0.0](https://git.kof22.com/QRun-IO/qqq-android/releases/tag/scanner-v1.0.0) (Gitea ID 1361, prerelease, signed Git tag at `3eef6431`; the tag adds documentation only over app source `0ae2101f`) hosts exactly two assets: `QQQ-Mobile-1.0.0.apk` (14933499 bytes) and `SHA256SUMS` (88 bytes). Both were downloaded back and matched the same SHA-256. The forge requires sign-in to download. The local installation kit ZIP was **not** published and no bundle is hosted. Link the release page, not an individual asset URL. The APK is kept outside this repository.

**Device evidence (MC9400, November 4):** the signed APK was installed on a fresh MC9400 (Android 13, EMDK present), launched without a startup crash, showed the Connect screen, and its installed bytes read back with the exact hash above. It was not enrolled: release builds require HTTPS and the only server available was the local HTTP demo. The same key and signer are unchanged. The user then chose to run the MC9400 in demo mode like the TC22, so that fresh, unenrolled production install was replaced with the development debug APK and port forwarding to the local server. No signed-build enrollment, scan or navigation was tested. The TC22 and its data were untouched throughout.

*(The November 4 MC9400 paragraph above is historical: the fresh install was later replaced by the migration recorded below.)*

## Qualification status

Done (not to be repeated as unchecked work):

- **MC9400 migration:** the user explicitly authorized it. Release 1.0.0 enrollment over trusted HTTPS passed, then an in-place 1.0.1 update preserved login and enrollment. Apps/ScanCapture navigation and EMDK Scanner-ready state were observed.
- **Physical scan:** the operator confirmed a physical Saved scan. The backend verified CODE128, AIM identifier, raw bytes, user and device. The actual barcode differed from the requested literal.
- **TC22:** the original development-signed installation and its data are retained. The accepted November 4 manual all-pass is not rerun.
- **Server discovery:** against the real shared Authentik-backed server, UI API 1.10 discovery with strict nonce passed for both scanner and printing channels, extensions and callbacks.
- **Photo policy (live):** a signed MC9400 gallery upload returned Saved photo with `AVAILABLE` status and exact parent, type, size and hash; the previous three photos were released normally. Updated native acceptance on the TC22 passed synthetic camera/gallery uploads, the captured photo's thumbnail and full preview, parent scoping, rotation and forced-logout cleanup. All requests passed the semantic API checks. The disposable app was removed and the normal app reopened. This does not establish camera-hardware or native-Authentik acceptance.
- **Local network:** the local QQQ server remains on a PC and was reached over trusted HTTPS temporarily through a user-approved private Tailscale network. This is local qualification, not a customer production deployment. Customer instructions still require a customer HTTPS URL with a trusted certificate. TLS must not be weakened, and workstation hostnames, IPs, ports, device IDs, vault IDs, private file paths and passwords are not published.

Open:

1. **Native Authentik** login, logout and account switching with the actual production APK remain open, awaiting the MC wireless-debug endpoint. No native-Authentik PASS is claimed.
2. **Printing:** the print nonce fix (PR 11, merged `abc3d0d`, 532 hosted tests passed) postdates the published print 1.1.1; the next installer remains with the active printing owner.
3. Public compatible Core/Collab/document/files/scripting/bridges, the final public cohort gate, GS1 physical print/rescan, and full release-asset publication and distribution.
4. Company profiles and wallpaper are tracked separately.
5. Build, sign and publish 1.0.2 (versionCode 4) in place of the unpublished 1.0.1 candidate after PRs 17 and 18 merge (see [Next candidate](#next-candidate-102-versioncode-4)), with its checksum and installation instructions after remaining qualification. The published download remains the 1.0.0 signed preview; automated signing CI is optional.

## Release requirements

- Keep this signer and a strictly increasing `versionCode` for every distributed build, including builds distributed outside CI.
- Never put passwords or keystores in Git, command lines, logs or committed Gradle properties. Recover them from the vault only for the signing step and remove temporary keystore copies afterward.
- Lint and build the release variant: `./gradlew :scanner-host:lintRelease :scanner-host:assembleRelease -PscannerVersionName=<semver> -PscannerVersionCode=<increasing integer>`, with no signing variables set for the isolated path.

## Updates, compatibility and recovery

Android updates in place only when the signer matches and `versionCode` does not decrease. Losing this key, or its backup, means installed copies can no longer be updated in place; they would have to be uninstalled and reinstalled, which removes enrollment and session state. Keep the vault backup recoverable and re-verify it (hash, open, certificate comparison) after any change to it.

Development-signed demo installations (the TC22) cannot be upgraded by the production key. Moving one requires an explicit migration: preserve its test evidence, plan re-enrollment, and never uninstall or reset a device or application implicitly. The MC9400 was migrated under explicit user authorization (see above). No update or key-rotation policy beyond keeping this one permanent signer is defined here.

## Not yet established

Publication of the 1.0.1 candidate, native Authentik qualification with the production APK, a customer production HTTPS deployment, stable-release promotion, an automated scanner release workflow (optional) and its secret handling, and key-rotation planning. SDK publication by the central workflow is done and its completed audits are not reopened here. Other pending items are in [TODO](TODO.md).
