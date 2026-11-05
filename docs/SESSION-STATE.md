# Current status

As of **2026-11-05**. Scope: scanner host and maintained local Echo workflow. [TODO](TODO.md) tracks remaining work; [history](history/README.md) preserves earlier evidence. This is a development preview, not production 1.0 qualification.

## Record browsing and photos

The existing TC22 app now opens permitted tables and records through the standard semantic metadata/query/get API. Tables support search, exact text filters, counts and stable paging; Back restores page/search/filter context. Records retain identity above Details, related-record and Photos tabs, with collapsible groups and a section selector. Dates/timezones, currency and empty values are readable; identifiers keep leading zeros.

**Record → Actions → Add photo** opens Take photo / Choose photo. Saved photos show parent-scoped lifecycle status and approved thumbnails/previews. Quarantined originals remain unavailable. Existing upload identity, retry handling, server authorization, pending-navigation guards and logout cleanup are preserved.

The maintained companion demo supplies Test Data: 1,000 orders, 39 fields in six groups, 12,000 items and 3,000 history entries. See [screenshots and verification](RECORD-UX-2026-11-05.md).

## Verification and installation

- **232 unit tests** pass: core 52, mobileapp 57, host 123; lint reports **0 errors/0 warnings**. Debug, E2E and instrumentation builds pass. Eight runner guard tests pass.
- Isolated MC9400 native acceptance passes rich-record paging/groups/relations/search/filter/Back, camera/gallery persistence, parent-scoped saved photos, approved preview, rotation, pending-photo navigation protection and forced-logout cleanup. All requests match the semantic API. Process-photo regression passes separately.
- The final date fallback recognizes default `%s` server metadata. It received unit/lint/debug-build gates and actual TC22 checks; the full native suite preceded that date-only follow-up.
- Existing TC22 app updated in place with signer and installed bytes verified: SHA-256 `F71033C835AC3EB361A04D78C842947C5A04C85CE1AB1B999AA8CEFBFC9D7A95`. No uninstall/data clear or separate manual QA app. Existing login, enrollment and server configuration survived.
- Actual TC22 inspection confirms Actions and capture chooser, direct tabs, date/timezone and currency formatting, empty Photos state and order 26 → Android Back → page two. Automated camera/gallery inputs are synthetic; no new physical camera/print claim is made.

## Source and runtime

Protected `main` contains PRs 8/10, with signing/documentation through PR 14 (`464814f`). The user authorized commit, push and merge of the GS1/record/UX work through normal Gitea checks. No new artifact release was requested. The maintained server uses public QQQ RC.21 and Printing 1.0.0 plus qualified document/files/scripting owner inputs. Contract versions remain UI API 1.10, mobile-scanners 1.5, printing 2.3 and mobile-record-files 1.0. Device loopback 8080 forwards to the existing host port 57832.

The QRun-owned signing key and [signed preview `scanner-v1.0.0`](https://git.kof22.com/QRun-IO/qqq-android/releases/tag/scanner-v1.0.0) remain unchanged. That preview predates this debug update. It is not a stable release or an automatic signing/publication pipeline; see [RELEASING](RELEASING.md).

## Accepted and outstanding hardware work

The November 4 TC22 operator group pass remains accepted; do not request it again. MC9400 remains installed, signed in and approved, with a physical trigger/backend check outstanding. Physical GS1 print/rescan, native provider callback, signed-build HTTPS qualification, public owner-dependency adoption and fleet/MDM rollout remain open. Historical hashes, signed-preview provenance and older tests are preserved in [the prior checkpoint](history/SESSION-STATE-before-record-ux-2026-11-05.md).
