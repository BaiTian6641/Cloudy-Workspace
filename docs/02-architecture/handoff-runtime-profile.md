# Resource handoff runtime profile

Reviewed 2026-10-16 for P1-S3. The [v1 schema](../../protocol/schemas/handoff.schema.json) describes the wire shape; the implemented [domain model](../../shared/android/domain/src/main/kotlin/io/github/baitian6641/cloudy/domain/HandoffEnvelope.kt) and [Android codec](../../shared/android/platform/src/main/kotlin/io/github/baitian6641/cloudy/platform/HandoffJson.kt) add bounded parsing and semantic validation. Their [scoped build/device result](../../qualification/results/P1-S3/2026-10-16-handoff-codec.md) does not implement or qualify cross-app navigation. The authenticated IPC receiver, request ledger and receiver-owned resource registry remain required work under [ADR0004](../adr/0004-resource-handoff-and-trust.md).

## Accepted representation

| Boundary | Runtime policy |
| --- | --- |
| Document | At most8,192 input bytes; strict UTF-8; no BOM; exactly one JSON object with the nine schema members |
| Members | Reject duplicate decoded names, unknown names, missing values, null, nested values and wrong JSON token types; no string/number coercion |
| Version | Exactly numeric1, including equivalent1.0/1e0 representations; at most64 characters in its numeric token; exact decimal comparison, no floating-point rounding |
| Request ID | Exactly36 characters in the UUID hex-group form; uppercase hex accepted and normalized to lowercase; no shortened groups |
| Resource IDs | Four separate opaque IDs, each1–128 ASCII characters; first alphanumeric, then alphanumeric, dot, underscore or hyphen; never interpreted as paths, commands or credentials |
| Actions | Exactly `open-console`, `open-desktop`, `open-files`; validity here does not mean the receiver supports or authorizes that action |
| Time strings | At most40 characters; four-digit year, uppercaseT/Z, explicit seconds, optional1–9 fractional digits, `Z` or numeric minute offset; strict calendar; no leap second or `-00:00` unknown-local-offset form |
| Time normalization | Standard `OffsetDateTime`/`Instant`; offsets within Java's ±18:00 range; normalized UTC year remains0000–9999 so canonical output can be decoded again |
| Lifetime | `0 < expiresAt - createdAt <= 120 seconds`; two minutes bounds a navigation request, not a remote session or credential lifetime |
| Freshness | Expired at `now >= expiresAt`; not yet valid when created more than5seconds ahead of the supplied clock; otherwise current. This is an explicit comparison, not automatic acceptance during decode. |
| Errors | Fixed typed codes; no submitted values, parser diagnostics or nested exception causes; envelope string representation is redacted |

The timestamp profile intentionally accepts a subset of RFC3339, which also permits lowercase separators, arbitrary fractional precision, leap seconds and broader lexical offsets. The schema's `format` check alone does not enforce this runtime profile. The standard parser handles calendar arithmetic; Cloudy adds bounds and normalization policy. The future tolerance is a small admission margin, not a source of authority. Cross-app code must use a receiver-owned clock, check freshness again immediately before an effect, and use a persisted replay policy that remains safe under clock changes and process restart.

`HandoffEnvelope` has a private constructor and no public copy path around validation. The codec uses Android `JsonReader`/`JsonWriter`, with explicit `peek()` token checks because reader accessors can coerce numbers and strings. Exact `BigDecimal` comparison avoids admitting a rounded value close to1. A narrow string guard rejects literal controls and invalid escapes that the reviewed AOSP reader accepts even with leniency disabled; it does not replace the library's JSON structure/number parser. Legal JSON escapes still pass to the reader and then the resource/domain constraints.

## Receiver work still required

The decoder deliberately performs no launch, enrollment, network call, file access or remote operation. Before exposing an IPC endpoint:

1. Use explicit counterpart IPC protected by a receiver-owned signature permission; validate the actual Binder calling UID and expected signed package in the incoming transaction. A valid envelope, same network or shared source module is not caller identity.
2. Resolve all IDs through the receiving app's enrolled resources and verify their host/environment/workspace relationship. Unknown resources must fail honestly; do not invent a registry entry or connect from sender-supplied strings.
3. Enforce action/recipient compatibility, expiry and an atomic bounded request ledger before any side effect. Duplicate requests must not repeat effects across concurrent calls, restart, clock rollback or corruption. Resource lookup and ledger policy must not accidentally share credentials between apps.
4. Qualify actual same-signed counterparts, wrong/unsigned callers, delegated handles, missing sibling, version mismatch, malformed messages, stale requests, duplicate/restart behavior, cancellation and successful navigation to real registered resources.

These are remaining P1-S3/P1-S4 and suite-integration obligations. Codec tests do not close them. Ordinary Console SSH and independently installed Desktop remain independent of this handoff route.

## Official references and maintenance

Sources G190–G194 and R264–R265 in the [source register](../07-reference/sources.json) record Android JSON/time APIs, the inspected AOSP parser and the RFCs. JSON APIs are public fromAPI11; the used time APIs are public fromAPI26, covered by the existing productmin35. API49 tracks compilation/device scope and fallback. No dependency, permission or API-floor change is introduced. The platform/security owner maintains this profile at P1-S3 and refreshes it when the schema, selected parser/platform or receiver policy changes.
