# Attempt03 receiver preparation failure: properties decoding

**2026-10-16; actual PREPARE failed, its cause is correlated, and the parent restored the full accepted baseline.** This is a failed qualification attempt, with no positive handoff or receiver-phase pass. [The manifest](2026-10-16-handoff-properties-failure.json) binds source/build/helper hashes and sanitized private-evidence digests. Earlier [attempt01/02 failures](2026-10-16-handoff-preparation-failures.md) and the separate [three-method diagnostic privacy pass](2026-10-16-handoff-receiver-diagnostics.md) remain preserved.

The attempt used both products at **0.1.14/code15**, with unchanged observer product APK bytes and the owning test APKs from diagnostic build01. The intended direction was Console to Desktop. Exactly one real Desktop owning `actualHandoffReceiverPhase` method started and failed: status codes1 and-2, zero passes/skips, then final instrumentation code-1. The final code closes instrumentation; it does not override the failed method.

## Correlated failure and source boundary

The actual bounded diagnostic was:

```
HANDOFF_RECEIVER_FAILED_PRESERVE_STATE:PREPARE:FILES_FIXTURE_OPEN:ARGUMENT:FILES_FIXTURE:64
```

At that exact frozen source line, `SshFilesFixture` constructs `SshHostKeyIdentity` from the private configuration's key format and fingerprint. The domain model requires a canonical `SHA256:` prefix, total length50 and a canonical32-byte digest encoded as unpadded base64. These product checks remain unchanged.

The fixture writes its private bootstrap properties with Java `Properties.store(OutputStream, String)`. Both bootstrap03 and runner07 instead read the file by splitting each line at the first equals sign. That operation retained a serialization backslash before the fingerprint's colon. The actual JSON input therefore contained51 characters rather than the model's required50. The runner's correlation guard compared the same incorrectly decoded value and accepted it.

Independent read-only inspection correlated the actual fixture readiness value, serialized property, generated payload and hash-confirmed archived receiver config. The serialized/JSON values match each other; removing the serialization escape produces the exact actual readiness fingerprint, with valid canonical base64 shape. No actual fingerprint, account, password or private filename is reproduced here. Java documents escaped special characters in stored keys/values and the stream encoding; the output must be decoded as Properties syntax rather than treated as plain key/value text. [Java SE21 Properties contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Properties.html).

This establishes a **qualification harness serialization defect for attempt03**. It does not justify weakening domain validation or establish the cause of an earlier generic failure. The error occurred during fixture construction before a run-specific qualification receipt or sealed checkpoint was created. The host recorded zero events; no receiver authentication, normal sender Open, observer continuity, registered nested listing/read, VERIFY or CLEANUP phase ran.

## Parent reconciliation and remaining limits

The finite parent reconciliation first required the exact failed-method boundary, only one confirmed owned receiver config, no other owned app/controller files, absent product processes/instrumentation, absent original host PID, a currently closed listener, zero host events and exact installed APKs. The complete protected baseline matched accepted setup after projecting out only that exact config and owned reverse. It archived and hash-checked the config before deletion, then removed only the exact reverse. After data equality was re-established, it restored only the receiver package's stopped metadata flag and rechecked product identity and the full baseline.

The actual reconciliation terminal reports `passed=true` and `fullBaselineRestored=true`. Independently inspected raw before/final/after-stopped private snapshots and the full final baseline match the recorded projection and accepted setup. Protected files, all three stores' rows/metadata/generations, replay, typed preferences, UIDs, boot/settings and pre-existing reverses are preserved; all five installed APKs were checked. No product install, data clearing, replay reset, broad deletion or automatic retry occurred. The original host exit code remains **unknown**: current PID/listener absence and the stopped flag do not reconstruct it or prove prior operation retirement.

A bounded parser/helper repair is separate work under review. Its review must establish correct decoding of the fixture producer's actual Properties syntax, strict field/value bounds and secret-safe failures, and pin the repaired parser through both consumers. A subsequent attempt needs fresh parent preflight and its own result. This record does not claim the repair ran, that PREPARE succeeded, or that normal visible Open/BAL, exact-account nested list/read, original observer chains or the reverse direction are qualified. No capability or stage is promoted.
