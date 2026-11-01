# October 31 client security review

The deep static review completed against published client revision `d1de0bf`.
It reported one medium and five low findings. Coverage is partial: external
server/provider/dependency implementations and some bundled binary implementation
review were outside the completed scope. This is not production release clearance.

| Finding | Severity | Current verification |
| --- | --- | --- |
| Delayed password completion after logout/server change | Medium | Fixed origin/generation binding and deferred cookie adoption; HTTP and native delayed-response cases pass. |
| Sample release cleartext traffic | Low | Debug-only HTTP environments/manifest override; packaged release denies cleartext. |
| Auth0 provider failure preserving local QQQ login | Low | Local sign-out is independent, bounded and idempotent; failed-provider, restart and remount cases pass. |
| Unauthenticated external scan broadcasts | Low | Trusted optional EMDK adapter and production receiver removal verified by actual DEX inspection,51 scanner JVM tests and an ordinary-UID/distinct-signer TC22 case: eight forged broadcasts cause zero process/record mutations; manual positive control succeeds. Actual physical-trigger positive control remains required before closing the complete scanner acceptance; normal operator installation is unchanged. |
| Legacy Auth0 callback crossing server origins | Low | Origin-bound callback/exchange lifecycle, one-use exchange and discarded-session cleanup; HTTP/native cases pass. |
| Sample session backup | Low | No-backup storage, disabled backups and explicit cloud/transfer exclusions; native storage and packaged release checks pass. |

The qualification batch passes 109 JVM tests (30 core, 46 mobile, 33 scanner),
scanner lint with no errors or warnings, debug/native-test APK assembly, and
unsigned sample release assembly. A physical TC22 run passes all 32 selected
navigation, input, account, synthetic OAuth/Auth0 lifecycle and host-storage cases
in 117.325 seconds. Ten new lifecycle cases cover delayed completion, duplicate
callbacks, failed provider logout, overlapping logout and logout-screen remounting.
These use synthetic repositories/browser callbacks; they do not prove a real
configured identity-provider round trip.

Packaged release inspection confirms backup and cleartext disabled, plus all ten
cloud/device-transfer exclusions. The wrapper now matches the official Gradle 8.7
checksum; see [provenance](WRAPPER-PROVENANCE.md). The new scanner debug APK hash is
`8c4d3886b4e994759f909677849dae26a333f476a036457c9ea76fe4566c512a`.
It has not replaced the operator's installed scanner app in this checkpoint.

Separate live dependency review identified affected Guava in the server's tested
classpath. A patched source pin is undergoing isolated consumer qualification;
the client APK does not contain that Guava artifact. Actual Authentik, trusted
physical scanner input, immutable Record Files publication and final deployment
and signing gates remain open. See [TODO](TODO.md) and [release prerequisites](RELEASING.md).
