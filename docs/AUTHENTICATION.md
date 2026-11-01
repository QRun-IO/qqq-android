# Scanner authentication

The server selects the login provider through the versioned QQQ UI API. `TABLE_BASED` uses the native username/password form. `OAUTH2` uses the organization's external browser only when the server explicitly advertises the supported native session flow. This supports Authentik without embedding an Authentik URL or client secret in a customer-specific APK. Unknown providers and unsupported flows show a configuration message; OAuth cancellation never falls back to a local password form.

The shared scanner APK accepts only `TABLE_BASED` and the API 1.9 `OAUTH2` flow described here. Legacy `AUTH0` support belongs to the shared library and sample host; the scanner rejects that metadata before opening a browser. Its unused Auth0 manifest placeholders do not configure a customer login. Legacy callback lifecycle tests do not establish a live Auth0 provider round trip. This matches the [README's scanner support scope](../README.md).

## Native browser contract

Before opening a browser, the client requires UI API `>=1.9.0 <2.0.0`, the `nativeSessionLogin` feature, and this typed descriptor in `/qqq/v1/metaData/authentication`:

```json
{
  "type": "OAUTH2",
  "name": "organization",
  "values": {
    "clientId": "server-exchanged-client",
    "scopes": "openid profile email",
    "nativeSessionLogin": {
      "flow": "AUTHORIZATION_CODE_PKCE_SESSION",
      "codeChallengeMethod": "S256",
      "prompt": "login",
      "issuer": "https://identity.example/application/o/company/",
      "discoveryUrl": "https://identity.example/application/o/company/.well-known/openid-configuration",
      "redirectUris": ["io.qrun.mobile.scanner.oauth://callback/oauth2redirect"]
    }
  }
}
```

Values above are illustrative. The actual server advertises its issuer, discovery URL, client and scopes. In Authentik global issuer mode, `issuer` may be the host root while `discoveryUrl` remains under the application path. The client does not guess either value. Server, issuer, discovery and discovered authorization/token endpoints require HTTPS for this flow, including on debug builds. Existing local TABLE_BASED testing may still use its debug HTTP policy.

AppAuth 0.11.1 opens an external browser with authorization code, query response mode, random state and nonce, S256 PKCE, and the advertised `prompt=login` policy. Missing or other prompt policies are unsupported. This asks the provider to authenticate the operator again, so an ordinary retained browser SSO session does not silently select the previous handheld user. The client validates the discovery issuer and exact callback, then submits `authenticationFlow`, `code`, `codeVerifier` and `redirectUri` to the existing `/qqq/v1/manageSession`. QQQ exchanges the code using its server-held secret and establishes its normal session. The optional `nativeClientId` metadata describes a separate public-client bearer flow and is never substituted in this stored-session exchange.

The nonce is bound to the original authorization request; the client does not receive an ID token and does not claim to validate its nonce. Provider token validation and refresh remain server responsibilities. No new authentication endpoint or local account system is introduced.

## Deployment setup

An administrator must configure the native session descriptor in QQQ and register the exact corresponding callback in the existing identity provider's allowed redirect list:

| Installed package | Callback |
| --- | --- |
| Normal scanner | `io.qrun.mobile.scanner.oauth://callback/oauth2redirect` |
| Isolated E2E scanner | `io.qrun.mobile.scanner.e2e.oauth://callback/oauth2redirect` |

Each customer uses the shared normal callback; branding remains server-driven. Register only packages actually deployed. Do not use wildcards or Authentik's first-use redirect registration. The provider must permit authorization code with S256 PKCE for the configured server-exchanged client. Keep its secret in existing server secret management, never in APK resources, client metadata or documentation. Do not register the E2E callback on production merely to run tests.

## Lifecycle and privacy

The pending attempt is kept in memory and bound to server, provider metadata, client, scopes, prompt and callback. Rotation preserves it; process death requires starting again. Attempts expire after five minutes and are consumed once. Server change, logout, cancellation and reset invalidate the attempt. A late exchange cookie is never automatically adopted by the HTTP interceptor. If an abandoned response provides a new session identifier, the client attempts a bounded logout at its original server without replacing the current session. If a network failure prevents receiving that identifier, server expiry remains the recovery boundary.

Provider callback URLs, authorization codes, verifiers and raw errors are not shown in diagnostics. App sign-out ends the QQQ session. The browser's identity-provider SSO session may remain; global provider logout is not implied by this contract.

Password and legacy Auth0 flows also bind each attempt and provisional cookie to
its initiating server, metadata and generation. A response cannot restore a
signed-out operator or replace a later operator after a server change. The
repository returns provisional session results: custom callers must validate
their pending attempt before explicitly adopting the session. Repeated logout
effects share one logical sign-out; local credentials are cleared independently
of bounded server revocation and the browser provider's outcome.

The fresh-login prompt is a request to the provider, not cryptographic proof of a newly validated `auth_time`. Before shared-handheld deployment, test with the prior operator still signed into the browser: the next login must require authentication, allow the next operator to identify themselves, and attribute subsequent scans to that operator. Custom provider flows or upstream SSO may affect this behavior and require provider configuration; this client change does not modify them.

## Host storage and transport

Applications embedding the shared libraries must keep reusable session storage under
Android's `noBackupFilesDir` and exclude it from both cloud backup and device transfer.
Ordinary preference storage can carry session authority to a restored installation.
The scanner and sample hosts disable backup and define explicit transfer exclusions.
The sample now uses a no-backup store; it deliberately does not migrate its previous
backup-eligible settings, so an upgraded sample requires a fresh login.

The sample permits HTTP development environments and cleartext traffic only in debug
builds. Its release manifest rejects cleartext even for an old restored HTTP address.
Embedding applications must enforce the same release transport policy before allowing
authentication. The scanner's existing release HTTPS requirement remains in force.

## Verification status

The latest security qualification passes 109 JVM tests, scanner lint and APK assembly. All 32 selected native navigation/input/account/authentication/storage cases pass on TC22, including seven synthetic AppAuth request/result scenarios and ten additional authentication lifecycle regressions. See [the scoped security evidence](SECURITY-REVIEW-2026-10-31.md) and [native OAuth evidence](NATIVE-AUTH-ACCEPTANCE.md) for their boundaries. Earlier generated AppAuth notices were verified in debug/E2E APKs. A real configured Authentik sign-in remains a separate acceptance gate: it requires a reachable HTTPS QQQ server advertising the descriptor, an explicitly registered callback and an authorized test account. No live provider configuration was changed as part of implementation.

Primary references: [AppAuth Android 0.11.1](https://github.com/openid/AppAuth-Android/tree/0.11.1) documents external-browser authorization, PKCE and server-side exchange when a confidential secret is needed. [Authentik OAuth2 provider documentation](https://docs.goauthentik.io/add-secure-apps/providers/oauth2/) documents PKCE, exact redirect registration and global issuer mode.

Authentik's [version 2026.8.3 authorization implementation](https://github.com/goauthentik/authentik/blob/version/2026.8.3/authentik/providers/oauth2/views/authorize.py#L550-L560) handles `prompt=login` by requiring a new login event. Its [current ForceAuthn documentation](https://docs.goauthentik.io/add-secure-apps/providers/saml/#forceauthn) describes the analogous authentication-flow behavior. Actual configured-provider acceptance remains required.
