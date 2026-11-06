# Installing QQQ Mobile directly

Use this for the **production-signed** APK on a small number of devices. Move to MDM later (see [RELEASING](RELEASING.md)). Automated scanner-APK signing/publication is not configured; that is not a blocker to direct installation.

This guide is not the local demo. The demo uses the development-signed debug APK, plain HTTP and `adb reverse` port forwarding to a workstation server; see [README](../README.md#installing-on-a-device). The two builds have different signers and cannot update each other.

**Current release status (November 5, 2026):** the only published release is the signed preview 1.0.0 below. A signed **1.0.1 (versionCode 3) candidate** exists but is **not published and has no download link**; this guide does not point to one. Use the table below for what is actually hosted. It is superseded by the planned **1.0.2 (versionCode 4)** candidate (not yet built; see [RELEASING](RELEASING.md#next-candidate-102-versioncode-4)). When a newer version is published, use the file, checksum and link given by the release owner, and verify the same signer.

| Candidate (not published) | Value |
| --- | --- |
| Version | 1.0.1, versionCode 3 |
| APK SHA-256 | `5124716B59CAD303717E7834FE64DDC1537E33A25C9AB75A6EA1ACF03AC2717F` (15015419 bytes) |
| Signing certificate SHA-256 | Same as below (`BDDB2D2F...5B17A9FF`) |

Details and qualification status: [RELEASING](RELEASING.md).

| Item | Value |
| --- | --- |
| Source | [Signed preview release scanner-v1.0.0](https://git.kof22.com/QRun-IO/qqq-android/releases/tag/scanner-v1.0.0) (prerelease; forge sign-in required) |
| File | `QQQ-Mobile-1.0.0.apk`, with `SHA256SUMS` on the same release page |
| Application ID | `io.qrun.mobile.scanner` |
| Version | 1.0.0, versionCode 2 |
| APK SHA-256 | `EE5AC896B8985AF6F11B2E883955925D6571A2913D74A4AACCDB564B79394D51` |
| Signing certificate SHA-256 | `BDDB2D2F45A5925C643A83ABAF616C1EAB2387DC5CDA7B645A6F4C0D5B17A9FF` |

The same APK is used for every customer; branding comes from the server at runtime. Download the APK and `SHA256SUMS` from the release page and compare the SHA-256 before installing, then verify the signing certificate matches the value above. The checksum shows the file is intact, not who signed it. This is a preview, not a stable release. Only the APK and `SHA256SUMS` are hosted; there is no hosted install kit.

**Device evidence.** *Historical (November 4):* this APK was installed and launched once on a fresh Zebra MC9400 (Android 13) and showed the Connect screen; it was not yet enrolled. *Current (November 5):* with the user's explicit authorization, the MC9400 was migrated: 1.0.0 enrollment over trusted HTTPS passed, then an in-place 1.0.1 update preserved login and enrollment. Apps/ScanCapture navigation and EMDK Scanner-ready were observed, and the operator confirmed a physical Saved scan that the backend verified. These checks are done and need not be repeated for this device. The 1.0.1 candidate is still unpublished, native Authentik login/logout/account switching is not yet qualified with the production APK, and the TC22 keeps its original development-signed installation.

## Before you start

- The device is connected to Wi-Fi.
- You have a customer **HTTPS** QQQ server URL with a trusted certificate that the device can reach. Release builds reject HTTP, and certificate checks must not be weakened. Use the address you are given (`https://<your-server>`); do not guess one. Local-network qualification used a temporary private network and is not a customer deployment.
- You have the QQQ credentials for the sign-in the server offers. None are in this guide.

## Install: copy the file (recommended)

1. Copy the verified `QQQ-Mobile-1.0.0.apk` to the device's **Downloads** folder.
2. Open **Files**, go to Downloads, and tap the APK.
3. If Android asks, allow that Files app to install apps, then continue. You can turn the permission off again afterward.

## Install: USB debugging (alternative)

Requires USB debugging enabled on the device, a computer the device has been asked to trust, and [Android Platform Tools](https://developer.android.com/tools/adb).

```powershell
adb devices
adb -s DEVICE_SERIAL install -r QQQ-Mobile-1.0.0.apk
```

Replace `DEVICE_SERIAL` with the serial of the device you chose from `adb devices`. Always name the target with `-s`. Do not add `-d` (downgrade) or `-t`.

## First launch and enrollment

1. Open **QQQ Mobile** and enter the HTTPS server URL, then tap **Connect**.
2. Sign in with the method the server shows.
3. The device enrolls and shows a **Device ID** with status pending.
4. A QQQ administrator reviews the device, sets its name and approves it in QQQ.
5. On the device, tap **Check approval**, then open the server's apps (**Apps**).

Grant location when Android asks; scanning works without it. Zebra devices with EMDK need no DataWedge profile set up by hand ([Zebra scanning](ZEBRA-SCANNING.md)).

## Confirm the first new install

On the first device of a new customer deployment, check each of these (already done for the MC9400 qualification above):

- The footer shows **Connected**.
- One scan is captured and the server keeps it (**Saved scan**).
- Navigation to **Apps** and back works.
- Echo: choose the printer, document and copies once. The server does the printing.

Printer clients belong on the server side. No printer driver goes on the scanner.

## Updates

For a later build signed by the same key with a higher versionCode, run the same `adb -s DEVICE_SERIAL install -r QQQ-Mobile-<version>.apk` command. Data is preserved.

## Devices running the development-signed build

Devices running the development-signed demo build (the TC22) cannot be updated by this APK, because the signer differs. Android is expected to refuse the install. This is not a fault: do not uninstall the app or reset the device to get around it. Moving that device needs an explicitly authorized, planned one-time migration that includes re-enrollment.

## References

- Android Debug Bridge, [installing and updating apps](https://developer.android.com/tools/adb#move) and [enabling USB debugging](https://developer.android.com/tools/adb#Enabling): <https://developer.android.com/tools/adb>
