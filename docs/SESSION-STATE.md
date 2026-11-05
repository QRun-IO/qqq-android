# Current status

As of **2026-11-05**. Scope: scanner host and maintained local Echo workflow. [TODO](TODO.md) tracks remaining work; [history](history/README.md) preserves earlier evidence. This is not a final-release or full-qualification claim. See [Release facts](#release-facts-2026-11-05) below.

## Release facts (2026-11-05)

- **Published:** still `scanner-v1.0.0`, a signed preview. Signed candidate **1.0.1 (versionCode 3)** is **not published**, has no tag and no download link, and is not production-complete. Signer: existing permanent QRun key (certificate SHA-256 `BDDB2D2F...5B17A9FF`); no new key. APK SHA-256 `5124716B59CAD303717E7834FE64DDC1537E33A25C9AB75A6EA1ACF03AC2717F`, 15015419 bytes. Details: [RELEASING](RELEASING.md).
- **Source:** Android PR 15 `ab6e44c`, merged `c782aaaaba3f62422232436a9000af9d4b38fc82`, same app-source tree. Root verified installed APK bytes/signature/non-debuggable/16 KiB alignment; `lintRelease` 0 errors, 0 warnings, 5 informational; release build passed.
- **Done:** MC9400 migration (user-authorized): 1.0.0 enrollment over trusted HTTPS, then in-place 1.0.1 update preserving login/enrollment; Apps/ScanCapture and EMDK Scanner-ready observed; operator-confirmed physical Saved scan verified in the backend (CODE128/AIM/raw bytes/user/device; actual barcode differed from the requested literal). Not repeat work. TC22 keeps its original development-signed installation and data; the accepted November 4 manual all-pass is not rerun.
- **Network:** the local QQQ server stays on a PC, reached over trusted HTTPS temporarily through a user-approved private Tailscale network. This is not a customer production deployment; customer instructions require a customer HTTPS URL with a trusted certificate. TLS is not weakened; no hostnames, IPs, ports, device/vault IDs, private paths or passwords are published.
- **Server/Authentik:** real shared Authentik server discovery verified (UI API 1.10, strict nonce, scanner and printing channels/extensions/callbacks). Production-APK native login/logout/account switching is **open**, awaiting the MC wireless-debug endpoint; no native-Authentik PASS is claimed. Print nonce fix PR 11 (merged `abc3d0d`, 532 hosted tests passed) postdates published print 1.1.1; the next installer remains with the active printing owner.
- **Photo policy:** live. A signed MC9400 gallery synthetic PNG became a Saved photo, `AVAILABLE`, with exact parent/type/size/hash; the previous three photos were released normally. The app has no admission policy; the server receipt controls the message. The root is running the updated disposable TC22 E2E; no full new-preview or native PASS is claimed until it completes.
- **Open:** public compatible Core/Collab/document/files/scripting/bridges, final public cohort gate, GS1 physical print/rescan, full release-asset publication/distribution. Company profiles/wallpaper are tracked separately; signing CI is optional and not blocking a direct APK release. Release authorizations already exist.

Statements below were written before these facts and are historical where they conflict (for example "debug update", MC9400 trigger check "outstanding", signed-build HTTPS qualification "open").

## Record browsing and photos

The existing TC22 app now opens permitted tables and records through the standard semantic metadata/query/get API. Tables support search, exact text filters, counts and stable paging; Back restores page/search/filter context. Records retain identity above Details, related-record and Photos tabs, with collapsible groups and a section selector. Dates/timezones, currency and empty values are readable; identifiers keep leading zeros.

**Record → Actions → Add photo** opens Take photo / Choose photo. Saved photos show parent-scoped lifecycle status and approved thumbnails/previews. Quarantined originals remain unavailable. Availability is decided only by the server receipt/lifecycle; focused tests cover both `AVAILABLE` and `QUARANTINED`, and the native E2E expects `AVAILABLE` for the configured Echo fixture while retaining quarantine assertions for the generic fixture. Thirteen focused photo/API tests passed. A fresh synthetic gallery upload through the signed MC9400 app returned "Saved photo."; server readback verified availability, parent, type, byte count and SHA256. The three earlier demo captures were released through the normal audited admin process. Updated TC22 native photo acceptance passed synthetic camera/gallery upload, the captured photo thumbnail/full preview, parent scoping, rotation and forced-logout cleanup, with all requests checked against the semantic API. The disposable test packages were removed and the normal app reopened. Public captured-photo dependency publication and native Authentik qualification remain open. Existing upload identity, retry handling, server authorization, pending-navigation guards and logout cleanup are preserved.

The maintained companion demo supplies Test Data: 1,000 orders, 39 fields in six groups, 12,000 items and 3,000 history entries. See [screenshots and verification](RECORD-UX-2026-11-05.md).

## Verification and installation

- **232 unit tests** pass: core 52, mobileapp 57, host 123; lint reports **0 errors/0 warnings**. Debug, E2E and instrumentation builds pass. Eight runner guard tests pass.
- Isolated MC9400 native acceptance passes rich-record paging/groups/relations/search/filter/Back, camera/gallery persistence, parent-scoped saved photos, approved preview, rotation, pending-photo navigation protection and forced-logout cleanup. All requests match the semantic API. Process-photo regression passes separately.
- The final date fallback recognizes default `%s` server metadata. It received unit/lint/debug-build gates and actual TC22 checks; the full native suite preceded that date-only follow-up.
- Existing TC22 app updated in place with signer and installed bytes verified: SHA-256 `F71033C835AC3EB361A04D78C842947C5A04C85CE1AB1B999AA8CEFBFC9D7A95`. No uninstall/data clear or separate manual QA app. Existing login, enrollment and server configuration survived.
- Actual TC22 inspection confirms Actions and capture chooser, direct tabs, date/timezone and currency formatting, empty Photos state and order 26 → Android Back → page two. Automated camera/gallery inputs are synthetic; no new physical camera/print claim is made.

## Source and runtime

Protected `main` contains PRs 8/10, with signing/documentation through PR 14 (`464814f`). The user authorized commit, push and merge of the GS1/record/UX work through normal Gitea checks. No new artifact release was requested. The maintained server uses public QQQ RC.21 and Printing 1.0.0 plus qualified document/files/scripting owner inputs. Contract versions remain UI API 1.10, mobile-scanners 1.5, printing 2.3 and mobile-record-files 1.0. Device loopback 8080 forwards to the existing host port 57832.

The QRun-owned signing key and [signed preview `scanner-v1.0.0`](https://git.kof22.com/QRun-IO/qqq-android/releases/tag/scanner-v1.0.0) remain unchanged. That preview predates the record UX update; candidate 1.0.1 is unpublished. The preview is not a stable release or an automatic signing/publication pipeline; see [RELEASING](RELEASING.md).

## Accepted and outstanding hardware work

The November 4 TC22 operator group pass remains accepted; do not request it again. MC9400's physical scan/backend check and signed-build trusted-HTTPS enrollment/update are done (see Release facts). Physical GS1 print/rescan, native provider callback, public owner-dependency adoption and fleet/MDM rollout remain open. Historical hashes, signed-preview provenance and older tests are preserved in [the prior checkpoint](history/SESSION-STATE-before-record-ux-2026-11-05.md).
