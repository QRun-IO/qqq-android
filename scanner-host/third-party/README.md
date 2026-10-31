# Bundled open-source notices

AboutLibraries **10.9.2** generates the scanner's variant-specific dependency definitions and renders them with its native Compose UI. Settings → Open-source licenses reads the APK's `res/raw/aboutlibraries.json`; it does not contact a server. Supplemental files use AboutLibraries' supported `libraries/` and `licenses/` schema. There is no project-specific POM/license parser or notice renderer.

The implementation was selected from [upstream revision `2ff232b9b40c24b8afa23494bc7077e6258b89fe`](https://github.com/mikepenz/AboutLibraries/tree/2ff232b9b40c24b8afa23494bc7077e6258b89fe). That version uses Kotlin 1.9.20, Compose 1.5.4, AGP 8.1.2 and minSdk 21 upstream. Our integration must pass the repository's actual Kotlin 1.9/AGP 8.6/Compose builds and device tests before qualification; matching metadata families alone is not qualification.

License text is supplied locally. `offlineMode`, disabled remote-license lookup and disabled funding lookup prevent license generation from depending on GitHub/SPDX network requests. Ordinary dependency/POM resolution still uses the configured Maven repositories. The app does not need those repositories at runtime.

Sources of supplemental notices:

- `Apache-2.0.json`: the exact Apache license retained from the pinned Google icon source in `docs/third-party/material-design-icons-LICENSE.txt`.
- `MIT.json`: the complete [Auth0 Android 3.0.0-beta.0 license](https://github.com/auth0/Auth0.Android/blob/894cdf3e27faf37e382f0689a21b486dbf15a5cf/LICENSE), including its Auth0 copyright notice. Re-review this text when adding another MIT dependency.
- `Google-Material-Icons.json`: Google artwork attribution, documented integration modifications, and the full Apache license. The 2,142 resource mappings and source/file hashes are in `docs/material-icons-provenance.json`, pinned to `d7a78a2ad434d5d38e1b8708c9dd9847cc3ada9b`.
- `MPL-2.0.json`: full Mozilla Public License 2.0 from the [Public Suffix List license](https://github.com/publicsuffix/list/blob/master/LICENSE), retrieved 2026-10-31. A distinct supplemental library explains that this is the list embedded by OkHttp 4.12.0; **OkHttp itself remains Apache-2.0**. Its original embedded NOTICE is retained by APK packaging.
- `QQQ-Android.json`: the repository's project LICENSE/NOTICE. Refresh this content when those source files change.
- `AndroidX-Native-Graphics.json` and `AndroidX-Native-DataStore.json`: native implementation/math/JNI copyright notices and full Apache text, merged into the existing artifact entries. [The native review](../../docs/third-party/NATIVE-NOTICES.md) records pinned release sources, all eight binary hashes and exact AAR verification.
- `Android-NDK-Bionic-CRT.json`: complete BSD-2-Clause startup/termination/build-identification notices for the pinned NDK r25c support objects.
- `Android-NDK-LLVM-Runtime.json`: complete matching libc++ and compiler-rt licenses, including LLVM object-code exceptions and retained historical terms. This separate entry does not relicense either AndroidX dependency or claim that the complete toolchain is bundled.

## Build and review gates

Every scanner variant runs `prepareLibraryDefinitions<Variant>` followed by `verify<Variant>ThirdPartyNotices` before resource merging. AboutLibraries strict mode rejects license IDs outside the reviewed Apache-2.0, MIT, MPL-2.0, BSD-2-Clause and Apache-2.0 WITH LLVM-exception set. The additional verification reads its standard output and compares all Gradle-resolved external runtime coordinates and versions, then requires each listed library to reference nonempty bundled license text and requires the five supplemental entries. MIT is reviewed only for Auth0, MPL only for the embedded Public Suffix List, and BSD/LLVM exceptions only for their named NDK entries; other dependencies require explicit review and suitable copyright text. The embedded-list notice must match the resolved OkHttp version, native dependencies must match the reviewed versions and expose their supplemental notices first, and project notice content must match LICENSE/NOTICE. A missing POM, empty license or stale runtime version fails packaging rather than silently omitting attribution. This is report validation, not a replacement dependency/license generator.

Unmodified standard JSON plus project LICENSE/NOTICE are copied into `scanner-host/build/reports/third-party/<variant>/` for distribution and inspection. The JSON is also packaged in the APK. Existing assemble tasks therefore enforce the gate in CI and local builds. The license screen instrumentation test uses the actual bundled notices without a server.

After changing dependencies, regenerate the release variant, review new licenses and embedded/native notices, retain the report with the matching APK, and inspect that APK. Do not add broad license exemptions, placeholder metadata or a fixed old dependency count merely to pass the gate. Standard POM reporting does not discover every embedded native or data component; this inventory and review remain necessary. This configuration is independent of release signing and does not assert legal clearance.
