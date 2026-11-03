# Real Authentik native acceptance (opt-in, physical TC22)

This is the only test that runs the Android OAuth2 callback against a real provider. It is skipped by default
(`RealAuthentikAcceptanceTest` needs `-e realAuthentik true`) and is never part of the synthetic E2E scenarios.
Until it passes on the device, [AUTHENTICATION.md](AUTHENTICATION.md) makes no claim of an Android Authentik round trip.

## What it exercises

The unmodified flow: `oauth2.signIn` -> real AppAuth external browser -> the real provider -> the exported
`OAuth2RedirectActivity` (`io.qrun.mobile.scanner.e2e.oauth://callback/oauth2redirect`) -> the real QQQ code exchange ->
device pending -> real administrator approval (`mobileDeviceApprove`) -> catalog; then operator A's **Sign out of company**
(RP-initiated, through the browser) and a fresh login of operator B without clearing any browser state. It asserts the
principal QQQ shows (`Current User: ...`), exactly one ACTIVE session for the device, A's session no longer ACTIVE, B's
different, an optional exact server actor, and that no password form is ever offered. It injects no callback, ActivityResult,
token or discovery document.

## Trust and transport

- `scanner-host/src/e2e/res/xml/network_security_config.xml` (E2E package only) keeps system trust and the existing cleartext
  `127.0.0.1` fixture traffic, and additionally lets the literal host `localhost` trust a **user-installed** CA over HTTPS only.
  Nothing is pinned or bypassed; debug, qa and release builds are unchanged.
- The temporary fixture CA must be installed by the person holding the device, through Android settings (a screen lock/PIN is
  required), and removed afterwards. Neither this test nor the launcher installs, probes or changes trust. The browser must
  also trust that CA, otherwise it shows a certificate warning: do not continue past it.

## Prerequisites

The provider fixture and QQQ are running (QQQ `https://localhost:18443`, Authentik `https://localhost:19443`) with the exact E2E
callback registered for login and post-logout; a physical TC22 with `ANDROID_SERIAL` set only in the environment, unlocked and
awake; the disposable packages absent; and a private config file **outside the repository** (it holds a live administrator
session cookie):

```json
{
   "fixtureType": "real-authentik-android",
   "qqqOrigin": "https://localhost:18443",
   "adminCookie": "<Cookie header value of an admin QQQ session; never printed>",
   "operators": {
      "A": { "display": "<exact Current User text for the first operator>", "actor": "<optional server actor value>" },
      "B": { "display": "<exact Current User text for the second operator>", "actor": "<optional server actor value>" }
   },
   "sessionActorField": "<optional mobileSession column holding the actor; omit to skip>"
}
```

## Run

```text
./gradlew :scanner-host:assembleE2e :scanner-host:assembleE2eAndroidTest
node scripts/ci/real-authentik-android.mjs --config <private-config.json> --confirm-temporary-ca-installed
node --test scripts/ci/real-authentik-android.test.mjs      # guard tests, no device needed
```

The launcher refuses emulators and any device that is not a Zebra TC22, refuses a config inside the checkout or with a different
origin, and refuses if `io.qrun.mobile.scanner.e2e` or its `.test` package already exists (it never uses `install -r`). It adds
`adb reverse` for `tcp:18443` and `tcp:19443` only when absent (never rebinding; a differing existing mapping is an error),
never touches `tcp:8080` or any other mapping, never reads or changes the normal `io.qrun.mobile.scanner` package or its data,
pushes the config through `run-as` stdin (never to shared storage), and afterwards removes only the pushed file, the two
disposable packages and the reverse mappings it created. Output is fixed stage text and a PASS/failure sentence: no serial, URL,
cookie, token or credential. The operator types provider credentials in the browser when an `ACTION:` line appears (bounds: 45 s
for the browser to appear, 600 s per login, 300 s for company logout, 40 min total).

## Limits

No browser automation is included, so the operator enters credentials. The test cannot verify CA installation; a missing CA
shows up as the browser never opening within the bound. Process death during the browser step loses the in-memory attempt by
design; rerun. A pass proves this provider/server/package combination only; it is not production-provider, release-build or
EMDK trigger qualification.
