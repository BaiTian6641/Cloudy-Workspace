# Finite real pairing and client-certificate HTTPS adapter

This is authored integration code against Moonlight Android v12.2 / `b48494cb96bff23d8886c4775cc4f39a1075495d`. It performs the actual upstream GameStream pairing sequence, followed by one real HTTPS `/applist` request. No Activity, network permission, host launch, native stream, decoder or playback is introduced here. No pairing/TLS/device pass is claimed.

`finite-pairing.patch` is a normal unified diff against the exact retained `app/src/main/java/com/limelight/nvstream/http/{NvHTTP,PairingManager}.java` files. `sources.lock.json` binds the patch and exact before/after source hashes. The Android preparation owner must verify and apply this overlay before compiling the authored `dev.cloudy.qualification.stream.pairing` classes. It does not alter the accepted native core or its receipt. No downloaded upstream source is tracked here.

## Parent integration API

Construct `PairingController.Profile` with a stable nonsecret profile ID, numeric address, explicit HTTP/HTTPS ports, the out-of-band public server certificate and its SHA256 leaf pin. The profile freezes certificate bytes and pin; numeric parsing uses public Android `InetAddresses`, with canonical OkHttp host form for IPv6. Ports/endpoint cannot change during an attempt. Certificate validity and exact pin are enforced even for CA-valid chains; pin matching deliberately supplies endpoint identity without a separate hostname/CA fallback.

Create one `PairingController(profile, existingQualifiedProvider, ownerGuard, authenticatedAtElapsedMillis, maximumDurationMillis)`. The timestamp must come from the actual same-attempt foreground credential callback, not a newly sampled time or saved flag. Duration must be positive and at most30,000ms, measured from that callback. The owner guard must reject stale/background/cancelled work; provider getters retain the existing identity/authentication guards. Neither controller nor patch prompts, regenerates keys, widens key policy, forces a provider or retries authentication.

Call `run(char[] pin)` on the serialized worker after obtaining fresh credential approval. PIN must contain four decimal digits. The controller copies/wipes its char array; pinned upstream `pair(..., String)` requires an immutable in-memory String that cannot be deterministically wiped. Keep it memory-only, wipe the caller array, do not journal/log it and do not claim all secret copies are erased. The parent controls PIN submission through Sunshine's exact pending-pairing ID API, concurrently with this blocking worker. The parent must configure a narrowly selected cleartext bootstrap/pairing exception and targetSDK37 network permission separately; this increment changes neither manifest nor network-security policy.

`cancel()` cancels the actual registered OkHttp call. `run()` owns close/retirement and must return before replacement; cancellation request is not completion. Calls stay registered until the response body is fully consumed/closed. Responses are bounded to1MiB, stored only in temporary process memory, and consumed into an in-memory ResponseBody for the upstream parser. Call timeout uses the remaining immutable overall deadline; connect/write are additionally capped at5s and read cannot exceed the overall remaining time, including the initial PIN wait. Redirects and connection-failure retries are disabled. No worker/IO callback can extend the deadline.

`Result` reports completion only after real pairing and the explicit HTTPS read/parse. `pairingCompleted()` separately preserves actual pairing completion if the later TLS read fails; cancellation does not revoke server enrollment. `stage()`/`failureAt()` expose only authored enums. The thrown failure is sanitized and carries no upstream Throwable, endpoint query, response, key, certificate body or PIN. The parent must not infer delivery from stage names or treat a failed final read as proof the host never enrolled the client.

## Exact changed upstream behavior

The old NvHTTP constructor delegates to the new constructor with a null hook. Without the hook, original transport/trust/UID/logging and automatic unpair behavior are retained. The diagnostic always supplies the strict hook, which:

- Selects explicit HTTP `/serverinfo` bootstrap without silently retrying a failed HTTPS identity check over HTTP.
- Uses actual upstream HTTP `/pair` challenge phases, with unconditional certificate match before accepting the returned server certificate. The final pairing challenge uses upstream HTTPS `/pair?phrase=pairchallenge`.
- Applies strict leaf identity in both TLS trust and hostname decisions; CA-valid different leaves are rejected.
- Guards the current owner before the cached opaque private key signs the client secret and before every HTTP/HTTPS request.
- Suppresses automatic upstream `unpair()` cleanup for controlled failures. Pending host-pair cancellation and established-client revocation require separate explicit parent-owned operations.
- Suppresses controlled verbose URL/body/exception traces and the app-list parser's incomplete peer-name warning. The later HTTPS `/applist` response is parsed with the actual upstream status/parser logic through a quiet overload.

The inherited HTTP pairing protocol is not encrypted end to end; it exchanges public certificates and PIN-derived protected challenges. The out-of-band server pin prevents silent certificate substitution, while real pairing verifies the cryptographic proofs. The isolated fixture must remain on its reviewed private route. No peer list/body is returned to UI.

## Checks and limits

A non-Gradle compiler check compiles the exact patched upstream files against the accepted local Android/Java closure, followed by strict authored Java17 compilation with `-Xlint:all,-classfile -Werror`. Six focused JVM tests cover immutable deadline/callback time, cancel-before-registration, cancellation while a body-owning registration remains active, unrelated retirement, exact leaf pin including caller mutation, and expired/future certificate rejection. Certificate fixtures use real test-only RSA signatures; they are not AndroidKeyStore or server integration evidence.

Private compiler/test evidence is `.cache/native-stream-pairing-check/`. Parent-owned full Android build/Lint, preparation integrity and package review remain required. Actual wrong-pin rejection, network cancel/deadline, real same-key client TLS, host enrollment/cleanup and PIN timing remain unqualified. The30-second fast path does not implement mid-pairing reauthentication or arbitrary PIN delay. No automatic unpair, host mutation, listener, playback or CAP-STREAM acceptance is supplied.
