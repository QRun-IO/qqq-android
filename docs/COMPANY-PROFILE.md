# Company scanner profile package

Status 2026-11-02: **prepared, not applied.** Nothing here has been installed on, pushed to or tested against a device. No device, setting, credential or server was changed. The target device's Android build, MX version and installed EHS version have **not been measured**; Zebra documentation versions below are the documentation reviewed, not device facts. Context and layering: `docs/DEVICE-PROVISIONING.md`.

Files: `deployment/company-profile/`

| File | Purpose |
| --- | --- |
| `profile.example.json` | Non-secret parameters. Unmeasured or unknown values are `null` or placeholders on purpose. |
| `generate-company-profile.mjs` | Validates the profile and renders `enterprisehomescreen.xml`, `managed-configuration.json` (once the endpoint is real) and `stagenow-mx-appmgr.xml` (only when the device is measured). No dependencies. |
| `generate-company-profile.test.mjs` | `node:test` cases for rendering, withholding and rejection. |
| `DEVICE-OWNER-ENROLLMENT.md` | The separate, destructive enrollment path. Not generated. |

```text
node deployment/company-profile/generate-company-profile.mjs my-profile.json out/ [--require-ready]
node --test deployment/company-profile/
```

Exit 1 on validation errors; 2 with `--require-ready` while any input is still missing. `out/profile-report.json` lists `missing` and `warnings`. One shared APK serves every customer: customer differences are only profile values (title, logo, wallpaper, app list), never a build.

## What the generated EHS file contains

Only tags from Zebra's EHS 8.0 Advanced Settings (`techdocs.zebra.com/ehs/latest/guide/settings/`): `kiosk`, `applications`, `tools`, `passwords`, and in `preferences` the `title` (static, or `<value>` with one documented device-ID token), `title_bar_icon` (`title_bar_icon_disabled`, `title_bar_icon_file`, `reuse_custom_icon`), icon label colours, `orientation`, `wallpaper`, `wallpaper_stretching_enabled`, the keyguard/USB/settings/recent-apps tags, `wireless_info` and `screen_blanking`. Tags not parameterised keep the values of Zebra's documented default file. Constraints enforced from that page: images only under `/enterprise/usr` (the persistent location), documented image formats, app labels of at most 18 characters, XML escaping, no wildcard packages (rejected for least privilege), no EHS self-listing.

Layout: EHS shows apps in listed order (links first when both exist). Arbitrary pixel placement is not a documented feature; folders are not in the pages reviewed and are not generated.

Explicit non-goals, enforced by the generator: `usb_debugging_disabled` is always `1`, `kiosk_mode_enabled` is always `0`, no `bypass keyguard`, `<passwords>` is always empty, `launcher.iconSize` is rejected (the Zebra snippet does not show where `user_options` sits).

## What the generated MX file contains

`AppMgr` characteristics only (`techdocs.zebra.com/mx/appmgr/`): optional `Install`/`Upgrade` of an APK already on the device, then `SetDefaultLauncher` for `com.zebra.mdna.enterprisehomescreen` (EHS setup page, `.../ehs/latest/guide/setup/`). Characteristic version `4.2` is the version used in Zebra's examples; `SetDefaultLauncher` is listed as MX 4.1+. **Not generated:** AccessMgr (allowlist and permission grants can lock out settings or break the scanner; Zebra's schema needs a device-specific review) and file delivery of the EHS XML and images to `/enterprise/usr` (Zebra recommends StageNow, DNA Cloud or an EMM for moving files; the StageNow file-delivery parameters were not in the cached pages, so they are not guessed).

## Hazards to review before any device

1. **EHS defaults disable USB debugging.** Zebra: `usb_debugging_disabled` 1 "or if left unspecified will prevent user access to the file system and all ADB functionality". Installing EHS on the current development TC22 will cut ADB. Use a spare device; Zebra advises restoring USB debugging before uninstalling EHS.
2. **Pushing the XML overwrites the existing file**, including the stored admin password hash. Set the EHS admin password in Admin Mode on a reference device; never author it. Decide whether to deploy that device's exported file.
3. **`system_settings_restricted` 1 (default) hides most Settings panels** and Zebra warns it might prevent some MX features. Confirm Bluetooth printer pairing and Wi-Fi workflows. The profile requires this value explicitly.
4. **Default launcher:** EHS must be re-designated after any reinstall. Test Home/Back/recovery before relying on it.
5. **MediaProjection consent is still per session** on Android 14; nothing here grants it.
6. **Managed bootstrap is consumed by the app, but only through an EMM.** `managed-configuration.json` is a reviewable key/value payload for the EMM's Android managed-configuration (app restrictions) field; StageNow/MX and the EHS XML do not deliver it, and no Zebra schema is claimed for it. See "Managed configuration" below.

## Inputs still required (also in `profile-report.json`)

- Measured on the target TC22: `device.androidVersion`, `device.mxVersion`, `device.osBuild`; choose and record `ehs.installedVersion` for that firmware (docs reviewed: EHS 8.0).
- Company logo and wallpaper image files, delivered to `/enterprise/usr/...`, and their paths.
- Real company `qqq.endpoint` (HTTPS origin) and optional `qqq.orientation`; the EMM to push them as app restrictions.
- Administrator list of permitted Android companion apps (camera/photo picker, printing) with exact package and activity names from the device.
- On-device EHS APK path and the artifact delivery/checksum method; the QQQ APK path if the profile should also deploy it.
- Decisions: `systemSettingsRestricted`, recent-apps values, `show IP` visibility, who sets the EHS admin password, EMM versus StageNow, and whether a device-owner fleet is wanted.

## Managed configuration (implemented in the shared APK)

The app declares `res/xml/app_restrictions.xml` (manifest `android.content.APP_RESTRICTIONS`) and reads it with `RestrictionsManager` (`ManagedConfiguration.kt`). Two keys, both optional; unmanaged installs behave exactly as before.

| Key | Values | Behaviour |
| --- | --- | --- |
| `qqq_server_url` | HTTPS origin only: no credentials, path, query or fragment; HTTP is rejected even in debug builds | No saved server: the connect form is prefilled with it and says the administrator configured it; the person still taps Connect. Different saved server: never switched silently. Settings shows a notice with "Use administrator's server", which runs the existing confirmed Change-server path (ends the session, logs out) and then shows the prefilled form. |
| `qqq_orientation` | exactly `SYSTEM`, `PORTRAIT`, `LANDSCAPE`, `REVERSE_PORTRAIT`, `REVERSE_LANDSCAPE` | A bootstrap default applied when the managed value changes (last applied value is remembered); a later local choice stands until the administrator changes it again. Unknown values are rejected, not defaulted. |

Changes are re-read when the host resumes and while it is resumed (system `ACTION_APPLICATION_RESTRICTIONS_CHANGED`, not exported). Rejected keys are tracked by name only; values are never logged. No credentials, business metadata or branding are accepted this way. In the profile: `qqq.endpoint` and optional `qqq.orientation` produce `managed-configuration.json` (withheld while the endpoint is a placeholder). The EMM must be told to push those two keys as app restrictions for `io.qrun.mobile.scanner`.

## Dynamic status wallpaper (implemented, optional)

EHS has no QQQ-user or IP field (title tokens are device IDs only; Wireless Info shows the IP when `show_ip_address` is 1, exposed as `launcher.showIpAddress`), so the shared APK carries an optional native live wallpaper: `QqqStatusWallpaperService`, declared with the system-only `BIND_WALLPAPER` guard. It is inactive until a person picks it: Settings → "Status wallpaper…" opens Android's standard live-wallpaper chooser preselected to it. Nothing is applied silently; no new permission and no second app.

- **What it shows:** company brand name always (the server's public brand name/colour already used by the host, or the default). On the unlocked home screen only, also: signed-in display name, server host and the default network's IP (address and interface, via a network callback, no polling). Preview, lock screen, a locked keyguard, a missing keyguard service and lock-only wallpaper show the brand alone. Identity is withdrawn when the screen turns off or the wallpaper stops being visible.
- **Freshness is not overstated.** The existing heartbeat runs only while the app is foreground. Shown while foreground: "Signed in: NAME". After the app leaves the screen: the name with "Last confirmed HH:mm (app in background)". After 15 minutes in the background the name is replaced by "Session unverified". States are UNKNOWN (new process, destroyed host), SIGNED_OUT and SIGNED_IN; the snapshot lives only in memory, so process death and server change return it to UNKNOWN, and logout, revocation and unauthorized responses clear the name. It holds no token, cookie, credential or session id and logs nothing.
- **Limit:** a wallpaper is visible only where the launcher or a transparent window shows it. Whether EHS reveals it is unproven; EHS draws its own `<wallpaper>` image, so leave that empty when testing.

## Status of the implementation

| Item | Status |
| --- | --- |
| Wallpaper service, manifest/resources, managed configuration, settings entry, activity wiring | Compiled into the debug and E2E APKs built after the last source edit (2026-11-02 15:34). Not installed; not run on a device. |
| JVM unit tests `WallpaperStatusTest` (13), `ManagedConfigurationTest` (8) | Pass in the host unit-test run after the last source edit. |
| Generator tests for managed keys, URL and orientation | Seven generator tests pass (root run). |
| Visibility behind EHS, lock-screen behaviour, keyguard/wake timing, Android 14 wallpaper flags on a TC22, EMM delivery of restrictions | **Untested on hardware.** |

APIs used that should be confirmed by the build and a device, because the SDK jar was not readable in this session: `WallpaperService.Engine.getWallpaperFlags()` (API 34, guarded), `KeyguardManager.isKeyguardLocked`, `ConnectivityManager.registerDefaultNetworkCallback` (API 24), `RestrictionsManager.getApplicationRestrictions`, `WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER` / `EXTRA_LIVE_WALLPAPER_COMPONENT`, `ContextCompat.registerReceiver` with `RECEIVER_NOT_EXPORTED` (core 1.13.1).

## Validation status

- Authored against the cached Zebra pages `qqq-zebra-docs-ehs-settings.md`, `-ehs-setup.md`, `-mx-appmgr.md` and `-mx-accessmgr.md` (untrusted reference content; facts above cite their official page).
- Seven generator tests pass (`node --test deployment/company-profile/`), including managed keys, URL and orientation.
- Nothing has been validated against Zebra's StageNow importer or a device.
