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
| Mobile extension | `name: mobile-scanners`, `version >=1.0.0 <2.0.0` |
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
| Unsupported required controls | Explicit unsupported message; Next and process widget submission blocked |

Record/table browsing and CRUD, reports, app-home widgets, record-list process components, bulk edit/load mapping/review, downloads, remote possible-value lookup, blob input and other specialized components are not implemented by this host. Rich web layout, advanced field presentation and section collapse/role presentation also do not have full native parity. Host authentication currently supports TABLE_BASED only. Keep handheld processes within the supported component vocabulary until their native implementation and end-to-end tests are added.

The scanner event preserves the barcode string, event UUID and capture timestamp. A retry sends the same event. A matching server `acknowledgedEventId` clears the pending retry; the scanner displays the saved message when the response also supplies `scanSaved: true`, and includes the returned scan ID when present. Scanner retry context is explicit; a generic process field named `eventId` does not activate scanner acknowledgment behavior. Device approval and user permissions remain server decisions, including scans submitted through an already-open process.

## Verification and upgrades

JVM checks cover compatibility rejection before credentials, origin isolation, additive metadata, structured errors, cancellation transport, native form rules and process-state decisions. The companion server checks generated OpenAPI against its committed contract baseline and validates real HTTP payloads.

The native E2E runner in [EMULATOR.md](EMULATOR.md) forwards the real host's requests through a test-only OpenAPI audit. Its six scenarios cover incompatible core major, missing extension, missing Android channel, the real supported server scan flow, additive future metadata, and native administrative forms for approve/revoke/cancel. The audit records operation IDs and safe request metadata, never headers or bodies. Passing runs must prove that all observed requests match published operations and rejected servers receive no credentials. The six scenarios passed across focused local runs; precise revision boundaries and hosted results are tracked in [SESSION-STATE.md](SESSION-STATE.md); implementation of a check is not evidence that its latest run passed.

Server changes using existing supported components can reach the shared APK through metadata. New required native controls or an incompatible major contract require a shared client update and conformance tests. Customer name, theme and logo changes remain server configuration and do not require customer-specific builds. During this initial migration, update the companion server and APK together; preserve the demo database and install with `adb install -r` to retain local device enrollment.
