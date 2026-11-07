# Changelog

## 1.0.2 (versionCode 4), candidate

Not yet built, signed, tagged or published (prepared 2026-11-06). Supersedes the unpublished signed 1.0.1 (versionCode 3) candidate and updates it in place with the same permanent signer.

- Company sign-out now completes: the provider logout request runs before the scanner host ends its device session, so the browser end-session page opens. Previously the device-session end ran first, removed the QQQ session server-side, and `nativeLogoutData` was never returned; observed on the MC9400 with the production APK against the real Authentik server. (PR 17)
- The signed-in user's name is shown after browser (OAuth) sign-in instead of a blank current user. (PR 17)
- The scanner host reports the installed app and OS version on every session heartbeat, so scan receipts show the new version after an in-place update. Effective with mobile-scanners server 1.6.0; older servers ignore the keys. (PR 18)

## Unreleased

- Add the QQQ Mobile scanner host for metadata-defined native apps and processes.
- Add administrator-approved device enrollment and Zebra DataWedge single-barcode capture.
- Add table-backed login, current QQQ HttpOnly session handling, and server-side logout.
- Preserve pending scan identity for explicit retry and require a matching server acknowledgment.
- Add scanner APK/test/lint CI and an optional emulator workflow alongside managed library CI.
