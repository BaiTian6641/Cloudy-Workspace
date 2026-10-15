# Streaming pairing transport: reviewed Android build

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4. **Result:** the real bounded pairing transport and its exact upstream patch compile in the Android closure and pass their focused tests. The pairing UI/network route is not activated; this APK was not installed. Actual pairing, client-certificate TLS and playback remain unqualified. The [manifest](2026-10-15-stream-pairing-build.json) binds this source/build checkpoint separately from the [installed0.1.3 credential result](2026-10-15-stream-credential-control.md).

## Exact changed boundary

The [authored pairing adapter](../../probes/native-stream/pairing/README.md) targets Moonlight Androidv12.2, revision `b48494cb96bff23d8886c4775cc4f39a1075495d`. It performs upstream `PairingManager.pair`, then one explicit HTTPS application-list read and actual upstream parser validation. The exact unified patch adds an optional control hook to `NvHTTP`; unhooked callers retain upstream behavior. The source lock binds both upstream before/after hashes, patch bytes and the authored controller/policy/tests. Preparation and verification replay that same patch offline and reject altered inputs or extra targets. Accepted native core bytes remain unchanged.

The controlled route freezes numeric endpoint, ports and the independently supplied public server certificate/pin. It checks exact leaf identity and validity before accepting the HTTP pairing certificate, during TLS trust and hostname decisions, and before using the cached opaque key to sign. Trusted-CA status cannot bypass the pin. Explicit HTTP bootstrap/pairing phases remain part of the upstream protocol; HTTPS failures do not silently fall back to HTTP in this selected call graph.

Actual OkHttp calls are registered for cancellation through response-body consumption and close. Redirects and automatic retries are disabled. The response bound is1MiB; all timeouts derive from one immutable authorization timestamp and at most30s. The same still-live owner/provider must reject cancellation, backgrounding, stale attempts, expired authentication or locked-device use. A prior completed identity attempt cannot supply fresh authorization.

The adapter suppresses controlled URL/body/exception logging and automatic unpair cleanup. `pairingCompleted()` separately preserves host enrollment if the later HTTPS read fails. Cancelling local work does not revoke a host enrollment. Upstream's required immutable PIN String remains memory-only and cannot be deterministically wiped; returned opaque key handles also cannot be cryptographically revoked by an application cancellation flag. These limits are explicit rather than claimed away.

## Actual build and review

Local diagnostic build **0.1.4/code5**, reference **`97055d59-89af-49fb-80a5-664eca2f8095`**, is staged at `.cache/native-stream-android/pairing-01/`. APK: **24,690,370 bytes**, SHA256 **`18feedff5c1ccc3e3288a4053cce4db5e0fbb4e48d3f01b0ca82daf527efbd88`**. The installed Googlebook app remains0.1.3/code4 with its nine identity records; no device key or authorization operation ran for this build.

Actual Android compilation and **eleven JVM tests** pass: the five existing identity/prompt tests plus six new deadline/cancellation/leaf-policy tests. Real signed test certificates exercise pin/validity behavior; they are not Keystore or server evidence. Eight preparation tests cover exact replay and changed patch, source, hash and target rejection. Independent direct Codex review and the parent source review found no remaining blocker in this bounded transport contract.

Scoped Android Lint passes with **zero errors and zero owned findings**, retaining27 inactive upstream baseline errors,337 warnings and one hint. No new baseline was created. Those inactive upstream findings must be addressed when their paths become active. The audit at `.cache/native-stream-android/pairing-audit-01/apk-audit.json` confirms the same39 runtime artifacts as0.1.3, byte-identical native core, signature,16KiB ZIP alignment, selected notices and one owned Activity. The manifest still has only biometric permissions and no `INTERNET`; there is no exposed pairing control or host connection in this package.

## Next integration and evidence

Integrate the real foreground pairing action into the retained identity owner, using a fresh explicit screen-lock authorization and the existing qualified key. Add the deliberately scoped HTTP bootstrap policy and network permission, immutable profile/PIN handling, actual host pending-pairing coordination and sanitized attempt journals. Preserve prompt cancellation, last-window/background closure and the30-second policy; do not reuse a retired provider, regenerate the identity, weaken authentication or count a constructed controller as a session.

The independent [Sunshine runtime review](../../../docs/06-validation/2026-10-15-stream-host-runtime-review.md) now establishes finite KWin/PipeWire and VA-API startup probes with explicit corrections and cleanup. Web server TLS is distinct from Android client-key TLS. Next qualify real host enrollment, wrong-pin rejection, network deadline/cancel, client-certificate HTTPS and cleanup before Surface/decoder/input integration. CAP-STREAM, final distribution closure and P0-S4 remain open.
