# P0-S3 finite SSH diagnostic source review

**Reviewed:** 2026-10-15. **Outcome:** primary and independent source/dependency reviews accepted the bounded **0.5.0 / code7** diagnostic. This record qualifies the source and package for real-device experiments; it supplies no Android SSH success, product adoption or supported capability. P0-S3 remains in progress.

## Implemented contract

The native screen uses a process-owned finite worker, one tracked socket and explicit start/cancel controls. Strict, bounded app-private configuration identifies one IPv4 fixture and an independently known SHA-256 host-key fingerprint. Unknown trust closes before loading the private key or authenticating. Enrollment persists only an exact observed/reference match; another explicit Run is required. Changed keys fail closed, including rekey mismatch after authentication. Enrollment does not resume a prior connection.

The reviewed SSHJ route permits ECDH-P256, ECDSA-P256, AES-128-GCM and no compression. GCM provides its own authentication tag; the hmac-sha2-256 proposal is not a separately used MAC. The generated fixture key is bounded unencrypted SEC1 EC PEM. Android's global provider list is not modified; process provider order is checked around actual observations. Arbitrary encrypted/user-key imports remain outside this diagnostic.

After key authentication, actual SFTP must read and hash the configured sentinel, exclusively create a new attempt-specific 64 KiB file, read it back exactly, reject a second exclusive creation and confirm unchanged bytes. No overwrite, rename, deletion or automatic replay exists. Durably recorded intent precedes remote creation/write. A terminal completion requires all checks and cleanup; failures retain their sanitized stage and partial-operation facts.

The private atomic journal bounds history to eight attempts and 32 events each. Strict UTF-8, lexical depth/token checks, typed model validation, regular-file checks and bounded reads precede restoration. A new process marks unfinished work interrupted without reconnecting. Historical completion is not a fresh remote-file audit. Visible windows observe one owner; losing the last observer cancels finite work. No foreground service or continuous hidden-session behavior is claimed.

## Review findings resolved

Separate authors and reviewers inspected engine, configuration/state, persistence, runtime/UI and dependency provenance. The primary agent read the integrated source and checked the final artifacts. Corrections made before acceptance include strict lexical parsing before Android JSONObject, no-follow regular-file admission, immutable first observed identity, fail-closed rekey handling, cancellation before socket connect, a per-attempt watchdog flag, terminal-state/history validation, bounded/coalesced UI delivery and clearing stale error text on a new admitted attempt. These are source findings; the device must still demonstrate actual framework/provider/backend behavior.

The [dependency adoption record](2026-10-15-ssh-dependency-adoption.md) binds SSHJ 0.41.2, BC 1.86, SLF4J API/NOP 2.0.20 and asn-one 0.6.0. Independent review re-fetched all 24 canonical binary/source/POM/extra-metadata records and verified eight new notice assets and pinned source copies. The exact bcpkix 1.86 `TrustAllX509TrustManager` lint exception concerns a real unsafe unused EST helper, with binary/class hashes and prohibited EST/BCJSSE use. It is not a blanket TLS suppression. Any changed dependency/key path requires renewed review.

## Actual local validation

The final normal strict command was `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon --console=plain`, using the pinned [toolchain](../03-platform/googlebook/probe-toolchain.md) and dependency verification. It passed in 48 seconds, with 51 tasks (16 executed, 35 up-to-date). All **109 JVM tests in 13 suites** passed, with zero failures, errors or skips. New tests cover strict config, trust/state/history, bounded exact payload/key-envelope handling and journal lexical depth/tokens; they do not substitute for Android persistence, cryptographic parsing or live SSH tests.

The [package audit](2026-10-15-ssh-probe-package.json) records **18,545,667 bytes**, SHA-256 **`ca0411cd4dd677aa0636538c4089edf3482625aa91504f6622d0fa8260867507`**. All **20** notice assets, including two provenance manifests, match source bytes. Debug/release runtime sets both contain 30 coordinates: 18 prior binaries, seven new SSH binaries and five prior metadata-only coordinates. The seven new binary hashes match Gradle verification metadata. ServiceLoader selects the intended SLF4J NOP provider; both exact license resource paths are merged rather than discarded. The merged manifest preserves five permissions, adds a non-exported SSH screen and adds no foreground service. The audit binds every app source/resource/test file by hash.

Two earlier failures remain in ignored build logs: duplicate BC license resources stopped the first package, and the second build exposed the older SLF4J advisory plus the reviewed BC EST helper. Exact resource merging, aligned SLF4J 2.0.20 and the documented narrowly scoped exception resolved these before the final strict build. The final private log is `.cache/bootstrap/ssh-build-final.log`; raw build logs are not public evidence.

## Next physical acceptance

Commit and push this source before installation, record the exact source/APK and device/backend identity, then test unknown trust, deliberate enrollment, actual key authentication, sentinel/payload hashes, exclusive-create collision and wrong/changed keys. Keep endpoint/user names, private keys, raw logs and UI private. Independently inspect the resulting evidence and actual host file before publishing a pass. Exec/PTY, live cancellation/lifecycle, direct channels, X11, renderer/input and product integration remain distinct requirements under the [engine brief](../03-platform/googlebook/p0-s3-ssh-engine-qualification.md).

## Source delivery and subsequent device evidence

This source was committed and pushed as [dd07d94](https://github.com/BaiTian6641/Cloudy-Workspace/commit/dd07d9447007a9ce8bdea33cbc3802be8e2cfab2), with the remote branch SHA verified before installation. Both exact-source hosted workflows passed: [Repository checks](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38719808801) and [Android qualification probe](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38719808793). Three exact-path Git attributes preserve upstream CRLF, separator and trailing notice bytes without changing source/APK hashes; normal authored-file whitespace checks remain enabled.

The subsequent [device run](../../qualification/results/P0-S3/2026-10-15-ssh-sftp-probe.md) separately records five actual trust/authentication/SFTP attempts on the identified HP Googlebook and scoped Ubuntu OpenSSH fixture. Its results do not retrospectively change the source-only boundary of the local validation above.
