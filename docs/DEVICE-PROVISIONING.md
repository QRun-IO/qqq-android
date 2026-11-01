# Company provisioning for Zebra handhelds

Research checked 2026-10-31. Target: Zebra TC22, Android 14, DataWedge 13.0.325, QQQ Mobile package `io.qrun.mobile.scanner`. This is a design recommendation, not an installed profile or a completed hardware qualification. No device configuration, enrollment, reset or runtime changes were made for this research. Production signing and distribution remain deferred.

## Recommended approach

Use Zebra StageNow/MX for repeatable initial setup, Enterprise Home Screen (EHS) for the company launcher, and the customer's existing Android Enterprise EMM/MDM with Zebra OEMConfig for ongoing fleet management. Keep QQQ responsible for its own device approval, users, sessions, business apps and semantic process UI. A QQQ-approved device and an Android-managed device are separate states.

Start with a static company-branded EHS home screen and QQQ's own live status view. Showing the QQQ user and IP continuously on the Android background is feasible as additional development, but is not a documented EHS configuration-only feature. Validate a small proof of concept before making dynamic wallpaper part of the release requirement.

| Layer | Handles | Important boundary |
| --- | --- | --- |
| StageNow and MX | Initial network/certificate setup, app/file deployment, supported OS settings and permission controls | A staging profile is not a persistent fleet-management service. Features depend on installed MX, Android and device support. |
| EMM plus current Zebra OEMConfig | Ongoing policy, application distribution and Zebra-specific managed configuration | Requires a management system and appropriate enrollment. Its licensing is separate from QQQ. |
| Enterprise Home Screen | Allowed Android apps, labels, icons, ordering/folders, launcher branding, launcher restrictions and optional kiosk mode | Does not alone provide complete device lockdown. Its orientation setting controls EHS screens only. |
| QQQ Mobile | Customer branding inside the shared APK, sign-in, device approval, sessions, mobile apps and semantic actions | An ordinary app cannot silently administer every Android setting or permission. |

These are supported product roles, not a proposal to build an MDM into QQQ. [StageNow overview](https://techdocs.zebra.com/stagenow/latest/about/), [OEMConfig overview](https://techdocs.zebra.com/oemconfig/latest/about/), [EHS overview](https://techdocs.zebra.com/ehs/latest/guide/about/).

## Versions, provisioning and privileges

The documentation reviewed identifies StageNow 5.22, OEMConfig 15.3 and EHS 8.0. EHS added explicit TC22/TC27 Android 14 support in 7.0. Select a supported EHS build for the actual TC22 firmware; a current documentation version does not establish the installed MX version. Check the device's Android build, MX version and relevant feature matrix before generating executable profiles. [EHS release history](https://techdocs.zebra.com/ehs/latest/guide/about/), [StageNow release history](https://techdocs.zebra.com/stagenow/latest/about/), [MX documentation](https://techdocs.zebra.com/mx/).

StageNow creates XML-based profiles on Windows and packages staging instructions as barcodes or binary files for its device client. Configuration can be staged offline, but referenced APKs, certificates and images must already be available or downloadable. Do not describe a barcode that points to a network-hosted APK as a completely offline installation. StageNow's workstation tool includes an FTP server; use an appropriate protected artifact-delivery method in production rather than assuming the workstation server is a production distribution service. StageNow Dynamic Staging substitutes deployment values when generating staging artifacts; it does not create a live wallpaper connected to QQQ sessions. [Getting started](https://techdocs.zebra.com/stagenow/latest/gettingstarted/), [Staging profiles](https://techdocs.zebra.com/stagenow/latest/stagingprofiles/), [Dynamic Staging](https://techdocs.zebra.com/stagenow/latest/dynamicstaging/).

For Android 14 use **Zebra OEMConfig Powered by MX**, package `com.zebra.oemconfig.release`. The legacy `com.zebra.oemconfig.common` package is not compatible with Android 13 and later. OEMConfig exposes Zebra's managed-configuration schema to an EMM; it does not replace the EMM. Verify support for each selected MX setting on the installed firmware. [OEMConfig requirements and compatibility](https://techdocs.zebra.com/oemconfig/latest/about/).

Applying supported Zebra configuration or installing EHS is distinct from Android Enterprise enrollment. Fully managed device-owner provisioning is intended for initial setup or a factory-reset device; a work profile controls a different scope and is not equivalent to owning the entire handheld. Decide the management approach before a production enrollment exercise. Do not reset the current development TC22 merely to try EHS. [Android device management](https://source.android.com/docs/devices/admin).

MX AppMgr can install/manage apps and select the default launcher. MX AccessMgr can control supported permissions, settings access and application allowlists. Permission Access Action requires MX 10.0.5.1 or later, and individual capabilities have their own prerequisites. App removal or reset can invalidate grants. Grant only capabilities the installed app declares and needs; do not interpret these controls as a blanket bypass of Android security. In particular, Android MediaProjection screen capture has a per-session user-consent requirement for apps targeting Android 14+, separate from ordinary runtime-permission grants. [AppMgr](https://techdocs.zebra.com/mx/appmgr/), [AccessMgr](https://techdocs.zebra.com/mx/accessmgr/), [Android 14 capture consent](https://developer.android.com/about/versions/14/behavior-changes-14#media-projection-consent).

EHS is described by Zebra as free. The TC22 is a Professional-series device; some additional DNA capabilities require separate licensing. Zebra specifically identifies advanced DataWedge multibarcode and image/document-capture capabilities as requiring a Mobility DNA Enterprise license on Professional devices. That does not establish a license requirement for ordinary Android camera capture used by QQQ. Confirm the selected feature entitlement and EMM costs with the supplier; no license pricing or blanket entitlement is assumed here. [EHS overview](https://techdocs.zebra.com/ehs/latest/guide/about/), [Zebra licensing](https://techdocs.zebra.com/licensing/about/).

## Company home screen and navigation

EHS configuration can define permitted apps/shortcuts and their labels/icons, title/logo, wallpaper and orientation. Apps follow configured ordering, with a documented exception that links appear before apps when both are present. This provides an ordered managed launcher, not a promise of arbitrary pixel-positioned icons. Current EHS also documents folder support. [EHS settings](https://techdocs.zebra.com/ehs/latest/guide/settings/), [EHS release history](https://techdocs.zebra.com/ehs/latest/guide/about/).

Use multi-app EHS initially: Android Home returns to the company launcher; QQQ's own home/menu navigates among QQQ business apps. Allow only the required Android companion apps, including any camera/photo-picker activities needed by the chosen capture flow. A single-app kiosk deliberately changes Home/Back escape behavior and needs a separate recovery and navigation test. EHS's portrait/landscape setting affects its own screens; the scanner's orientation preference remains an app setting. [EHS kiosk and orientation settings](https://techdocs.zebra.com/ehs/latest/guide/settings/).

EHS supports static wallpaper files in common image formats, preferably under `/enterprise/usr`; Zebra says `/enterprise` content survives an Enterprise Reset. This is not a guarantee of factory-reset persistence. Use supported StageNow/EMM deployment and appropriate access controls. Do not introduce world-writable configuration files as a convenience. Protect EHS administrator access and test the recovery procedure before enabling launcher restrictions. [EHS configuration and storage](https://techdocs.zebra.com/ehs/latest/guide/settings/), [EHS setup](https://techdocs.zebra.com/ehs/latest/guide/setup/).

## Live user and IP information

EHS provides two relevant built-in facilities:

- Its title can contain a device identifier such as Android device name, serial number, Wi-Fi host name or Bluetooth name. The documented substitutions do **not** include a QQQ user or IP address.
- Its Wireless Information screen can expose the currently assigned IP to users using `show_ip_address`. That is a diagnostic screen, not a documented live wallpaper field. Other network details can remain hidden.

Both capabilities are documented in [EHS settings](https://techdocs.zebra.com/ehs/latest/guide/settings/). StageNow generation-time substitutions do not fill this runtime gap.

| Option | Assessment |
| --- | --- |
| Static company wallpaper plus QQQ live status | Recommended first step. Reliable branding; current user, connection and IP can be presented by the app that owns the session. |
| QQQ status/dashboard activity reachable from EHS | Small additional surface if status must be easy to inspect outside a business process. It can be added to the EHS app list after implementation. |
| Event-updated wallpaper bitmap | Technically possible using Android WallpaperManager when policy permits. Must verify visibility under EHS and update/clearing behavior; periodically rewriting the EHS XML is not a recommended session transport. |
| Android live wallpaper | Requires a WallpaperService/Engine component and activation. EHS compatibility, policy, background lifecycle and resource use require a TC22 proof of concept. Not a documented EHS native feature. |
| Home-screen widget/custom launcher | A widget needs a launcher that hosts it. EHS third-party widget hosting was not established by the documentation reviewed; do not promise it. A custom launcher is a substantially larger maintenance commitment. |

Android exposes wallpaper support/permission checks and bitmap setters; live wallpaper is a separate service contract. These APIs establish feasibility, not proof that EHS renders a chosen live wallpaper or that management policy allows it. [WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager), [WallpaperService](https://developer.android.com/reference/android/service/wallpaper/WallpaperService).

The authoritative identity must come from QQQ's current session. EHS administrator mode, Android device owner and QQQ sign-in are different identities. Publish only an approved display name and state; never publish session tokens or credentials. Clear the name on logout, expiry, remote termination and server/customer change. Show an explicit stale/offline state if session validity cannot be confirmed. Avoid personal identity on the lock screen by default; any generated session image should be excluded from backup and replaced when its owner changes.

For IP updates, use Android network callbacks and LinkProperties instead of polling or a single cached Wi-Fi address. Handle network loss, DHCP changes, IPv4/IPv6, VPNs and multiple transports; label the displayed interface/address. A local IP alone does not prove that the QQQ server is reachable. [Android network state and callbacks](https://developer.android.com/develop/connectivity/network-ops/reading-network-state).

## Proposed profile parameter inventory

These are design inputs, not a finalized XML schema or implemented managed-configuration contract.

| Group | Non-secret parameters |
| --- | --- |
| Profile identity | Customer/profile ID, revision, target model/Android/MX range, rollout ring, rollback revision |
| Device identity | Assigned short name, description, asset ID; explicitly map Android device name and QQQ device record |
| Network/trust | Network-policy reference, certificate/credential delivery reference, proxy/DNS/time policy; no embedded passwords in repository fixtures |
| Apps | Package IDs, approved version/channel, artifact reference/checksum, signing identity once decided, required companion activities |
| Launcher | Title, logo/wallpaper asset references, ordered app list/folders, multi-app or kiosk policy, admin recovery reference |
| Scanner | DataWedge profile association, expected intent/output contract, required decoders; preserve the verified QQQ scan transport |
| App bootstrap | HTTPS QQQ server URL, tenant/branding discovery hints, supported configuration revision, optional orientation policy |
| Capabilities | Camera/location policy, diagnostics visibility, screen-sharing policy, required permission decisions; no blanket grants |
| Operations | Update window, reporting/retention policy, support contact, rollback and recovery procedure references |

QQQ can support Android managed app configurations without customer-specific builds, but the app must declare and consume the configuration schema. Source inspection found no RestrictionsManager/app-restrictions declaration or wallpaper component in the current scanner host. This is proposed work. Managed settings should supply bootstrap/policy only; business screens and processes remain on the versioned QQQ semantic API. Reconcile admin-managed values with user preferences explicitly. [Android managed configurations](https://developer.android.com/work/managed-configurations).

## Safe qualification and rollout

1. Inventory firmware/MX/StageNow/EHS versions and selected feature entitlements. Choose existing customer EMM or staged-only pilot. Preserve current scanner configuration and a tested administrator recovery route.
2. Produce a versioned, customer-parameterized profile with no repository secrets. Review settings and exclusions before generating installable artifacts. Keep development debugging available on a development-only profile.
3. Test on a spare TC22 first: network/trust, installation, app approval, scan delivery, camera uploads, location behavior, Home/Back/menu/logout and orientation. Check reboot and network-change behavior, plus management connectivity loss.
4. Verify restrictions and recovery together: app allowlist, settings access, permission denial, policy update, rollback and retained data. Test the dynamic status option separately, including logout/remote revocation, stale state and lock-screen visibility.
5. After signing/distribution decisions, qualify signed upgrades and management deployment in a small pilot ring. Expand only after documented acceptance. Device-owner enrollment/reset and production kiosk activation remain explicit rollout actions, not part of this research.
