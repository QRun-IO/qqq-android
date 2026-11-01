# Stable authentication and image dependencies

Reviewed October 31, 2026. This decision replaces Auth0 Android `3.0.0-beta.0` with `3.0.0` and all three Coil `3.0.0-rc01` version aliases with `3.0.4`. It preserves the current Kotlin, Compose compiler, AGP, Java and Android SDK configuration. These are bounded stable replacements, not a claim that every dependency is the latest release or has ongoing maintenance guaranteed by its publisher.

## Auth0 Android

Auth0 `3.0.0` is the last stable 3.x release before passkey dependencies were introduced in `3.1.0`. An Auth0 maintainer explicitly recommends it for applications avoiding the added Play Services dependency in [upstream issue 871](https://github.com/auth0/Auth0.Android/issues/871). Its [published POM](https://repo.maven.apache.org/maven2/com/auth0/android/auth0/3.0.0/auth0-3.0.0.pom) declares the same direct dependency coordinates and versions as the current beta. It retains Kotlin stdlib 1.8.22, OkHttp 4.12.0 and Gson 2.8.9. Its source targets Java 8 and supports Android API 21; this application uses API 26 or later.

The current host uses `Auth0.getInstance`, `WebAuthProvider.login/logout`, audience/scheme configuration, and browser callbacks. Native semantic OAuth login uses AppAuth separately. The application does not use Auth0's credential managers, passkey provider, DPoP, PAR or native-to-web credentials.

The stable release adds AuthenticationActivity null handling and changes the fallback for an empty authentication error description. Later 3.x changes include browser process-death restoration in 3.4, configurable authorization/logout URLs, optional DPoP and its replay/process-restoration fixes, partial browser tabs and PAR. Later RSA-padding, biometric and session-expiry fixes concern Auth0 credential managers that this client does not use. See the [tagged changelog](https://github.com/auth0/Auth0.Android/blob/3.21.3/CHANGELOG.md) and [3.0.0 to 3.21.3 source comparison](https://github.com/auth0/Auth0.Android/compare/3.0.0...3.21.3).

The bounded comparison found no later security fix applicable to the current WebAuth usage; the public repository advisory endpoint returned no published advisories at review time. Neither observation is a complete security audit or a maintenance guarantee. Full process-death recovery through Auth0's later application callback APIs is not provided by choosing 3.0.0. Existing late-callback, rotation, logout and origin-isolation protections still require regression testing.

Auth0 3.21.3 is a stable alternative, but its [POM](https://repo.maven.apache.org/maven2/com/auth0/android/auth0/3.21.3/auth0-3.21.3.pom) adds Credential Manager and its Play Services provider. The [provider POM](https://dl.google.com/dl/android/maven2/androidx/credentials/credentials-play-services-auth/1.3.0/credentials-play-services-auth-1.3.0.pom) introduces Google authentication, FIDO and Google ID artifacts. Upstream publishes them as regular dependencies; no supported exclusion recipe was found. This change does not exclude required classes or waive notice checks. Version 3.9.0 is not a dependency-free alternative: its missing transitive declarations were a publication defect repaired in 3.9.1.

## Coil

Coil 3.0.4 is a stable patch in the existing major/minor line. Its [release notes](https://coil-kt.github.io/coil/changelog/#304---november-25-2024) include image rendering and cache corrections. The intervening RC removed the `AsyncImage` equality-delegate parameter, which this repository does not use. Both image consumers use supported `ImageLoader`, `SvgDecoder.Factory`, `ImageRequest` and `AsyncImage` APIs.

Published POM comparison predicts Kotlin runtime 2.0.20 to 2.0.21, lifecycle runtime 2.8.6 to 2.8.7 and annotations 1.8.2 to 1.9.1. Compose foundation 1.7.0, coroutines 1.9.0, OkHttp 4.12.0, Okio 3.9.1 and AndroidSVG 1.4 remain unchanged. These are requested dependency versions, not a substitute for inspecting Gradle's final selected graph. See [Compose](https://repo.maven.apache.org/maven2/io/coil-kt/coil3/coil-compose-core-android/3.0.4/coil-compose-core-android-3.0.4.pom), [core](https://repo.maven.apache.org/maven2/io/coil-kt/coil3/coil-core-android/3.0.4/coil-core-android-3.0.4.pom), and [SVG](https://repo.maven.apache.org/maven2/io/coil-kt/coil3/coil-svg-android/3.0.4/coil-svg-android-3.0.4.pom) metadata. No toolchain upgrade or forced transitive downgrade is included.

## Qualification required after these edits

- Compare debug, release, E2E and QA runtime graphs; verify the stable versions, aligned Coil modules, Kotlin/Compose selections, absence of newly introduced Google Play dependencies and continued compile-only EMDK isolation.
- Run core/mobile/scanner JVM suites, lint and scanner/sample APK and instrumentation compilation. Inspect release manifests and bundled native-library hashes again if their resolved dependencies change.
- Regenerate and validate each variant's complete AboutLibraries report and packaged texts. New graph size must be measured; preserve exact coverage and license gates.
- Repeat native authentication/navigation regressions, including legacy Auth0 callbacks and provider-failure logout. Configured-provider browser acceptance remains a separate deployment check.
- Verify raster and SVG branding/widget rendering, loading/error states, image-loader disposal, and offline license viewing. Existing upload and screen-mirror tests alone do not prove Coil image rendering.
- Requalify the TC22 scanner flow on the resulting APK before delivery. Do not treat earlier APK evidence as validation of these new pins.

The candidate now passes127 JVM tests, all34 selected TC22 shared native cases (including actual PNG/SVG/error rendering), the scanner overlay regression, package/lint gates and four-APK inspection. Actual packaged reports contain131 entries/nine complete texts and the expected stable Auth0/Coil versions with no new Google identity dependencies. Selected Kotlin stdlib is2.0.21 and reflect2.0.20; historical JDK7/8 compatibility wrappers remain1.9.0. These observed selections are not a claim of a broader toolchain upgrade. See [exact package evidence and remaining gates](TRUSTED-SCANNER-ACCEPTANCE.md). No production release has been signed or published.
