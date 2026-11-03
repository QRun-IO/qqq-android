# Device Owner enrollment (destructive, separate from the safe profile)

Not generated and not part of `generate-company-profile.mjs`. Fully managed (Device Owner) provisioning is intended for a new or factory-reset handheld (`docs/DEVICE-PROVISIONING.md`, Android device management). Doing it wipes the device's data and settings, including the current scanner and printing setup.

Do not start unless all of these are recorded and approved by the owner of the device fleet:

1. The customer EMM/MDM, its enrollment method and Zebra OEMConfig package (`com.zebra.oemconfig.release` on Android 13+).
2. A spare device, not the development TC22, with the ordinary safe profile already reviewed.
3. The recovery route if the EMM or EHS locks the device out, and the factory-reset authority.
4. Confirmation that the QQQ device approval and Android management states stay separate.

Enrollment credentials, tokens and QR payloads belong in the EMM, never in this repository.
