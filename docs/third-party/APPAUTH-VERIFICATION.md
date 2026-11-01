# AppAuth 0.11.1 package verification

Read-only verification on October 31, 2026. This records attribution evidence, not legal clearance. It supplements the earlier 130-entry/native qualification with the new OAuth2 dependency.

- Resolved `net.openid:appauth:0.11.1` AAR SHA-256: `D9FF43B14F7EE8E81BC802148190AD808BEB5389953E6E6FFA53CD5575EA611E`.
- POM SHA-256: `F1D8F1CC0A94E0F6FDF74B8C094F0FF37887B91D5C12B2B7E7B0436F39C76C3D`. It identifies AppAuth for Android, Iain McGinniss, the OpenID repository and Apache Software License 2.0.
- The AAR and nested classes.jar contain no separate LICENSE/NOTICE entry; the AAR contains no native library.
- Upstream tag `0.11.1`, tree `5966cc7efc1c7cacf78598ecae45b5e7d9365f9c`, contains root LICENSE and no separate NOTICE. Its LICENSE blob is `2bb9ad240fa04c8cf706a4901c4807878e90c2dc`, 10,172 bytes, SHA-256 `59899C6091B540582ED617E8EEAAC4919DC985CCFC35459EE9752B699BE5205B`.
- The bundled Apache text contains those complete terms plus the standard application appendix. Different full-file hashes do not indicate missing license terms.

The inspected debug and E2E APKs each contain the generated `res/raw/aboutlibraries.json` byte-for-byte: 131 component entries, comprising 126 external runtime entries and five supplemental notices, with nine complete license/notice texts. AppAuth records its exact coordinate/version, author, description and website/SCM with an Apache-2.0 reference. Every referenced text is nonempty. Settings' existing offline license viewer uses these bundled definitions.

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `CD048AA081E6F34DC53A17F5C535C10116E7C2CF51E1E0234A3330596E979A25` |
| Debug notice report and APK resource | `F624D7996198AD9A47DBB21D8400E7630DE7A251E4AB808D8860B34C0631F4D8` |
| E2E APK | `608F07B898ACE1F2E91263749C2B46D5F4015828CD8646A3377C14A77506D7C5` |
| E2E notice report and APK resource | `4880E92145CB314BF8D31AAB23E014A38E68B2D9BBF9DF9095BEDFB3E0964A6F` |

Both APKs contain exactly eight native libraries, all matching the prior [native provenance inventory](native-binaries-provenance.json). AppAuth adds none. These hashes identify the inspected bytes; subsequent rebuilds require their own byte verification. The earlier unsigned release APK was not rebuilt for this addition and is not an AppAuth-enabled release. Production signing and distribution remain deferred.

Primary evidence: [tagged upstream source](https://github.com/openid/AppAuth-Android/tree/0.11.1), [tagged license](https://github.com/openid/AppAuth-Android/blob/0.11.1/LICENSE).
