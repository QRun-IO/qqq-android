# Native binary attribution review — 2026-10-31

The scanner contains two native libraries for four ABIs each. This review follows the actual binaries beyond their Maven POM declarations. [The provenance manifest](native-binaries-provenance.json) records all eight `.so` hashes, source hashes, exact AAR URLs/hashes, and the inspected APK hash. Every inspected APK native entry matches its official Google Maven AAR byte for byte. No native library was rebuilt, replaced or relicensed.

| Published artifact | Cached and independently downloaded AAR SHA-256 | Release source revision |
| --- | --- | --- |
| `androidx.graphics:graphics-path:1.0.1` | `8CA4032B6D79B351F0B59AD4B580EDDBB9423E1652F7C958830687F1EEE2EC03` | `8a05a22af450d589ef911d772a001a49dcb05b71` |
| `androidx.datastore:datastore-core-android:1.1.1` | `883ABC911E4B14119959E44E20A520523F59C303E834D6B325007B3EE5E9CB6C` | `4e91af5f5597ac6275559f2ce4bc2f5cc852bddb` |

The source revisions come from Google's exact-version release links: [Graphics Path 1.0.1](https://developer.android.com/jetpack/androidx/releases/graphics#graphics-path-1.0.1) and [DataStore 1.1.1](https://developer.android.com/jetpack/androidx/releases/datastore#1.1.1), both released May 1, 2024. Their source JARs omit the C++ implementation, so the review also read the pinned upstream native sources and build files. This establishes a published artifact/source association, not an independently reproduced build or compiler attestation.

## Native implementation and included code

Graphics Path builds `Conic.cpp`, `PathIterator.cpp` and `pathway.cpp` with local headers. Its included `math/vec2.h`, `TVecHelpers.h` and `compiler.h` are Filament math headers under Apache-2.0 with Android Open Source Project copyright notices from 2013 and 2017. The path/conic implementation retains 2022 and 2006 AOSP notices. All examined implementation and local headers are Apache-2.0; the minimum Skia structure declarations in `Path.h` do not establish that a separate Skia binary is distributed. [Pinned native tree](https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path/src/main/cpp/).

DataStore builds `shared_counter.cc` and `jni/androidx_datastore_core_SharedCounter.cc`, with its local header and NDK `<atomic>`/JNI headers. Its implementation is Apache-2.0, copyright 2022 AOSP. The exact NDK JNI header is Apache-2.0, copyright 2006 AOSP. [Pinned native tree](https://android.googlesource.com/platform/frameworks/support/+/4e91af5f5597ac6275559f2ce4bc2f5cc852bddb/datastore/datastore-core/src/androidMain/cpp/).

AboutLibraries supplemental records merge these notices into the existing two dependency entries, placing the native copyright/full Apache text first so its stock viewer displays it. The Maven artifact license remains Apache-2.0.

## Toolchain and startup support

The standard `readelf.py` from pyelftools 0.32 inspected dynamic dependencies, symbols, `.note.android.ident` and `.comment` for every ABI. All eight binaries identify **NDK r25c, build 9519653**, with Android Clang 14.0.7 (`9352603`, based on `r450784d1`) and LLVM source revision `4c603efb0cca074e9238af8b4106c30add4418f6`. Google's [pinned NDK prebuilt](https://android.googlesource.com/toolchain/prebuilts/ndk/r25/+/1622e087e1b881a0c7c744091d314662d12f0997/source.properties) confirms version `25.2.9519653`. Its ARM64 API21 `crtbegin_so.o` has the same Android build-identification note and compiler identity as the inspected native libraries.

All eight ELF `DT_NEEDED` lists contain only `libm.so`, `libdl.so` and `libc.so`. The APK contains no separate `libc++_shared.so`. Graphics Path explicitly uses `-nostdlib++`, disables exceptions/RTTI and removes unused sections in its [pinned build configuration](https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path/build.gradle). These facts do **not** prove that no header-generated or statically linked compiler support exists; the following notices cover that support separately.

- **Bionic startup/termination/build-identification objects:** the `crtbegin_so.c`, `crtend_so.S` and `crtbrand.S` source headers retain BSD-2-Clause notices, copyright 2012, 2013 and 2015 AOSP. The included `atexit.h` and `__dso_handle_so.h` share the 2012 notice; `pthread_atfork.h` uses Apache-2.0 (2015 AOSP), and its body is conditional on API23 or a forced CRT flag. The [pinned reference sources](https://android.googlesource.com/platform/bionic/+/0b981d1ec055efaef86d8a5d402cf1b3167ba10b/libc/arch-common/bionic/) and the exact NDK's NOTICE corroborate these terms. [The retained BSD notices](native-AOSP-BSD-2-Clause.txt) appear in the separate **Android NDK Bionic startup support** viewer entry. This does not claim that the device's system libc/libm/libdl implementations are shipped by the application.
- **LLVM C++ headers and compiler runtime:** the exact NDK `<atomic>` header declares `Apache-2.0 WITH LLVM-exception`. The [matching LLVM source](https://android.googlesource.com/toolchain/llvm-project/+/4c603efb0cca074e9238af8b4106c30add4418f6/) provides full libc++ and compiler-rt licenses, including the object-code exception and retained historical terms. Both complete upstream files are retained as [libc++](native-LLVM-libcxx-LICENSE.txt) and [compiler-rt](native-LLVM-compiler-rt-LICENSE.txt), and appear together in the **Android NDK LLVM runtime and C++ headers** entry. Including them avoids relying on an inferred absence of embedded support or applying the compiler exception as a blanket waiver. It does not claim the whole LLVM toolchain is bundled.

The corresponding SPDX allowances are restricted to those exact supplemental entries. The build also pins this native review to Graphics Path 1.0.1, DataStore Core Android 1.1.1 and NDK 25.2.9519653. Changing either dependency requires renewed native/source/toolchain review; a new dependency cannot inherit the BSD or LLVM allowance silently.

## Verification and limits

Before adding the native notices, negative build checks removed one real runtime dependency and then blanked Apache license text in generated output. Each failed for the intended reason; generated JSON was restored byte for byte, all three positive variant checks passed, and the already-tested E2E APK stayed unchanged. After the native additions, debug, release and E2E metadata checks pass with **125 resolved runtime dependencies and 130 notice entries**, including five supplemental components. The two native dependency overrides merge existing entries instead of inflating the runtime count.

Final bounded packaging passed on 2026-10-31: debug APK, E2E APK/test APK, unsigned release APK and `lintRelease` (zero errors/warnings), 273 tasks in 2m32s. The manifest retains the earlier device-tested APK as historical native-byte evidence and separately records all final package hashes. Each final host APK contains **130 entries and nine complete license/notice texts**, byte-for-byte identical to its validated variant report. All eight native hashes match the reviewed AARs in every APK; no MP3 recordings remain. Release resource optimization renames the notice file to `res/M7.json`; SDK `aapt2 dump resources` resolves it from `raw/aboutlibraries`, so verification does not assume the debug ZIP path.

| Final artifact | SHA-256 |
| --- | --- |
| Debug APK | `E12CFB7F078AD0C5F28EED717880187F394379C9532EA2D5828A228D1E4A134F` |
| E2E APK | `733285E6BE30B67A46BF2810F07376379C94E4AE529746F321262AB86CCF0740` |
| E2E instrumentation APK | `E697023F2F31F98F5F4A9A82E4A91FE8A8F5B48B08687DD784FE9960947EED35` |
| Unsigned release APK | `924D7EC15F93957073F0D9B47348F0E4CF7F272FBB4EBE4100FEDCBD8DB027A2` |

SDK `apkanalyzer` confirms the actual release APK is non-debuggable; its decoded application manifest sets `usesCleartextTraffic`, `allowBackup` and `fullBackupContent` to false, with no overriding network security configuration. SDK `aapt2` decoding confirms both cloud-backup and device-transfer exclusions for root, file, database, sharedpref and external domains at path `.`. SDK `apksigner` confirms the expected absence of a signature. No signing environment, key or identity was changed. Final production signing/distribution and upgrade acceptance remain separate gates.

The prior offline viewer test passed on TC22; this final packaging pass performed no device actions. No general legal clearance or claim of a fully reproduced upstream toolchain build is made. Stripped binaries limit instruction-level source attribution; the review conservatively retains identified runtime/header/startup notices instead of treating a Maven POM or missing dynamic symbol as complete evidence.
