# Coding standards review — November 1, 2026

The scanner client received source-wide mechanical corrections and an explicit native Kotlin formatting pass. Compiler warnings now fail Kotlin compilation. The combined strict build passed with 163 JVM tests and no Kotlin warning/error diagnostics. Android lint reported zero errors, one bitmap-density warning and two informational findings; the density correction and its verification remain pending. These checks do not establish runtime acceptance or complete standards compliance.

## Scope and ownership

The review inventoried the original 2,524 tracked paths and all maintained working-tree source. The final mechanical gate covers 234 source/build/tooling files: 216 Kotlin sources, seven Gradle Kotlin files, nine JavaScript modules, one Java security probe and one Bash script. This includes shared core/mobileapp libraries, scanner host, sample application, source-set drivers, unit/native tests and CI scripts. The new About screen is included; its literal text and the Dogfooding/Query destinations are preserved by the formatter verification.

The 2,142 Material Design drawable resources retain their upstream attribution and provenance in NOTICE and the material-icons manifest. Binary art, upstream wrapper implementation, third-party license data and generated/ignored build output were excluded from ownership rewrites. Existing Kingsrook and QRunIO copyright dates/notices were preserved. New comments do not replace third-party ownership.

Static checks cover the entire maintained executable-source inventory. Named declaration-header correction covers all 216 Kotlin files and the Java probe. The declaration scanner excludes implicit property accessors, secondary constructors and anonymous objects; its counts are not a claim that every Kotlin syntax form was parsed. Manual semantic inspection concentrated on authentication/session boundaries, scanner ownership and release, capture consent/leases, transport/resource handling, widget conversion and callback registration. This is not an exhaustive semantic audit of every unchanged statement or every test assertion.

## Implemented corrections

- Added missing copyright/SPDX headers without replacing existing notices; corrected LF/final newlines and trailing whitespace.
- Replaced all 46 original wildcard imports with explicit symbols, including Compose delegation and coroutine-test extensions.
- Added useful ownership, cancellation, transport and contract comments to missing production declarations. Descriptive tests also receive the visual headers explicitly permitted by K22-CODE-09 and the QQQ code-style guidance.
- Added the [source standards gate](../scripts/ci/check-source-standards.mjs) to the existing Scanner Android verification job. It checks copyright/SPDX, UTF-8, LF/final newlines, trailing whitespace/tabs, Kotlin/Java wildcard imports and JavaScript syntax. The inventory includes tracked and nonignored untracked source, so the gate checks its own new file locally as well as in CI.
- Enabled `allWarningsAsErrors` for every `KotlinCompile` task across modules, variants and tests. No global warning suppression was added.
- Replaced unchecked widget-map casts with validated string-key copies into owned mutable state. Added regressions for immutable/shared-input ownership and malformed keys.
- Replaced the unchecked callback-function cast with checked zero-argument function registration and a return-discarding wrapper. Added regressions for malformed registration and once-per-step execution. Removed two proven redundant non-null assertions.
- Migrated the host to the already available lifecycle-compose lifecycle owner, removed a shadowed projection-listener name and retained deprecated accessibility-node recycling only on Android 26–32 with an expression-level compatibility explanation.
- Routed previously unused modifiers to the corresponding existing UI/error nodes. Five intentionally unused published renderer/dialog parameters retain their compatible signatures with parameter-level suppressions and specific explanations. These suppressions do not disable diagnostics elsewhere.
- Implemented the sample repository's previously unimplemented URI resolver with the existing production HTTP(S)/host/no-userinfo validation, and removed obsolete commented implementations. Unfinished metadata/renderer questions remain explicitly tracked in [legacy source follow-ups](LEGACY-SOURCE-FOLLOWUPS.md) and the [task list](TODO.md); deleting inline work markers does not implement those features.

## Applicable formatter and precise limitation

The existing repository sets `kotlin.code.style=official` in [gradle.properties](../gradle.properties), while [.editorconfig](../.editorconfig) requires three spaces. The shared personal style entry point and K22 standard give repository formatter configuration precedence. Kotlin therefore uses the native official structural layout with three-space indentation, rather than imposing incompatible generic Allman/control-parenthesis rules. The shared K22 no-line-length-limit rule is retained. The equivalent choice is now recorded in the [project IDE profile](../.idea/codeStyles/Project.xml) and its per-project selector.

The installed Android Studio Rabbit 2 / 2026.2.2 formatter processed isolated copies of all 223 Kotlin/KTS files. Literal-aware comparison, including nested string templates and raw literals, found identical executable tokens and literal contents for all 223. The stable output changed 202 active files. A second native dry run reported 222 files well formed and **`scanner-host/build.gradle.kts` still needing reformatting**. Its original active content was retained instead of chasing repeated formatter churn. The tool returned exit code zero even when reporting files that needed reformatting, so exit status alone is insufficient for a formatter gate.

The current GitHub workflow installs the Android SDK but does not declare an Android Studio IDE formatter installation. The existing mechanical CI gate is active; a complete native formatter CI gate requires a runner/image supplying the same existing IDE formatter and a wrapper that treats `Needs reformatting` as failure. No optional check is silently reported as a passing full formatter gate, and no alternate formatter framework was introduced.

Relevant shared requirements: K22-CODE-01/02 (gates/warnings), 03–09 (layout and headers), 11/12 (inactive code and tracked work), 14 (imports), 20–22 (errors/private data/input validation), 24/25 (license headers/generated exclusions) and 26 (published API documentation). The remaining formatter file/CI provisioning and parser/semantic-review boundaries prevent claiming every standard is fully proven.

## Verification for the current cohort

- Source standards gate: **234 files passed**, including the new About screen and gate itself.
- JavaScript syntax: all nine maintained modules passed without executing their device/server operations.
- Native formatter comparison: **223/223 executable-token and literal checks passed**; 222/223 dry-run reports well formed, with the exact retained script exception above.
- Combined strict build: **passed** in 1 minute 11 seconds. Actual JVM reports contain **39 core + 53 mobileapp + 71 scanner-host = 163 tests**, with zero failures or skips. All four new warning-fix regressions are included. No Kotlin `w:`/`e:` diagnostics were emitted under warnings-as-errors.
- Scanner debug Android lint: **zero errors, one warning, two informational findings**. `IconLocation` identifies the new `query_mascot.png` in `res/drawable`; moving the unchanged image to `drawable-nodpi` and rerunning lint/debug assembly are pending. The informational findings recommend specialized integer state holders.
- Existing environment/toolchain advisories remain: SDK XML version mismatch, Java 21 warnings about source/target 8 configuration, and deprecated Gradle features. They are distinct from Kotlin source warnings and are not represented as a wholly warning-free build.
- QA/native-device verification is a separate coordinating run and remains pending in this report. JVM/build success must not be substituted for its result.

No provider, physical scanner, projection, GPS or release acceptance gate is closed by this standards work. Those results remain in their existing readiness/acceptance records.

Final follow-up: the unchanged Query bitmap now lives in drawable-nodpi; debug APK rebuild and lint pass with zero errors and zero warnings (two informational findings remain). All46 sample native tests pass on TC22 with no failures/errors/skips, and QA instrumentation compiles. Toolchain advisories and the formatter CI limitation above remain explicit.
