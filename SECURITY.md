# Security policy

Do not open public issues containing vulnerabilities, credentials, device tokens, session cookies, or real scan data.

Report vulnerabilities privately through [GitHub security reporting](https://github.com/QRun-IO/qqq-android/security/advisories/new) when available, or email **security@qrun.io**. Include the affected commit/APK version, Android and QQQ versions, a synthetic reproduction, and the impact.

The scanner host is experimental. Report issues against the current development version; there is not yet a stable scanner APK support schedule. The broader [QQQ security policy](https://github.com/QRun-IO/qqq/blob/develop/SECURITY.md) applies to the framework.

Release installations must use HTTPS, authenticated users, and approved device credentials. The debug APK permits HTTP for local development only. Device enrollment secrets and sessions must not be included in backup, transfer, logs, or process values. Scanner-provided metadata is client input rather than proof of hardware identity.
