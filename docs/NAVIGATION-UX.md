# Mobile navigation and operator experience

Status: implementation and native acceptance in progress. This specifies the requested behavior; it does not claim the current APK already implements every row.

## Information hierarchy

The primary hierarchy is **Apps → selected app → running process**. The Apps catalog lists only server-authorized business apps. App pages list their server-defined processes. Client settings, troubleshooting and account actions are not business apps and do not belong in that catalog.

Every screen provides one **hamburger navigation drawer**, a clear current app/process title, and a separate **Account** action. The drawer's primary section contains **All apps** and the server-authorized apps; Settings and Diagnostics remain fixed at its bottom while the app list scrolls. There is no separate Apps toolbar button or Switch app overflow action. The hamburger remains accessible alongside Back inside an app or process. Before login or device approval, the drawer exposes Settings and Diagnostics but hides business destinations. Use one authoritative navigation stack; toolbar Back and Android Back must call the same navigation policy. Do not maintain a second mutable title/history stack. App switching replaces the active app branch so Back cannot unexpectedly return to an abandoned process.

| Layer | Contents and entry |
| --- | --- |
| Primary navigation | Apps catalog, chosen app, its processes. All apps and authorized app entries are reachable through the hamburger drawer from every approved authenticated screen. |
| Process actions | Server-defined submit/next/finish actions; contextual Cancel/Close. A previous business step exists only when supported by the server contract. |
| Account | Current user and Sign out. Explicit user sign-out is distinct from forced session expiry. |
| Settings | Current server/customer, device name/short name, device permissions, supported operator preferences. Changing server requires leaving work and signing out of the old server. |
| Diagnostics | Server reachability details, recent safe logs, retry/check connection and app/version information. Reachable from the connection footer and secondary menu. |
| Administration | Approval and fleet management remain in QQQ administration. A handheld pending approval shows its short name and administrator instructions. |

Optional location permission belongs under device settings, not in the primary business navigation. Camera permission is requested when the operator chooses Take photo. A live viewing indicator and Stop viewing control remain visible while an administrator is viewing the app; they are not hidden in settings.

The owner also requires a device-local Orientation preference in Settings: System default, Portrait, Landscape, Reverse portrait and Reverse landscape. Persist the selection across app restarts and apply it across login, menus and processes. Use Android's activity orientation policy without changing system-wide rotation settings. Rotation or a policy change must preserve drafts and pending upload identity. Licenses belong in the same secondary Settings layer and must remain readable offline.

## Entry and exit states

1. Initial setup accepts a server origin, validates compatibility before credentials and obtains customer branding. Errors provide Retry, server settings and diagnostics. Release builds require HTTPS.
2. Login clearly identifies the customer/server, labels username/password, supports keyboard Next/Done and a reachable Sign in action on TC22-sized screens. Submission shows progress and prevents duplicate requests. Errors preserve username, never log passwords and never expose protected screens.
3. Pending/revoked enrollment shows a friendly device name/short name, the approval state, Check approval, Sign out and secondary diagnostics/settings. It does not show the authorized app catalog until approved.
4. Successful login and approval land at Apps. Zero available apps has an explicit empty state, Retry and account/settings access. One available app still has a discoverable Apps destination.
5. Explicit Sign out asks for confirmation, with active-work consequences if applicable. Cancel leaves navigation/session unchanged. Confirm cancels or reconciles active work, ends the mobile session, clears sensitive client state and returns to login. Device enrollment and server selection survive ordinary logout.
6. Remote termination or expired authentication immediately blocks protected content/capture and returns to login with a clear reason. It must not wait for a user confirmation or leave stale screens reachable through Back.

## Back and switching behavior

| Current state | Toolbar Back / Android Back | Drawer All apps / selecting another app |
| --- | --- | --- |
| Apps catalog | No misleading disabled Home arrow; Android Back uses normal platform exit/background behavior without signing out. | Stay at Apps; repeated taps do not add duplicate routes. |
| App menu | Return to Apps. | Return to Apps or replace the app route. |
| Completed process | Close to the containing app; no cancellation request for completed work. | Close and go to requested destination. |
| Active process, including scanner | Ask to leave the process. Stay preserves work. Confirm uses native cancellation then navigates. Saved scans remain saved; never imply Cancel undoes committed records. | Same leave policy, remembering the requested destination. |
| Initializing process | Same policy; cancellation intent survives a late process UUID and cancels that process before navigation completes. | Same policy. |
| Submission/upload in flight or pending photo | Keep the operation identity and block leaving until the request settles or the pending photo is resolved. | Same policy; no hidden background second process. |
| Settled scan with missing acknowledgment | Warn that the scan may already be saved. Stay retains the original event for an explicit same-event retry; Leave process stops retrying and requests server cancellation. Retain the event if cancellation fails, and discard it only after confirmed cancellation. | Same explicit confirmation and cancellation policy. |
| Cancellation failed | Stay retains work; retry cancellation or explicitly choose Leave anyway. Warn that the server process may still run and the scan may already be saved. Leave anyway discards local retry state and applies the requested destination without another server request. In-flight and pending-photo guards still apply. | Same second-stage explicit choice; never an automatic fallback. |
| Settings/diagnostics/account surface | Dismiss the surface, preserving current app/process and draft. | Close secondary surface, then apply the normal primary navigation policy. |

The top-level Back arrow means navigation out of the current process, not undoing a scan or automatically moving to a previous business step. Label the leave dialog accordingly, for example **Leave Scan Capture?** with **Stay** and **Leave process**. Completed scans stay saved. For multi-step forms, render a server-supported previous-step action within the process when available; never guess it from an arrow or step name.

Opening the drawer preserves the current process and draft. Android Back or tapping its scrim closes the drawer without navigating or cancelling work. Selecting All apps or another app then applies the same guarded leave policy as toolbar Back. Keyboard Back first dismisses the keyboard where Android normally does so. Dialog Back dismisses the dialog as Stay. Repeated taps while cancellation/sign-out is in progress must not launch duplicate work. Failed cancellation remains visible with retry/reconciliation; it must not silently orphan a live process. Settings changes that affect identity or server use the same guard as app switching.

## Small-screen and scanner usability

Use labeled actions with accessible names, at least 48dp touch targets, readable status and visible focus. Do not disable keyboard focus on navigation controls. Keep primary submit actions reachable above the IME or after its Done action; long forms scroll without hiding the last field or action. Titles may truncate visually but retain meaningful accessibility text. Diagnostics should show the server connection, not just whether Android has internet access. The footer is persistent across login, approval, catalog and processes and opens connection details.

## Required native acceptance

- Login → approval → Apps → app → process; verify user-facing titles and selected navigation at each stage.
- Toolbar Back and Android Back both leave a process through the same confirmation; Stay preserves field values and makes no cancellation request; Leave cancels exactly once and returns to the app.
- The hamburger remains available beside Back inside processes; duplicate Apps and Switch app controls are absent. Drawer dismissal preserves drafts and makes no cancellation request. All apps and direct app switching use the same guard and land at the requested destination; Back never resurrects the old process.
- Signed-out and pending-approval drawers expose Settings and Diagnostics without business navigation. Secondary entries stay visible below the scrolling app list.
- Completed result closes without cancel; initializing process cancellation handles its late UUID; uncertain submission does not replay an ordinary mutation or discard retry identity.
- A lost scan response or malformed receipt offers an enabled Leave process action after the request settles. Stay preserves the event; confirmed leave cancels once without resubmitting. Failed cancellation retains the event and retry option. In-flight requests and pending photos still block leave.
- Failed cancellation exposes Leave anyway only after a real attempt. Stay retains the event; cancellation retry remains available; explicitly leaving anyway clears local state once without another mutation. Verify the fallback cannot bypass in-flight requests or pending photos.
- Settings, account and diagnostics open/close without navigating away from or submitting the process; server change uses the leave/sign-out guard.
- Explicit sign-out Cancel preserves work; Confirm clears protected content and returns to login; fresh login returns to Apps with enrollment retained.
- Remote session end bypasses confirmation and prevents stale Back navigation/capture.
- Empty catalog, single app, multiple apps, short viewport, large font, keyboard navigation, IME dismissal, rotation and Activity recreation retain coherent state.
- Photo capture/upload adds matching leave/cancel/reconciliation cases once its integration is available. Physical TC22 acceptance includes hardware Back and trigger behavior after app switching.

Automated tests must exercise actual navigation and request counts/state, not only button visibility. Companion real-server tests verify cancellation/session changes and persisted scans. Physical tests qualify handheld ergonomics separately.
