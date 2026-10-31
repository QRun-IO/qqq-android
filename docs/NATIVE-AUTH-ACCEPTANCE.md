# Native navigation and OAuth2 acceptance

On October 31, 2026, the separate sample application test package ran on the physical Zebra TC22. The terminal instrumentation summary was `OK (20 tests)`, elapsed 49.235 seconds, with `INSTRUMENTATION_CODE: -1` (the normal successful Android instrumentation result). No operator identifier, barcode, credential or raw device log is retained here.

The selected classes were NavigationTests, SemanticInputTests, SessionTests and OAuth2LoginTests. They cover the signed-out drawer, Back and guarded app/process navigation, secondary Settings/Diagnostics, input/account behavior, and seven OAuth2 cases:

- An advertised browser flow creates exactly one semantic exchange and opens the authorized app catalog. The actual AppAuth request uses S256 and exactly one `prompt=login` parameter.
- Browser cancellation does not exchange a code or offer a password fallback.
- A mismatched browser state never exchanges a code.
- A cancelled attempt cannot be revived by a late successful browser result.
- A cancelled exchange discards a known late session at its original server without adopting it.
- Missing native advertisement offers neither password nor browser login.
- A mismatched discovery issuer is rejected before browser launch.

The initial run passed 19 cases and exposed Back during drawer-opening animation. The handler now consumes Back while the drawer is open or targeting Open; the test helper settles the UI before issuing hardware Back. The rebuilt suite passed all 20 cases. The follow-up bounded build also passed 102 JVM tests, scanner lint, host debug/E2E APK assembly and native-test compilation in 1m3s (266 tasks, 34 executed).

This is actual handheld execution with a synthetic QQQ repository, injected HTTPS discovery metadata and an instrumentation monitor returning AppAuth activity results. It does not establish a live Authentik login, an actual browser redirect through the exported callback activity, browser-authorization rotation/process-death behavior or configured shared-operator reauthentication. Those remain deployment acceptance gates in [AUTHENTICATION.md](AUTHENTICATION.md). This run did not replace the normal operator scanner app or modify its data. No production signing or release delivery is implied.
