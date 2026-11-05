# Checklist

Open work only, as of **2026-11-05**. Current evidence: [SESSION-STATE](SESSION-STATE.md). Completed and superseded items: [history](history/README.md).

## Hardware and release qualification

- [ ] Qualify actual GS1 label media and physical printed-label rescan. Synthetic capture/render evidence does not close this check.
- [ ] Verify actual production-APK native Authentik login, logout and account switching; awaiting the MC wireless-debug endpoint. Real-server discovery (UI API 1.10, strict nonce, scanner and printing channels) is already verified; no native-Authentik PASS is claimed.
- [ ] Publish signed candidate 1.0.1 (versionCode 3, APK SHA-256 `5124716B...AC2717F`, same permanent signer), checksum and installation instructions after remaining qualification. The published release is still the 1.0.0 signed preview.
- [ ] Publish and distribute full release assets to remaining devices; validate MDM/company profile and wallpaper separately. A customer deployment needs a customer HTTPS URL with a trusted certificate.
- [ ] Printing: the next installer remains with the active printing owner (print nonce fix PR 11 postdates published print 1.1.1).
- [ ] Optional, non-blocking: automatic APK signing CI. Direct APK release does not depend on it.

Done (November 5, do not repeat): MC9400 migration with release 1.0.0 trusted-HTTPS enrollment then in-place 1.0.1 update preserving login/enrollment; Apps/ScanCapture navigation and EMDK Scanner-ready observed; operator-confirmed physical Saved scan verified in the backend (CODE128, AIM, raw bytes, user, device; actual barcode differed from the requested literal). Live photo policy verified with a signed MC9400 synthetic gallery photo (`AVAILABLE`, exact parent/type/size/hash).

Updated TC22 native photo acceptance passed: synthetic camera/gallery uploads, the newly captured photo's thumbnail/full preview, parent scoping, rotation and forced-logout cleanup, with semantic API checks. The disposable test packages were removed and the normal app reopened. Native Authentik remains a separate open check.

## Dependencies and broader coverage

- [ ] Adopt public compatible Core/Collab/document/files/scripting/bridges inputs through a reviewed companion-server change, and pass the final public cohort gate.
- [ ] Verify company profile / EHS / OEMConfig / MDM deployment ([DEVICE-PROVISIONING](DEVICE-PROVISIONING.md), [COMPANY-PROFILE](COMPANY-PROFILE.md)).
- [ ] Resolve applicable [legacy source follow-ups](LEGACY-SOURCE-FOLLOWUPS.md).
- [ ] Extend specialized forms, reports and other unsupported [native components](UI-API.md) with conformance and E2E coverage as needed. Read-only table/record browsing and record photo attachment are implemented.

Offline queueing and background synchronization remain outside version one. The November 4 TC22 operator pass is accepted and should not be requested again.
