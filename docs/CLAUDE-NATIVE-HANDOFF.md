# Claude native handoff: Echo setup named possible values

## Source-verified contract (core `qqq-wt-native-provider-logout`)
- operationId `searchProcessPossibleValues`: `POST /qqq/v1/processes/{processName}/possibleValues/{fieldName}` (`UiApiOperationIds`, `ProcessPossibleValuesSpecV1`, openapi.yaml ~12440). Server advertises feature `possibleValues` on `GET /version`.
- Request: `application/json` `{ "values": {other form values}, "processValues": {...}, "useCase": "form", "searchTerm"?, "ids"? }` (`PossibleValuesSpecUtils`). Not multipart. Same session cookie, `X-QQQ-Client` and device headers as other native calls (shared Retrofit client).
- Response `PossibleValuesResponseV1`: `{ "options": [{ "id", "label", ... }], "hasMore"? }`; `options` is omitted when empty.
- Field metadata (FieldMetaData v1): `possibleValueSourceName`, and for contingent fields `possibleValueSourceDependsOn: [names]` and `possibleValueSourceDependencyBehavior: DISABLE_UNTIL_SET | SHOW_ALL_UNTIL_SET` (docs/CONTINGENT-POSSIBLE-VALUES.md).
- Frontend rules (same doc): re-query when a dependency changes, clear a selection that no longer fits (transitively), disable with a hint until dependencies are set (default), ignore stale responses.

A previous revision of this work invented the multipart shape from memory; it has been replaced entirely by the above. The route path happened to match; the body did not.

## Root cause (unchanged, source inference)
Echo `setup` declares `printerId`/`mediaId` with named PVS (media `dependsOn printerId`); fixture uses ENUM PVS. The client rendered only inline choices, so Printer became a free-text "Printer *" field and submission was blocked as unsupported. That init is a synchronous COMPLETE (not ASYNC) is inferred from sources and the operations log; the native E2E remains the authority. The fixture is NOT changed to inline choices.

## Implementation
- core: `QQQApiService.processPossibleValues`; `QQQRepository.processPossibleValues(process, field, values, processValues)` (default throws Unsupported so other fakes compile); `NetworkQQQRepository` builds the JSON body and decodes options (scalar ids kept); `QFieldMetaData` gains `possibleValueSourceDependsOn` / `possibleValueSourceDependencyBehavior`.
- mobileapp `ProcessViewModel`: `loadRemoteOptions`, `remoteOptionsBlockedReason` (DISABLE_UNTIL_SET -> "Choose Printer first." and no request), `fieldChanged` (dependents drop selection immediately, transitively, and re-query), per-field generation token so only the newest request publishes (a late reply for an old parent cannot restore options or a selection), post-load prune of a choice no longer offered, `fieldResets` so the control follows a VM-cleared value. Load failures are shown with "Retry choices", cancel the superseded job, never set client-work, so Cancel/Leave stay available. `reset()` clears all of it.
- `ProcessComponentEditForm`: select for named PVS (label text unchanged: "Printer", "Paper or label size", button "Choose a value"), disabled with a hint while blocked.
- `SemanticProcess.unsupported` accepts named PVS in EDIT_FORM only (widget INPUT_FIELD remains unsupported).
- E2E test: literal `waitFor("Printer")` unchanged (diagnostic text dump retained on timeout); added a readiness wait for the enabled media control before clicking it, since media is contingent. No assertion weakened.
- docs/UI-API.md updated.

## Tests added/updated
- `NetworkQQQRepositoryTest.processPossibleValuesUsesThePublishedJsonOperation`: method/path/JSON body/use case, response with options, and the omitted-`options` empty response.
- `ProcessSubmissionTest.namedPossibleValueSourcesLoadWithDependentValuesAndReportFailures`: blocked dependent not searched; parent value sent; dependent selection cleared on parent change; late old-parent reply ignored; failure reported, no client-work block, retry succeeds.
- `SemanticProcessTest`: named PVS in EDIT_FORM supported; UNKNOWN type and widget PVS still unsupported.
Note: server response bodies in tests are minimal shapes taken from `PossibleValuesResponseV1`/`ProcessPossibleValuesSpecV1Test` (`options[].id/label`), not captured from a live server.

## Commands for root (Java 21, wrapper)
```
./gradlew :qqq:android:core:testDebugUnitTest :qqq:android:mobileapp:testDebugUnitTest :scanner-host:testDebugUnitTest :scanner-host:lintDebug :scanner-host:assembleE2e :scanner-host:assembleE2eAndroidTest
node --test scripts/ci/scanner-echo-guards.test.mjs
```
Then re-approve rebuilt APK hashes and run the root native Echo helper.

## Known gaps / risks
- Nothing compiled or run (no shell). Expect possible small compile fixes (Compose state/`mutableIntStateOf` delegate, imports).
- `possibleValues` is not added to the client's REQUIRED_FEATURES (all existing fixtures advertise only six features); a server lacking it fails the search with a visible error + Retry rather than at startup.
- Pre-existing and untested: decoding inline `inlinePossibleValueSource` ids typed `java.io.Serializable` via kotlinx.
- Whether the real Echo metadata publishes `possibleValueSourceDependsOn` for media is inferred from `withPossibleValueSourceDependsOn(List.of("printerId"))` in the producer; unverified against a live `/metaData/process/echoPrint` response.
- Untracked `native.md` at the repo root is an obsolete earlier handoff (a pointer to this file); delete it.

Coordinator verification:169JVMtests pass; lint0errors/0warnings; E2EAPKs assemble; all8guards/sourcegate236pass. RealTC22 Echo native scenario completes all5stages with86/86documented semanticrequests,2accepted synthetic scans andsuccessfulcleanup. Printing remains simulated.
