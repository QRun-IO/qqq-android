# Third-party release review

Review date: 2026-10-31. This is source, cached dependency metadata and APK evidence, followed by authorized vector provenance remediation. It is not license clearance or a final release SBOM. No Gradle build was run for this review. The source was changing during inspection; final release outputs must be reviewed again after dependency and asset remediation.

The inventory below is historical checkpoint evidence. The current candidate replaces Auth0's beta with stable 3.0.0 and Coil's RC with 3.0.4; see [the dependency decision](DEPENDENCY-DECISIONS.md). Auth0's tagged stable LICENSE matches the bundled MIT text byte-for-byte, including its copyright. Post-change packaged graph/notice qualification is pending: an initial build correctly rejected stale generated Auth0 metadata, exposing a missing dependency-graph input in the pinned AboutLibraries collector. The build wiring now tracks resolved coordinates; a subsequent successful build and APK inspection must establish the new inventory rather than reusing these old counts or hashes.

## Evidence boundary

- Repository baseline: `6574ec4991dbb859b60c3a0be462af96918aee32`, plus the working changes.
- Cached debug package graph: `scanner-host/build/intermediates/lint_report_lint_model/debug/generateDebugLintReportModel/debug-artifact-dependencies.xml`, written 2026-10-31 03:44:28 workstation local time. SHA-256: `4C482AE3C0D20DF83D9780A924C58AC23B4B311A56439F6F059FED6CE532F870`.
- That graph has 123 package entries: two project modules and **121 external coordinates**. Compile-only and test graphs are different; this count comes from its `package` section.
- Inspected cached debug APK: `scanner-host/build/outputs/apk/debug/scanner-host-debug.apk`, 17,852,539 bytes, written 03:13:23. SHA-256: `36FFA7C5F405433A2EBE49BDEB3909A1D12E6998B77F1BDA0E1326118E709382`.
- The graph is newer than the APK. They are separate observations, not proof that the exact graph generated that APK. There was no cached signed release APK qualified by this review.
- All 121 coordinates had cached Maven POMs. Their declarations were inspected with XML tooling; this was an inspection, not a replacement license-report generator.

## Runtime inventory and declared licenses

The cached POMs directly declare Apache-2.0 variants for 119 coordinates and MIT for Auth0. `com.google.guava:listenablefuture:1.0` inherits its license from the cached `com.google.guava:guava-parent:26.0-android` POM, which declares Apache-2.0. Thus all 121 top-level artifact declarations were accounted for. This does **not** cover embedded data, native subdependencies, copied resources, missing upstream copyright notices or every transitive source file inside an artifact.

| Component family | Observed packaged versions | Primary local evidence / declared license |
| --- | --- | --- |
| AndroidX UI and Compose | UI/runtime/foundation/animation 1.7.2; Material3 1.3.0; ripple 1.7.0; icons/tooling preview 1.6.6 | Resolved package graph and exact cached POMs; Apache-2.0 |
| AndroidX platform libraries | Lifecycle 2.8.6, Navigation 2.8.3, Activity 1.9.2, Core 1.13.1, AppCompat 1.7.0, DataStore 1.1.1; other AndroidX support modules remain in the graph | Exact cached POMs; Apache-2.0 |
| Material Components | `com.google.android.material:material:1.12.0` | Exact cached POM; Apache-2.0 |
| Coil | Seven resolved Android/JVM artifacts at **3.0.0-rc01** | Exact cached POMs; Apache-2.0 |
| Auth0 | `com.auth0.android:auth0:3.0.0-beta.0` | Exact cached POM declares MIT and points to the upstream license; preserve actual copyright/license text, not just the label |
| Retrofit/converter | `com.squareup.retrofit2:retrofit:2.9.0`; `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0` | Exact cached POMs; Apache-2.0 |
| OkHttp/Okio | OkHttp and logging-interceptor 4.12.0; okio-jvm 3.9.1 | Exact cached POMs; Apache-2.0; embedded Public Suffix List requires separate treatment below |
| Kotlin runtime | reflect and stdlib **2.0.20**; stdlib-jdk7/jdk8 1.9.0; parcelize and android-extensions runtime 1.9.22 | Exact cached POMs; Apache-2.0 |
| kotlinx | Serialization core/json JVM **1.6.3**; coroutines core JVM/Android 1.9.0 | Exact cached POMs; Apache-2.0 |
| Other resolved artifacts | accompanist-drawablepainter 0.36.0; androidbrowserhelper 2.4.0; AndroidSVG AAR 1.4; Gson 2.8.9; annotations 23.0.0; error_prone_annotations 2.15.0; listenablefuture 1.0 | Exact cached POMs, including inherited Guava declaration; Apache-2.0 |

Do not derive the release inventory solely from `libs.versions.toml`: declared serialization 1.5.1 resolves to 1.6.3; declared Lifecycle values resolve to 2.8.6; Compose resolves a mixture of versions. The current catalog and explicit dependencies explain inputs, not the final selected graph.

### Additional content inside the APK

The inspected APK preserves `okhttp3/internal/publicsuffix/NOTICE`. It identifies `publicsuffixes.gz` as compiled from the Public Suffix List and names **MPL-2.0**, with source and license URLs. Treat that embedded data separately from OkHttp's Apache POM declaration. Keep its notice and verify the relevant source/license delivery for the exact packaged version; do not summarize the complete app as containing only Apache/MIT material.

The APK also contains `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` for four ABIs. The corresponding AAR declarations are known, but native embedded notices/subdependencies have not been independently inventoried. That is an explicit remaining review item, not a claim of an additional license or incompatibility.

## Notice delivery gap

`NOTICE` now describes QRunIO, its trademarks and the pinned Google Material resources. It is not a complete dependency inventory. `scanner-host/build.gradle.kts` excludes `/META-INF/{AL2.0,LGPL2.1}`. This exclusion is not evidence that the graph includes LGPL code, but removing generic license resources requires a reviewed replacement notice-delivery path.

The inspected APK has no named app-wide license/NOTICE/report payload or OSS-license resources; only the Public Suffix List notice was found by archive entry inspection. No AboutLibraries/OssLicenses integration or user-accessible licenses screen was found in the inspected source. Repository-root LICENSE/NOTICE files alone do not establish that the distributed APK carries the needed third-party notices.

Release work must generate a reviewable report from the **release variant**, package the reviewed notices, expose them from the app's About/licenses entry, and retain the same report alongside release assets. Verify this offline on the final signed bytes. Check exact copyright/NOTICE content as well as SPDX labels.

## Vector resource provenance

Git commit `4cb65dc1d1d1f681c268513dc1c6c8d7b2f0d89c` introduced the drawable set on 2024-10-30 with the message “Initial checkin - of all material icons ... and some default sound effects.” The sampled files already had project boilerplate at introduction; that boilerplate is not evidence of exclusive project ownership.

The review pinned Google's last repository revision before that commit's timestamp: [`google/material-design-icons` at `d7a78a2ad434d5d38e1b8708c9dd9847cc3ada9b`](https://github.com/google/material-design-icons/tree/d7a78a2ad434d5d38e1b8708c9dd9847cc3ada9b). Its complete `src` tree contains 2,170 filled 24px icon definitions. All corresponding Android XML vectors were downloaded read-only into the workstation cache from that fixed revision.

For each local drawable, the ordered `android:pathData` strings were joined with a newline and hashed with SHA-256, then compared against all pinned upstream vectors. This compares drawing instructions only: it does not claim whole-file identity, identical tint/fill, or a mathematical proof of equivalent paths for nonmatching representations.

- Before remediation, **2,094 of 2,142** local drawables exactly matched a pinned upstream path-data hash.
- This includes 31 renamed/alias resources, such as `__ft_apart` → `6_ft_apart`, `_d_rotation` → `3d_rotation`, `ic_broken_image` → `broken_image`, and `resource_public` → `public`.
- Local files commonly remove Google's theme tint and replace the fill with black. Record these as modifications while retaining the appropriate upstream provenance.
- **46 named Google icons** had nonmatching path-data representations. Samples showed absolute/relative command changes and circle/arc transformations; the review did not infer an exact match from visual similarity or a filename.
- `loading_img` and `qqq_no_network` were the other two unmatched resources. They needed provenance or replacement.

The comparison manifest is in `C:/Users/james/.cache/qqq-material-icons-pinned-20241030/all-geometry-comparison.json`, SHA-256 `2B36EBE19CABF189370A1EBF9A1D8946EF04A467AC177108D1B61CA60D150125`. The same directory contains the 2,170 pinned upstream XMLs and download URLs; the cache is review evidence, not shipped attribution. Google's repository supplies an [Apache-2.0 license](https://github.com/google/material-design-icons/blob/d7a78a2ad434d5d38e1b8708c9dd9847cc3ada9b/LICENSE). Preserve the pinned upstream license and modification/provenance record when adopting these assets.

The 46 nonmatching named resources are:

```text
account_circle assignment_add barcode_reader bed bedtime_off bedtime cached
check_circle construction conveyor_belt desktop_mac desktop_windows dew_point
directions_off directions emoji_symbols forklift format_list_bulleted_add
front_loader gavel hotel_class keyboard_hide kitchen live_help local_gas_station
local_offer movie_edit new_releases nights_stay pallet percent person_pin_circle
rebase_edit recycling school screen_search_desktop shelves shopping_bag snowing
sports_cricket star sunny_snowing trolley tungsten wb_twilight window
```

### Completed source remediation

The 46 named vectors were replaced with pinned upstream counterparts, using the application's black-fill/no-theme-tint convention. The existing `loading_img` resource now uses pinned `hourglass_empty` artwork, keeping the Coil placeholder resource ID without changing UI code. `qqq_no_network` now uses pinned `wifi_off`, keeping its resource ID and blue fill. Every resource retains its original dp width and height; a comparison against Git HEAD found zero dimension differences across all 2,142 resources. Viewports for replacement geometry come from the corresponding upstream vector.

All 2,142 resources now have Google original-artwork attribution, the exact pinned source URL and project integration/modification attribution. Their final drawing path-data hashes all match the selected pinned originals. [The checked-in manifest](material-icons-provenance.json) records each resource, upstream path, upstream file SHA-256, final local file SHA-256, ordered path-data SHA-256 and final styling. Every local file hash was verified against that manifest. [The exact upstream license](third-party/material-design-icons-LICENSE.txt) and the updated repository NOTICE are retained.

`loading_img` remains used by `ImageWidgetBlock.kt`; `qqq_no_network` remains used by `NoNetworkDialog.kt`. No UI code or caller names were changed by this resource remediation. This adopts known upstream artwork with its existing license and identifies modifications; it does not relicense Google artwork as exclusively project-owned. The authorized asset update is complete and ready for the serial Android build. Final APK packaging and visual acceptance remain separate gates.

## Recorded audio

The same introduction commit added three recordings. Their metadata has no author/source/license attribution; encoder/container metadata is not provenance.

| File | Use observed before remediation | SHA-256 |
| --- | --- | --- |
| `boop1.mp3` | Submit feedback in `QProcessHome.kt`; approximately 1.485 seconds | `990D784B32369B6A67CAC1A11AF8AEBD5483A0ADB2951D23A34FF5170FFCFED2` |
| `tick1b.mp3` | Loading-step tick in `ProcessStepLoading.kt`; approximately 0.131 seconds | `DEF0D5340E38E247699E70659BDAB66CF371B91E5C0206D10682794F2380CB79` |
| `tick1.mp3` | No source use found; approximately 1.485 seconds | `08672F3E764EC68A28E0130FDDB3E820F43F06E460D38766F3B59691258861FE` |

All three remain in the inspected **older APK**. During this review, the implementation owner removed the source files and changed the two call sites to platform-generated `ToneGenerator` feedback through the existing `ProcessSound.Playback` lifecycle: submit acknowledgment for 80ms and loading beep for 25ms. This preserves feedback intent without distributing unknown recordings. A rebuild must prove the files are absent from the final APK; hardware audibility, system volume behavior and lifecycle disposal still need acceptance. This source change is not an artifact-level clearance.

## Version stability and Kotlin alignment

Coil 3.0.0-rc01 and Auth0 3.0.0-beta.0 are actual packaged prerelease dependencies, not merely unused catalog entries. The shared library still references Auth0 even though the first scanner server uses table-based authentication. Review supported stable replacements and test authentication, SVG/image loading and scanner/native flows before adopting changes; a version label alone is not proof of a vulnerability.

The precise Kotlin mismatch is **compiler/plugin 1.9.0 versus runtime reflect/stdlib 2.0.20**, not reflect 2.0.20 against an older main stdlib. Compose compiler is 1.5.1; runtime helper artifacts also include 1.9.0 and 1.9.22. `qqq/android/core/build.gradle.kts` explicitly declares reflect 2.0.20, and `GenericSerializing.kt` imports reflection APIs. Do not blindly force every artifact down to 1.9.0 or call this a proven crash. Choose a supported compiler/Compose/runtime combination, inspect release `dependencyInsight`, then run the existing serializer and native suites. This review ran no compatibility build and makes no unsupported claim that the current mix is safe or broken.

## Standard reporting approach

The scanner now integrates **AboutLibraries 10.9.2** for standard variant-specific definitions and native Compose display. Source and configuration are in [the supplemental metadata directory](../scanner-host/third-party/README.md). The selected upstream source is pinned to `2ff232b9b40c24b8afa23494bc7077e6258b89fe`; it uses Kotlin 1.9.20/Compose 1.5.4/AGP 8.1.2 upstream. Local qualification against this project's toolchain is a separate recorded gate. This introduces no project-specific POM/license parser or HTML/JSON notice renderer. [Pinned upstream AboutLibraries documentation](https://github.com/mikepenz/AboutLibraries/tree/2ff232b9b40c24b8afa23494bc7077e6258b89fe).

The integration supplies full local Apache/MIT/MPL text and distinct Google Material icon, QQQ project and Public Suffix List notices. The exact Auth0 MIT copyright is retained. The plugin's network license/funding lookups are disabled; the runtime viewer reads its packaged resource. AboutLibraries strict mode only checks recognized license IDs, so the build also validates its generated standard output against the actual Gradle runtime coordinates/versions and rejects absent license references or empty text. Every scanner variant runs this validation before resource merging. The distribution copy is `scanner-host/build/reports/third-party/<variant>/aboutlibraries.json`, with project LICENSE/NOTICE alongside it. The new viewer's isolated instrumentation opens the Google notice from the generated APK data and checks its actual rendered text.

The implementation should:

1. Generate the scanner **release** dependency/license report with the chosen tool, excluding test-only artifacts; preserve selected coordinates and exact output with the source/APK hashes.
2. Review full license/copyright/NOTICE text and inherited POM declarations. Add pinned Material icon provenance, Public Suffix List data and any native embedded notices through the tool's supported supplemental mechanism or a simple reviewed asset notice document.
3. Package and expose the reviewed report using the tool's native UI. Include project LICENSE/NOTICE separately as appropriate; do not replace third-party copyright with project boilerplate.
4. Make missing/unresolved notice metadata a release review failure, then verify the final signed APK contains the intended reports and that the licenses screen opens offline.
5. Regenerate after stable dependency selection, Kotlin alignment and resource replacement. A cached debug inventory cannot certify a later signed release.

No legal clearance, final dependency security clearance, asset relicensing or production release approval is asserted by this document.

## Qualification update — 2026-10-31

The AboutLibraries integration passed the current scanner build, 33 scanner JVM tests, scanner lint (zero errors/warnings), debug/E2E APK assembly and shared native-test compilation. The isolated TC22 `licenses` scenario subsequently passed: it opened generated entries and rendered the full Google notice using packaged resources without a server. Release signing remains deferred by the owner.

Negative generated-report checks independently removed a real runtime entry and blanked Apache license text; each failed with its intended error. The generated report was restored byte for byte after each test, then all three variants passed positive verification. The device-tested E2E APK remained unchanged throughout those checks.

[The native binary review](third-party/NATIVE-NOTICES.md) closes the earlier `.so` inventory gap with exact published AAR/source pins, per-ABI hashes, embedded NDK identity and full startup/runtime/header notices. It adds two distinct NDK entries and merges the native copyright notices into the two existing AndroidX dependency entries. Current debug, release and E2E reports each verify **125 external runtime dependencies and 130 notice entries**. Final bounded packaging passed for debug, E2E/test and unsigned release APKs, with zero release lint errors/warnings. Every final host APK contains the exact validated 130-entry report, all nine complete license texts and the eight unchanged upstream native binaries; the unknown recordings are absent. SDK tools verified release non-debuggability, disabled cleartext/backups and the expected unsigned state. The provenance manifest records the final APK/report hashes separately from historical device-test evidence. This update does not resolve the separately documented prerelease dependency/toolchain alignment or production signing/distribution gates.
