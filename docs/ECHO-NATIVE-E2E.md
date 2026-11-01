# Echo native UI acceptance fixture

The explicit Echo scenario exercises the real Android host, TABLE login, enrollment/approval, semantic printer/media choices, real Echo process and H2 persistence. Its test-only backend accepts **simulated** jobs. Passing it does not qualify EMDK, barcode rendering, printer adapters or physical output.

The backend owner supplies `com.kingsrook.examples.echo.fixture.EchoHttpFixture` from the isolated optional Echo test module, launched with a free loopback port, `ECHO_FIXTURE_TYPE=echo-ui-e2e` and private `ECHO_FIXTURE_SCANNER_PASSWORD`/`ECHO_FIXTURE_ADMIN_PASSWORD`. Readiness is `ECHO_UI_E2E_READY port=N simulatedPrinting=true`. It uses in-memory H2 and no real printing/generation adapter. Root owns launch, the exact private dependency tuple and approval of the concrete APK/fixture cohort.

The credential file must stay private and contain these fields with newly generated passwords of at least 16 characters:

```json
{
  "fixtureType": "echo-ui-e2e",
  "simulatedPrinting": true,
  "baseUrl": "http://127.0.0.1:PRIVATE_FREE_PORT/",
  "username": "scanner",
  "password": "PRIVATE_GENERATED_PASSWORD",
  "adminUsername": "admin",
  "adminPassword": "PRIVATE_GENERATED_PASSWORD"
}
```

The runner refuses normal scanner ports 57832/51631 and printing ports 18081/18086, non-loopback/decorated URLs, the wrong purpose, unsimulated fixtures, incompatible core/mobile contracts and branding other than `Echo UI E2E — simulated printing`. It requires UI API1.10.0/mobile-scanners1.4.0 and checks APK package identities. Only a selected physical Zebra TC22 and absent disposable `io.qrun.mobile.scanner.e2e`/`.test` packages are accepted. Normal and QA packages/data remain untouched. Location reporting is disabled only in the new disposable package, and no screenshots are taken.

Preparation requires no device execution:

```text
node --test scripts/ci/scanner-echo-guards.test.mjs
./gradlew :scanner-host:testDebugUnitTest :scanner-host:lintDebug :scanner-host:assembleE2e :scanner-host:assembleE2eAndroidTest
```

Only after root launches the private fixture and approves the exact cohort:

```text
node scripts/ci/scanner-device-echo-e2e.mjs --serial TC22_SERIAL --fixture PRIVATE_ECHO_FIXTURE.json
```

The native test signs in, enrolls and approves through existing helpers; opens Apps → Echo; selects `Simulated printer` / `Simulated 4 x 6 in — 300 dpi` once through the semantic possible-value controls; injects two public synthetic values with CODE128/QR types through the E2E-only driver; checks `Print queued` and absent manual controls; rotates/recreates without another acceptance; then uses Apps/Leave and verifies the run closes. Independent reads use standard QQQ `echoScan` and read-only `echoFixtureRun` queries as the scanner with approved-device headers, retaining the real actor/device locks. No custom business verification endpoint or administrator bypass is used.

The existing OpenAPI proxy audits all observed requests and requires native cancellation. Fixed stage markers and operation IDs/counts, APK hashes and a simulation-labelled `echo-proof.json` are written under `scanner-host/build/native-device-e2e/`; credentials, serial and opaque identifiers are redacted from instrumentation output. No normal app data, global animation settings, DataWedge profile or existing forwarding is replaced. The runner removes only its own reverse and newly installed disposable packages; cleanup failures fail the run and its proof.

At preparation time, compilation and source/unit guards are the only authorized checks. Native/device acceptance remains pending until the explicit approved invocation completes; do not describe this fixture as physical printing evidence.

Recorded acceptance: all5stages passed onTC22 with169JVMtests,8Nodeguards,zero-warninglint and86/86documentednative requests. Printing is simulated. The proof explicitly leaves physicalScannerQualified and physicalPrintingQualified false.
