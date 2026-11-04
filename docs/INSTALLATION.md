# Installing QQQ Mobile directly

Use this for the **production-signed** APK on a small number of devices. Move to MDM later (see [RELEASING](RELEASING.md)). Automated scanner-APK signing/publication is not configured; that is not a blocker to direct installation.

This guide is not the local demo. The demo uses the development-signed debug APK, plain HTTP and `adb reverse` port forwarding to a workstation server; see [README](../README.md#installing-on-a-device). The two builds have different signers and cannot update each other.

| Item | Value |
| --- | --- |
| File | `QQQ-Mobile-1.0.0.apk` (locally signed) |
| Application ID | `io.qrun.mobile.scanner` |
| Version | 1.0.0, versionCode 2 |
| APK SHA-256 | `EE5AC896B8985AF6F11B2E883955925D6571A2913D74A4AACCDB564B79394D51` |

The same APK is used for every customer; branding comes from the server at runtime. Compare the SHA-256 before installing. The checksum shows the file is intact, not who signed it.

This APK was installed and launched once on a fresh Zebra MC9400 (Android 13): install succeeded, the Connect screen appeared and the installed bytes matched the hash above. It has **not** been enrolled, scanned or navigated, because no HTTPS server was available, so these steps are not hardware-qualified until the first install is confirmed (below). That MC9400 now runs the development demo build instead.

## Before you start

- The device is connected to Wi-Fi.
- You have an **HTTPS** QQQ server URL the device can reach. Release builds reject HTTP. The server is not chosen yet: use the address you are given, and do not guess one.
- You have the QQQ credentials for the sign-in the server offers. None are in this guide.

## Install: copy the file (recommended)

1. Copy `QQQ-Mobile-1.0.0.apk` to the device's **Downloads** folder.
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

On the first device only, check each of these:

- The footer shows **Connected**.
- One scan is captured and the server keeps it (**Saved scan**).
- Navigation to **Apps** and back works.
- Echo: choose the printer, document and copies once. The server does the printing.

Printer clients belong on the server side. No printer driver goes on the scanner.

## Updates

For a later build signed by the same key with a higher versionCode, run the same `adb -s DEVICE_SERIAL install -r QQQ-Mobile-<version>.apk` command. Data is preserved.

## Devices running the development-signed build

Devices running the development-signed demo build (the TC22, and the MC9400 in demo mode) cannot be updated by this APK, because the signer differs. Android is expected to refuse the install. This is not a fault: do not uninstall the app or reset the device to get around it. Moving that device needs a planned one-time migration that includes re-enrollment.

## References

- Android Debug Bridge, [installing and updating apps](https://developer.android.com/tools/adb#move) and [enabling USB debugging](https://developer.android.com/tools/adb#Enabling): <https://developer.android.com/tools/adb>
