# P0-S3 isolated SSH diagnostic dependency adoption

**Checked:** 2026-10-15. **Owner:** Android/platform developer with Terminal/Android developer. **Decision scope:** select the exact graph below for the finite native P0-S3 diagnostic. The primary agent accepted the source-level dependency recommendation, added the declarations and verified the initial seven-JAR graph. The final selection below upgrades the SLF4J API/NOP pair to 2.0.20; its exact artifact and notice research is complete, while the updated resolved-graph audit, final source checks, APK notice inclusion and Android/backend behavior require separate records. This is not the Console/Desktop product adoption, a distribution-license decision, P1-S4 completion or a supported CAP-SSH/CAP-TERMINAL outcome.

Read the [engine investigation](../03-platform/googlebook/p0-s3-ssh-engine-qualification.md), [platform experiment](../03-platform/googlebook/p0-s2-platform-decision.md), [P0-S3 stage](../05-delivery/phases/p0-qualification.md#p0-s3--ssh-engine-and-terminal-renderer-qualification) and [existing notice policy](../../qualification/probes/device-baseline/THIRD_PARTY_NOTICES.md). This research inspected official artifacts in memory; it did not execute dependency code, operate the device or copy an implementation into the repository.

## Exact graph and deliberate changes

SSHJ's published POM and Gradle metadata request SLF4J API 2.0.17, BC provider/PKIX 1.84 and asn-one 0.6.0. The complete additional runtime closure is six JARs before choosing a logging provider: `bcpkix-jdk18on` depends on same-version `bcutil-jdk18on`, which depends on `bcprov-jdk18on`; asn-one also requests SLF4J API 1.7.13. Its inspected source uses `Logger` and `LoggerFactory`, for which upstream preserves client binary compatibility. Deliberately resolve both older API requests to **2.0.20**, with one API version on the classpath. SLF4J's parent POM has test-only JUnit; that is not another runtime dependency. [SSHJ metadata](https://repo.maven.apache.org/maven2/com/hierynomus/sshj/0.41.2/sshj-0.41.2.module), [asn-one metadata](https://repo.maven.apache.org/maven2/com/hierynomus/asn-one/0.6.0/asn-one-0.6.0.module), [SLF4J compatibility](https://www.slf4j.org/faq.html#compatibility).

| Component | Diagnostic version | Selection and required handling |
| --- | --- | --- |
| `com.hierynomus:sshj` | **0.41.2** | Reviewed candidate at source `6fad160fe36605a68847ac8a527750c615b4bd04`; binary class-file major 52. No global provider registration. |
| `org.bouncycastle:bcprov-jdk18on` | **1.86** | Explicit reviewed replacement for requested 1.84; align all three BC modules. |
| `org.bouncycastle:bcpkix-jdk18on` | **1.86** | Keep the declared key-parser dependency; do not exclude it because automatic provider registration is disabled. |
| `org.bouncycastle:bcutil-jdk18on` | **1.86** | Actual transitive dependency of PKIX; the same version is required. |
| `com.hierynomus:asn-one` | **0.6.0** | SSHJ key/ASN.1 parsing dependency, source `2faafc6ebb3978f56d877dcb80e5773d6e11ffb7`; no implementation vendoring. |
| `org.slf4j:slf4j-api` | **2.0.20** | Reviewed update from SSHJ's 2.0.17 request; resolve both upstream API requests explicitly. |
| `org.slf4j:slf4j-nop` | **2.0.20** | Deliberately chosen seventh JAR. Suppress library message output; application-owned sanitized errors and real outcomes remain mandatory. |

Use exact declarations/strict constraints for the three BC coordinates and exact SLF4J alignment, then refresh the repository's normal dependency locks and checksum metadata. The existing engagement runtime is a separate inventory and must remain intact. **Seven** is the incremental SSH runtime binary count, not a measured final APK count. Verify both actual debug/release graphs and fail review on an unexplained additional provider/version. Do not add BCJSSE, `bctls`, OpenPGP, MLS, logback, log4j bindings or a global security-provider installation to make this graph work.

Select the reviewed **SLF4J 2.0.20 API/NOP pair** after the first lint run flagged the originally chosen 2.0.17 NOP version. The official 2.0.18–2.0.20 notes concern logging behavior, markers and module-license packaging; the inspected notes do not identify a security correction required by this selected API/NOP usage. The upgrade removes the need for a version-advisory exception. The pinned release source is `58d80a4fe8f2e811707fcfba4d292b5d62fc2fe9`. NOP suppresses library messages while application-owned sanitized failure state remains observable. Re-review before selecting another logger or product dependency. [SLF4J news](https://www.slf4j.org/news.html), [provider behavior](https://www.slf4j.org/manual.html).

## Why BC 1.86 rather than the published 1.84

The official **1.85** announcement (2026-07-28) lists fixes after 1.84 for ASN.1 nesting/allocation, RSA verification and password-based key decoding, including CVE-2026-13506, CVE-2026-14682, CVE-2026-12860 and CVE-2026-15055. These are reasons to review the bundled parsing/crypto dependency rather than inherit the older pin unexamined. They do not prove that this probe reaches every affected path. [BC 1.85 announcement](https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-85/).

The **1.86** announcement (2026-09-15) and pinned release notes add further hardening, including CVE-2026-17508 bounds across remaining password-based derivation paths and corrections in PKIX and other protocol modules. The notes also describe classical elliptic-curve/RSA side-channel work. Select the official release **1.86** consistently, rather than a mix of 1.84/1.86 modules or a beta. This is a conservative dependency-maintenance decision; it is not an independent cryptographic audit or a claim that all vulnerabilities are fixed. [BC 1.86 announcement](https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-86/), [pinned release notes](https://github.com/bcgit/bc-java/blob/eacda831fbe15b272933e40841d8b0827b556fae/docs/releasenotes.md).

**Reachability matters.** In inspected SSHJ 0.41.2 source, X25519 agreement and Ed25519 signatures use JCA through `SecurityUtils`. Direct BC imports occur in historical encrypted-PEM handling and the PuTTY v3 Argon2 derivation path. SSHJ also contains its own OpenSSH/key-decoding logic. Upgrading BC therefore does not automatically repair or bound every SSHJ key parser, nor does it replace Android's installed crypto implementations. This first probe restricts input to its deliberately generated, bounded **unencrypted SEC1 EC PEM** fixture key; arbitrary user-supplied encrypted-key import needs its own format/size/KDF limits and cancellation review. A network timeout cannot interrupt an unbounded CPU-bound key derivation.

## Provider policy and initial algorithm contract

Initialize `DefaultSecurityProviderConfig` before constructing any `SSHClient` or first using SSHJ crypto helpers. Its static initializer calls `SecurityUtils.setRegisterBouncyCastle(false)`. This is process-wide SSHJ state, not per-connection isolation. `SecurityUtils.setSecurityProvider(null)` resets that registration choice to automatic in the inspected implementation, so do not call it later as a reset. Do not instantiate `AndroidConfig`, register SpongyCastle, call `Security.addProvider`/`insertProviderAt`/`removeProvider`, or force Android's global provider list. Default-provider lookup means an existing platform provider can still be chosen; it does not mean BC is absent from the OS. [Pinned provider helper](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/common/SecurityUtils.java), [default configuration](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/DefaultConfig.java).

`DefaultSecurityProviderConfig` alone does **not** remove legacy algorithms inherited from `DefaultConfig`. Set explicit factory lists before any connection. The smallest initial fixture route is:

| Purpose | Exact SSHJ 0.41.2 factory | Wire choice and boundary |
| --- | --- | --- |
| Key exchange | `net.schmizz.sshj.transport.kex.ECDHNistP.Factory256()` | `ecdh-sha2-nistp256`; actual peer and JCA behavior must pass. |
| Host-key/auth signature | `com.hierynomus.sshj.key.KeyAlgorithms.ECDSASHANistp256()` | `ecdsa-sha2-nistp256`; generated fixture keys only, exact pinned trust before authentication. |
| Cipher | `com.hierynomus.sshj.transport.cipher.GcmCiphers.AES128GCM()` | `aes128-gcm@openssh.com`; do not infer that all AES modes/providers are equivalent. |
| MAC proposal | `com.hierynomus.sshj.transport.mac.Macs.HMACSHA2256()` | `hmac-sha2-256` proposal matching this GCM-only fixture; GCM authenticates through its own tag, so a separate MAC is not actually used with GCM. This does not select non-EtM MAC use for a future CTR route. |
| Compression | `net.schmizz.sshj.transport.compression.NoneCompression.Factory()` | `none`; no compression or compression-performance claim. |

Android documents `EC` key factories/generators, `ECDH` and `SHA256withECDSA` from API 11; the probe minimum is API 35. AES/GCM appears in the algorithm table from API 10, while the `Cipher.updateAAD` methods used by this route require API 19. A method/algorithm table is not proof of the selected device's complete SSH interaction. [KeyFactory](https://developer.android.com/reference/java/security/KeyFactory), [KeyPairGenerator](https://developer.android.com/reference/java/security/KeyPairGenerator), [KeyAgreement](https://developer.android.com/reference/javax/crypto/KeyAgreement), [Signature](https://developer.android.com/reference/java/security/Signature), [Cipher](https://developer.android.com/reference/javax/crypto/Cipher).

Additional modern routes can be qualified explicitly: `Curve25519SHA256.Factory()`, `KeyAlgorithms.EdDSA25519()`, `RSASHA512()`/`RSASHA256()`, AES256-GCM, or AES-CTR with SHA-2 EtM. Do not add SHA-1 signatures, DSA, group1, CBC, MD5 MACs or permissive trust as compatibility fallbacks. Android documents XDH from API 33 and Ed25519 `Signature` from API 33, but its key factory/generator tables do not establish every literal alias SSHJ uses (`X25519`, `Ed25519`). Check the exact factory/generator/agreement/signature and key-format path on the device before advertising those candidates; report failure instead of mutating global providers. This initial allowlist is a bounded diagnostic choice, not the complete product algorithm policy.

## Artifact and notice provenance

The machine-readable [incremental provenance manifest](../../qualification/probes/device-baseline/app/src/main/assets/third_party_notices/ssh-provenance.json) records each binary, POM and source-archive URL, byte length and SHA-256, published dependency edges, pinned upstream revisions and notice mapping. It also binds SSHJ/asn-one Gradle metadata and the SLF4J parent POM. The author fetched and inspected the exact artifacts; no signature verification or reproducible source-to-binary build is claimed. Artifact checksums bind bytes, not software safety.

| Runtime JAR | Bytes | SHA-256 |
| --- | ---: | --- |
| SSHJ 0.41.2 | 599969 | `48a85a0067196a7b47dfb5c1d22c4ca6ed76c84a232af9ad29f1ba7aaf5c4614` |
| bcprov-jdk18on 1.86 | 7224011 | `2af190b300cbb0b35e248ccf5f4a06b6072030aeb3da7a98ec73abe5b4cb371f` |
| bcpkix-jdk18on 1.86 | 1341621 | `8d8b41a4b149bdae8d331f059a578870954d1e3ff29f8269a9eb3ee19a7e4ef7` |
| bcutil-jdk18on 1.86 | 789287 | `1c268e15f785aafb1e4670d8d6e5f856f69475e96c162dadcd1ff9b350e9139f` |
| slf4j-api 2.0.20 | 70255 | `7e1446499b359675d8aabe6af2de86b85e72ae8a2a932c0b40d0e5ba57097439` |
| slf4j-nop 2.0.20 | 4966 | `6522080396fb339e99bae40d329a06d18d3190dc864020ac4ca943519d0fd85b` |
| asn-one 0.6.0 | 67005 | `e4f70fd92849b52240048b8ebace0c9a17d3bb7b9c882b3c7778cec3458495f5` |

The new [SSH notice directory](../../qualification/probes/device-baseline/app/src/main/assets/third_party_notices/ssh/README.txt) preserves:

- SSHJ's exact pinned Apache LICENSE and NOTICE, including the latter's derived-code terms; both are absent from its binary JAR.
- The byte-identical `META-INF/LICENSE.md` in all three BC 1.86 JARs and source archives, retained once with component mappings. The selected BC release source is `eacda831fbe15b272933e40841d8b0827b556fae`.
- Both SLF4J 2.0.20 JARs' identical embedded MIT license and their exact pinned module MIT license. The embedded copies use CRLF and the module copies use LF; normalized text matches, but both byte-exact representations are retained. API/NOP module equality was checked at source `58d80a4fe8f2e811707fcfba4d292b5d62fc2fe9`; these modules are MIT. This does not extend that license finding to other SLF4J modules.
- asn-one's exact pinned Apache LICENSE plus its runtime source copyright, transcribed from published source headers with comment delimiters removed. Its JAR/source archive has no embedded license file and the pinned repository root has no NOTICE. No test key or implementation source is copied.

Product source/distribution licensing remains open under D05/P1-S4. This record selects a permissively licensed diagnostic graph and its notice handling; it does not select a Cloudy product license or certify legal compliance for other distributions.

The primary agent's first integration compile passed Kotlin, but APK packaging stopped on duplicate BC `META-INF/LICENSE.md` resources. The selected correction uses AGP 9.4 `ResourcesPackaging.merges` for the two exact paths `META-INF/LICENSE.md` and `META-INF/LICENSE.txt`; matching texts are appended instead of discarded. This retains the byte-exact notice assets as the auditable component copies. The primary agent also reported the expected seven additional runtime JARs. A final successful build, resolved-graph audit and APK byte comparison still require their own evidence; this note does not turn the initial packaging failure into a pass. [AGP 9.4 packaging contract](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/ResourcesPackaging).

## Scoped Bouncy Castle lint decision

The second integration build reached APK assembly but lint reported `TrustAllX509TrustManager` in `org.bouncycastle:bcpkix-jdk18on:1.86`. This is a **real unsafe helper if called**: `org.bouncycastle.est.jcajce.JcaJceUtils.getTrustAllTrustManager()` returns an anonymous trust manager whose `checkServerTrusted` performs no validation. Its concrete bytecode in `JcaJceUtils$1.class` is just `return` (`b1`). The inspected caller is the EST enrollment `JsseESTServiceBuilder(String)` constructor. These APIs are unrelated to the selected SSH key-parser route. [Pinned helper](https://github.com/bcgit/bc-java/blob/eacda831fbe15b272933e40841d8b0827b556fae/pkix/src/main/java/org/bouncycastle/est/jcajce/JcaJceUtils.java), [pinned caller](https://github.com/bcgit/bc-java/blob/eacda831fbe15b272933e40841d8b0827b556fae/pkix/src/main/java/org/bouncycastle/est/jcajce/JsseESTServiceBuilder.java).

The reviewed SSHJ source and current authored probe source contain no EST reference. SSHJ's direct BC imports instead concern PEM/key decoding, provider setup and PuTTY Argon2. This is scoped source inspection, not a whole-program proof that the packaged helper can never become reachable. The existing HTTPS probe continues to use the platform trust-manager factory and normal hostname verification.

The primary agent approved one lint exception for **this issue on this coordinate/version artifact path only**: `**/org.bouncycastle/bcpkix-jdk18on/1.86/*/bcpkix-jdk18on-1.86.jar`. The exact binary must remain **1341621 bytes**, SHA-256 `8d8b41a4b149bdae8d331f059a578870954d1e3ff29f8269a9eb3ee19a7e4ef7`. The SHA-256 of the inspected anonymous class is `7d87fb71cdcc7671efe072e94b1c8c8d980dc3b661cf047ad217f44ec6e5a875`. Retain the original dependency and all notices; do not strip its classes or suppress this issue for authored source, other artifacts or all versions. The artifact path can encompass additional findings inside that exact JAR, so future lint changes require review even if the pattern still matches.

**Guard:** no EST/BCJSSE use, direct or reflective, is permitted in this diagnostic. Any BC/SSHJ dependency upgrade, new crypto/TLS dependency, changed key-import path, or new use of these APIs invalidates this reachability review and requires renewed artifact/source review before deployment. A successful lint run under this narrow exception does not certify BC TLS safety or replace positive/negative HTTPS and SSH trust tests. The final updated build and packaged notices still need independent verification.

## Required verification and stop conditions

1. Independently review this source decision, complete dependency closure and notice mappings. Verify actual locked debug/release artifacts against these pins and the pre-existing graph. A transitive change or missing artifact is a new review item.
2. Compile the exact Android source, run normal warnings-as-errors lint and meaningful trust/config/operation tests, then inspect the built APK. Compare every existing/new notice asset byte for byte, including both provenance manifests. Check ServiceLoader packaging selects the intended NOP provider and no unreviewed logging/security provider is introduced. Inspect multi-release JAR/D8 behavior rather than assuming desktop Java compatibility.
3. On the identified API-37 device, report concrete JCA algorithm/provider failures and compare the process's provider names/order before and after SSH configuration and operations. Exercise actual trust rejection before credentials, deliberate matching-key authentication, wrong-key denial, negotiated algorithms and exact finite SSH/SFTP outcomes. Never export private keys or full exception messages into public reports.
4. Retain the platform experiment's ownership, bounded resources, cancellation and process-loss constraints. Stop on an incompatible crypto/provider requirement, weak-only connection, unchecked parser/resource growth, lost trust/ownership, required global provider mutation or the experiment's effort cap. Do not turn a failed candidate into a pass by quietly changing its graph or algorithms.

**Current verification boundary:** artifact/source/notice research and independent dependency review are complete. The separate [source review](2026-10-15-ssh-probe-review.md) and [package audit](2026-10-15-ssh-probe-package.json) now record the final passing build, graph and APK checks; earlier integration failures above remain historical. No Android SSH success, parser fuzzing result, side-channel assurance, general security certification, physical-input pass or supported capability is supplied by this document.

## Source register mapping

Existing R51–R61/R71 bind SSHJ artifacts, public hooks and notices; R62 is the official BC distribution entry point. New R72–R92 bind the BC updates, dependency/provider/license sources, final SLF4J 2.0.20 pins and exact EST helper review used here. R82/R83/R86 retain the inspected earlier 2.0.17 metadata/license history; they are not the final selected pair. G100–G104 bind the Android JCA contracts, and G105 binds the exact AGP packaging DSL. All are checked 2026-10-15 in the [canonical source register](../07-reference/source-register.md); source availability is not runtime qualification.
