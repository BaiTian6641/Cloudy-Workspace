# Explicit paired HTTPS application catalog

This third authored Java overlay composes **after the accepted pairing and playback overlays**, against Moonlight Android revision `b48494cb96bff23d8886c4775cc4f39a1075495d`. The [source lock](sources.lock.json) binds exact prerequisite patch, before/after target hashes, patch bytes and new authored catalog source/tests. It does not change the native core or the frozen earlier overlays. The [patch](finite-catalog.patch) changes only controlled quiet application-list parsing: whitespace before the first App does not dereference an empty list, and malformed IDs are rejected with a fixed error before upstream's String-ID setter can log raw peer text. Ordinary nonquiet parsing remains unchanged.

The real catalog controller uses the existing controlled `NvHTTP` constructor, `getAppListRaw()` and `cloudyGetAppListByReader()`. It admits only GET **HTTPS `/applist`**, on the immutable approved numeric peer and explicit positive HTTPS port. Zero HTTPS ports are invalid because upstream would discover that port through HTTP server information. There is no server-info bootstrap, HTTP fallback, redirect, connection retry, PIN, pairing, automatic unpair, launch or resume in this operation. The strict pinned leaf check applies even to otherwise CA-valid server certificates. Actual OkHttp calls remain registered and cancellable through the complete bounded response body; the response is capped at 1 MiB.

## Private public-trust profile and actual selection

Provision `no_backup/catalog-config.json` with schema 1 and exactly these eight fields:

- `schema`, `profileId`, `numericAddress`;
- `httpPort`, `httpsPort`, `rtspPort`;
- `certificateSha256`, `certificateDerBase64`.

The profile is bounded to 32 KiB. It requires numeric unicast nonloopback IPv4, distinct positive ports, exact SHA-256 public certificate pin and canonical Base64 for one exact DER certificate of at most 16 KiB. No application ID belongs in this file. This syntax check does not establish LAN reachability or approve a peer; the parent controls the private host/route provisioning. Credentials and PINs are not part of the profile.

An explicit native button requires local-network permission, an existing READY app-owned identity and a fresh DEVICE_CREDENTIAL prompt with actual authentication type 1. Qualification, obtaining the opaque provider and the catalog read use the same live attempt and original callback's maximum 30-second window. A returned provider is never reused after retirement. The shared process owner serializes catalog work with identity, pairing, reconciliation and playback.

The actual upstream parser supplies application IDs. Catalog policy accepts at most 128 entries with positive distinct IDs, bounded titles and native text display stripped of control/format characters. Catalog titles and selection remain in process memory only. Every new read clears the old peer/list/selection before admission; failure or an empty list cannot leave a prior ID launchable. Native selection captures the catalog generation, and stale callbacks cannot select from a replacement peer. Selecting a returned entry creates the existing immutable playback profile. Preview has no fallback to an old `playback-config.json` or guessed application index and requires a **separate fresh credential authorization**. Catalog reading neither starts the native engine nor consumes or resets its one-run-per-process allowance.

## Ownership and durable evidence

`catalog-journal` joins pairing/playback admission and exact-byte previous-process reconciliation. All three network actions scan all three directories before work. An intentional bounded pending reservation is written and read back before network work. Malformed, unfinished or evidence-unavailable records block every network lane. Reconciliation preserves original records, acknowledges only exact well-formed previous-process bytes, records host state UNKNOWN and never replays work or claims host shutdown.

The controller is published before running and cancellation is checked again afterward. Cancel, real backgrounding, original authorization deadline and owner clearing cancel the actual HTTP call as well as the identity attempt. Busy ownership lasts through actual worker/prompt retirement and successful terminal evidence write/readback. Failed catalog evidence retains ownership; operator cancellation can retry the terminal write. If the owner is cleared without terminal evidence, the process lease is retained rather than pretending success. Process death leaves unfinished durable evidence for explicit reconciliation or manual repair.

The catalog journal records sanitized stages, actual HTTP call/body-byte counters, completion and application count. It does not persist titles, XML, URLs, endpoints, certificate bodies, keys or PINs, and does not invent zero frame/connection observations. A later playback journal records the selected actual positive application ID and catalog generation, without catalog titles or body. Earlier identity/native/pairing evidence is not migrated or rewritten.

## Bounded checks and limits

Focused JVM tests call the **real composed upstream parser**, with kxml2 restricted to the test dependency closure. Cases cover real non-positional IDs, leading/empty whitespace, no invented Desktop entry, malformed-ID sanitized rejection and protocol status rejection. Pure policy tests cover stale selection generations, title bounds/control stripping, positive/distinct/count limits, original authorization expiry and cancellation reaching the registered call. These do not qualify AndroidKeyStore, a system prompt, TLS or a real host.

Source compile/test results are reported to the integration leader; the parent owns full preparation, Android compilation/Lint, package review, installation and actual catalog qualification. This source increment makes no device, catalog, playback, video/audio, latency, performance or CAP-STREAM pass claim. The next finite observation is a real paired pinned HTTPS catalog with actual IDs, deliberate selection and terminal evidence, followed by separately authorized playback only after review. Existing release/source/notices and inactive Lint baseline gates remain in force.
