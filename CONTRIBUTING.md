# Contributing to QQQ Android

Follow the [QRun-IO contribution guidelines](https://github.com/QRun-IO/.github/blob/main/CONTRIBUTING.md) and this repository's existing Kotlin conventions. Use a `feature/`, `fix/`, `docs/`, or `chore/` branch from `main`, and open a pull request to `main`. Use Conventional Commits and include tests and documentation for changed behavior.

## Local verification

Install Java21 and Android SDK35, then use the included Gradle wrapper:

```text
./gradlew :qqq:android:core:testDebugUnitTest :qqq:android:mobileapp:testDebugUnitTest :scanner-host:testDebugUnitTest :scanner-host:lintDebug :scanner-host:assembleDebug
```

On Windows use `gradlew.bat`. Keep SDK locations in ignored `local.properties` or environment variables. Do not commit APKs, signing keys, passwords, session cookies, device tokens, or real barcode data.

For authentication/navigation/process UI changes, run the native emulator suite:

```text
./gradlew :qqqsampleandroidmobileapp:connectedDebugAndroidTest
bash scripts/ci/emulator-smoke.sh
```

The smoke script clears application data only after verifying the target is an emulator. See [emulator testing](docs/EMULATOR.md). DataWedge trigger behavior still needs a physical Zebra handheld.

## CI and delivery

Managed CircleCI remains the shared-library test and Maven publication lane. Its Android profile publishes libraries only from `main`, after its checks. Feature branches do not publish.

The GitHub **Scanner Android** workflow runs shared-library/scanner JVM tests, scanner lint, and debug APK assembly for pull requests and supported branches. It uploads the development APK with SHA256 and verification reports. **Run workflow** optionally adds native emulator tests and a scanner launch screenshot. This workflow never publishes Maven artifacts, signs a production APK, or deploys to a device/store.

Production signing and distribution require a separately configured release procedure. Do not treat the development APK as a production release.

## Review and security

Request review from the repository code owner. Explain compatibility changes between Android metadata/process contracts and the companion qbit. Use synthetic fixtures and preserve raw barcode values and retry identities. See [SECURITY.md](SECURITY.md) for private reporting and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for community expectations.
