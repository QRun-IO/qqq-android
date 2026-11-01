# Android semantic UI API contract

QQQ Mobile uses QQQ's versioned semantic UI API to discover apps, render supported native process components and execute their actions. Device enrollment, branding and connectivity use the registered `mobile-scanners` extension of that API. The app has no process-specific scan endpoint or hard-coded Scan Capture process name. Android provides the native controls and scanner hardware adapter.

This defines the client's protocol and rendering boundary. It does not mean that every feature of the QQQ web client is implemented on Android. Unsupported required screen components produce an explicit message and block process submission, rather than silently dropping required input.

## Discovery and compatibility

The host first requests `GET /qqq/v1/version` through a public client with no session cookie, authorization or device credentials. No branding, authentication or protected metadata request proceeds until the response passes these checks:

| Contract | Required advertisement |
| --- | --- |
| Core semantic API | `apiVersion >=1.8.0 <2.0.0` |
| Core features | `version`, `metaData`, `processes`, `processCancel`, `extensions`, `widgets` |
| Client channel | `clientChannels` includes `android` |
| Mobile extension | `name: mobile-scanners`, `version >=1.1.0 <2.0.0` |
| Extension prefix | `pathPrefix: /qbit/mobile-scanners`, resolved under `/qqq/v1` |

Compatibility is cached for the selected server's host lifetime and isolated between server origins. Missing features/extensions or Android channel, malformed discovery or unsupported versions stop startup with an explanation and retry option. Unknown optional properties and feature names are tolerated; supported enum models retain an unknown value so an added enum does not crash JSON decoding. Unknown authentication providers and required UI controls remain explicitly unsupported.

The app sends `X-QQQ-Client: android/qqq-mobile`. The QQQ software release and semantic API version are distinct: the local demo currently uses QQQ `4.1.0-RC.20` and advertises semantic API `1.8.0`.

## API operations

Core authentication metadata, session exchange, application/process metadata, process initialization, step submission, asynchronous status polling and cancellation all use `/qqq/v1`. A `COMPLETE` response without `nextStep` means the process has finished; a named `nextStep` selects the next native screen. The client also honors an explicit legacy `values.noMoreSteps: true`, applies frontend-step replacements and field updates, and does not infer completion from the last declared screen. A false `noMoreSteps` value alone does not terminate a process. Closing a completed process does not send a cancellation request.

The extension operations are:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/qqq/v1/qbit/mobile-scanners/branding` | Public customer appearance |
| GET | `/qqq/v1/qbit/mobile-scanners/status` | Public server reachability |
| POST | `/qqq/v1/qbit/mobile-scanners/devices/enroll` | Authenticated device enrollment |
| GET | `/qqq/v1/qbit/mobile-scanners/devices/current` | Authenticated device approval status |
| POST | `/qqq/v1/qbit/mobile-scanners/sessions/heartbeat` | Session activity, capture availability and optional foreground location |
| POST | `/qqq/v1/qbit/mobile-scanners/sessions/current/end` | End this authenticated mobile session |
| POST | `/qqq/v1/qbit/mobile-scanners/sessions/current/screen` | Upload a bounded JPEG while an administrator's viewing lease is active |

The extension publishes its operation IDs, request/response schemas, errors and scanner vocabulary in the server's merged `/qqq/v1/openapi.json`. See the [server contract](https://github.com/QRun-IO/qbit-mobile-scanners/blob/fix/authentication-metadata-device-headers/docs/UI-API.md) for the authoritative extension details. The former `/mobile/v1` routes have been removed; there is no compatibility fallback.

Core structured errors retain their code, message, field errors and trace ID. Field errors are shown against matching form inputs. Diagnostics retain bounded event codes, HTTP status and latency; they do not record raw error bodies, credentials or scan values. Metadata-provided media URLs are resolved as HTTP(S) resources. Public branding assets use a separate client without QQQ credentials.

## Native rendering boundary

| Area | Current behavior |
| --- | --- |
| App navigation | Server-defined apps and process links, including root processes and section app links |
| Process lifecycle | Init, step submission, asynchronous polling, server-directed completion and backend cancellation |
| `EDIT_FORM` | Metadata field subsets, labels, hidden/editable/required flags, text/password, Boolean, integer/long/decimal, inline possible values and validation errors; explicit null clearing is preserved |
| Date/time fields | ISO text inputs with format hints and validation; no native date/time picker |
| `VIEW_FORM` | Read-only values for the declared field subset |
| Dynamic metadata | Updated frontend steps and updated fields applied to the active process |
| `HELP_TEXT`, `HTML` | Plain semantic text, with text fallback for markup; no embedded web application |
| `PROCESS_SUMMARY_RESULTS` | Status, counts and messages; link destinations displayed as text, without assuming a web route is a native action |
| Composite/ad-hoc widgets | Existing text, button, input, image, audio, divider and nested composite controls |
| `SCANNER` extension block | Native DataWedge/manual input; server title, instructions, labels and maximum length; standard process submission and acknowledgment |
| `RECORD_FILES_CAPTURE` extension block | Server-selected parent record; optional Record Files 1.x capability discovery; native camera/photo picker; checksummed resumable online upload and exact committed receipt |
| Unsupported required controls | Explicit unsupported message; Next and process widget submission blocked |

Record/table browsing and CRUD, reports, app-home widgets, record-list process components, bulk edit/load mapping/review, downloads, remote possible-value lookup, blob input and other specialized components are not implemented by this host. Rich web layout, advanced field presentation and section collapse/role presentation also do not have full native parity. Host authentication supports TABLE_BASED and explicitly advertised native OAuth2 session login through UI API1.10 with required nonce validation; see [the contract and pending verification](AUTHENTICATION.md). Keep handheld processes within the supported component vocabulary until their native implementation and end-to-end tests are added.

The scanner event preserves the barcode string, event UUID and capture timestamp. A retry sends the same event. Successful acknowledgment requires a matching `acknowledgedEventId`, `scanSaved: true`, and a nonblank string `scanId`. Missing or contradictory receipt fields leave the same event available to retry. After the request settles, the operator may explicitly leave with a warning that the scan may already be saved. Confirmed server cancellation clears local retry state; failed cancellation preserves it and offers a separate Leave anyway action to abandon local retry without claiming cancellation or undoing saved records. In-flight requests and pending photos still block leaving. An explicitly invalid SCANNER `maxLength` disables the control; only an absent value uses the default. Scanner retry context is explicit; a generic process field named `eventId` does not activate scanner acknowledgment behavior. Device approval and user permissions remain server decisions, including scans submitted through an already-open process.

## Session activity and screen viewing

The approved, authenticated host sends foreground activity through the registered extension. Heartbeats contain app/process/step names and availability, never form values, barcodes, passwords or credentials. The server derives identity from the verified QQQ session and device. Ending a session in QQQ causes the host to clear its local login when the next protected operation or heartbeat returns an authentication failure. Signing out or changing servers stops capture immediately and attempts to end the current server session before standard logout.

An administrator viewing the ordinary QQQ session record renews a short screen-viewing lease through its standard widget. The host uses PixelCopy on its own app-content rectangle only, at most once per second, bounded to 640×1280 pixels and 256 KiB JPEG. No OS screen, other application or remote control is exposed. Capture requires the resumed, focused, approved authenticated screen, completed rendering and a live lease. Password screens, loading transitions, backgrounding and logout block capture and clear the server's displayed frame. Screen/session state is checked after capture and immediately before upload. Lease timing uses server-relative duration and Android's monotonic clock, not the handheld wall clock. Frames are not saved locally.

The operator sees a screen-viewing indicator and can stop viewing until they allow it again. Location is optional: the operator explicitly grants Android foreground location permission. Denial does not prevent scans. No background location permission is requested. Heartbeats report availability plus coordinates, accuracy and capture time only when a foreground fix exists; QQQ shows the age of the last known observation separately.

## Record photos

The optional `record-files` extension must advertise a supported 1.x version at `/qbit/record-files`. The companion `mobile-record-files` 1.x extension at `/qbit/mobile-record-files` defines the native vocabulary through its public `/contract` descriptor; this is checked without credentials before record capability requests. A `RECORD_FILES_CAPTURE` block supplies `values.parent` with opaque string `tableName` and `recordId`, plus optional title/instructions. The client checks the record's capabilities before exposing photo actions; it never infers a target from process names or form values. This block is self-contained and does not silently advance a process step.

Camera capture uses Android's external camera activity and a temporary URI grant restricted to the app's private photo cache. Successful camera staging automatically starts upload; gallery selection uses the system Photo Picker and an explicit Upload action. No broad media/storage permission or background upload queue is introduced. Original bytes are copied/hashed with fixed buffers, bounded by server limits and the 20 MiB client ceiling, and sent as checksummed binary chunks. Create/finalize retries preserve the original request and item UUIDs. A lost finalize response is recovered through status before displaying Saved; all item IDs, sizes, checksums and retained association identifiers must match the complete receipt. Saved does not imply released content or current download permission.

The process cannot advance or navigate away while a photo is pending. Backgrounding stops transport and retains the in-memory intent for explicit foreground recovery; rotation preserves its ViewModel. Discard recovers an uncertain create before requesting server staging cancellation and never deletes committed originals. Process destruction or logout cancels active I/O and cleans only that model's private temporary files after its writer closes. It does not claim remote staging was deleted when authentication has ended. Recovery across operating-system process death is not an offline queue guarantee; server staging expiry/administrative reconciliation still applies. Native device acceptance of the integrated photo flow remains pending; see [SESSION-STATE.md](SESSION-STATE.md).

## Verification and upgrades

JVM checks cover compatibility rejection before credentials, origin isolation, additive metadata, structured errors, cancellation transport, native form rules and process-state decisions. The companion server checks generated OpenAPI against its committed contract baseline and validates real HTTP payloads.

The native E2E runner in [EMULATOR.md](EMULATOR.md) forwards the real host's requests through a test-only OpenAPI audit. Scenarios cover incompatible core major, missing extension, missing Android channel, the real supported server scan flow, additive future metadata, native administrative forms, session activity/viewing/forced logout, and actual PixelCopy image contents. The audit records operation IDs and safe request metadata, never headers or bodies. Passing runs must prove that all observed requests match published operations and rejected servers receive no credentials. Precise revision boundaries and results are tracked in [SESSION-STATE.md](SESSION-STATE.md); implementation of a check is not evidence that its latest run passed.

Server changes using existing supported components can reach the shared APK through metadata. New required native controls or an incompatible major contract require a shared client update and conformance tests. Customer name, theme and logo changes remain server configuration and do not require customer-specific builds. During this initial migration, update the companion server and APK together; preserve the demo database and install with `adb install -r` to retain local device enrollment.
