# Checklist

Open work only, as of **2026-11-05**. Current evidence: [SESSION-STATE](SESSION-STATE.md). Completed and superseded items: [history](history/README.md).

## Hardware and release qualification

- [ ] Qualify actual GS1 label media and physical printed-label rescan. Synthetic capture/render evidence does not close this check.
- [ ] Confirm one physical MC9400 trigger scan in the backend. The normal app is installed, signed in and approved.
- [ ] Qualify the production-signed build against an HTTPS server: enrollment, scanner path, navigation and update.
- [ ] Verify an actual native Authentik/provider callback, logout and account switching.
- [ ] Distribute the qualified signed build to remaining devices; validate MDM/company profile separately. The published signed preview predates the current record UX update.
- [ ] Automatic APK signing/publication remains owned by the shared CI design; no new pipeline is configured here.

## Dependencies and broader coverage

- [ ] Adopt public, coherent Generation/Record Files/Scripting owner inputs through a reviewed companion-server change and a fresh complete gate.
- [ ] Verify company profile / EHS / OEMConfig / MDM deployment ([DEVICE-PROVISIONING](DEVICE-PROVISIONING.md), [COMPANY-PROFILE](COMPANY-PROFILE.md)).
- [ ] Resolve applicable [legacy source follow-ups](LEGACY-SOURCE-FOLLOWUPS.md).
- [ ] Extend specialized forms, reports and other unsupported [native components](UI-API.md) with conformance and E2E coverage as needed. Read-only table/record browsing and record photo attachment are implemented.

Offline queueing and background synchronization remain outside version one. The November 4 TC22 operator pass is accepted and should not be requested again.
