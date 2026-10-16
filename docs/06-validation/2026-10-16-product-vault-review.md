# Product vault source and build review

Checked 2026-10-16. This P1-S3 increment implements app-owned credential storage and its native management screen in Console/Desktop **0.1.2/code3**. It is accepted for continued development, with actual product authorization and Keystore qualification still pending. Both installed products remain **0.1.1/code2**; the new APKs have not been installed or launched. The [18 connected storage tests](../../qualification/results/P1-S3/2026-10-16-vault-storage-tests.md) run in a separate test package and use test-only software crypto. They do not establish protected product storage or complete P1-S3.

## Implementation and boundaries

The new `:identity` library contains immutable owner/protocol/endpoint/role/account bindings, canonical host validation without DNS, bounded binary credential/envelope/profile codecs, actual Android Keystore AES-256/GCM, and independent per-app no-backup stores. It uses provider-generated IVs and authenticated binding data before decrypting. There is no plaintext persistence fallback, arbitrary alias lookup, network operation or shared credential daemon. Credential usernames/domains/passwords are encrypted; profile metadata contains the expected binding and opaque identifiers only.

Each record has a dedicated key and explicit CREATING, READY, DELETE_PENDING and DELETED journal states. AtomicFile writes are serialized off-main and read back before commitment. Missing keys are not regenerated. Explicit retries finish already approved deletion; completed tombstones can be purged only after key/ciphertext absence is verified. Both stores reject main-thread calls before waiting on their process lock.

`CloudyVaultActivity` is a non-exported FragmentActivity with Compose content, opened through Saved credentials in each product window. The existing window Activity remains a ComponentActivity. Save snapshots its immutable binding and sensitive payload before the system prompt. Check performs actual authenticated decryption/decoding within a scoped consumer; it neither displays plaintext nor connects remotely. Delete requires confirmation and fresh local authorization. Refresh, cancellation and local cleanup are explicit. Secret fields are not saved, autofill is excluded, and the secure window blocks ordinary capture. Mutable payload buffers close on all owned paths; immutable UI strings prevent any claim of complete JVM zeroization.

The actual AndroidX credential-only prompt is separate from the key's 30-second strong-biometric/device-credential policy. Only a current type-1 success callback can mint an app grant. Admission is single-use and bound to one window authority, attempt, operation, record and binding, with a 30-second use deadline. Background/recreation cancels authority; process restoration cannot recover it. Cancellation retains the process prompt slot until a terminal callback. No framework acknowledgement means subsequent prompts remain unavailable in that process; no timeout fabricates retirement. Whether normal credential presentation triggers an Activity stop on this Googlebook is still a device check.

DELETE authorizes the opaque local record ID and owning package. The supplied endpoint binding is confirmation context, not cryptographic proof about damaged ciphertext. USE always verifies authenticated binding data. Deletion currently requires a readable journal: malformed journals, orphan ciphertext and key-only orphans remain unavailable and have no blanket cleanup route. Broken ciphertext with a valid journal can be deleted. Missing-profile records cannot be used through this UI.

## Reviewed corrections

Direct Codex coworkers owned disjoint codec, storage, prompt and UI files. Independent review and leader inspection corrected:

- Callback correlation: each callback captures its originating session; stale callbacks cannot retire or authorize a successor.
- Interrupted enrollment: a profile written before revoked creation could consume capacity indefinitely. Explicit Remove unused entry now verifies absence of key, ciphertext and every journal/AtomicFile remnant before removing only metadata, serialized with enrollment by the same app worker.
- Main-thread misuse: both storage layers reject before acquiring their shared lock, so a mistaken UI call cannot wait behind disk work.

The leader also reviewed dependency changes and tests. [The dependency review](../07-reference/2026-10-16-vault-dependency-review.md) records the stable prompt dependency, actual runtime closure and limits.

An authorized OMP `zai/glm-5.3` full-code review reached its 240-second limit without final output and is **not accepted evidence**. A narrower two-file authorization/session-slot review completed in 23.427 seconds, with actual provider `zai`, model `glm-5.3`, zero tool events and no proposed defect. The leader checked its claims against the actual equality and synchronization code. This supports only the supplied pure-state review; it is not a production security verdict. Assignment hashes, raw events and leader dispositions remain ignored under `.cache/omp-workers/2026-10-16-vault-code/`. Direct GPT6.1-Sol workers continue through Codex, not OMP.

## Validation

Dependency bootstrap builds 01/02 intentionally updated locks and SHA256 metadata. Subsequent build03 passed with strict verification and no regeneration flags. After the profile lock-order correction, **build04** passed strict Kotlin/Java compilation, both product APK assemblies, both app Lint checks, identity Lint and test packaging; all three Lint reports have zero findings. The retained JVM reports pass **23 cases**: domain5, design4, identity14. The identity cases cover codec/host validation, scoped grants, replay/deadline/cancellation and session retirement. Unchanged suites may be reused by Gradle; executed versus up-to-date tasks remain in the logs.

Build04 also executed the **18 connected Android storage cases** successfully, with no failures/skips. These are real Android AtomicFile/profile/cleanup tests with injected test-only crypto, plus an alias-derivation check. The separate result gives exact device and artifact identities. No system credential prompt was started. The test package was removed; both product versions and boot ID/count were unchanged.

Raw build logs remain in `.cache/vault-entry-01/build-01/` through `build-04/`. The foundation CI workflow now builds the identity test APK and runs identity unit/Lint checks; hosted CI still has no Googlebook. The preceding design-review commit `3129011` has seven successful hosted workflows. This new increment needs its own exact-commit CI result after push.

Final repository checks pass: 32 Python AST files, Ruff, 123 Python regressions, nine terminal-bridge cases, JavaScript syntax, both token generators and project/schema validation (115 JSON, 282 Markdown, 2,093 local links, 465 sources, 48 APIs and 82 declared contrast pairs). Both candidate APK v2 signatures verify. All 660 prior dependency SHA256 entries are retained; 93 new entries bring the verification record to753. Direct worker review accepts the documentation's source/test/device boundaries. These checks do not enlarge the device result.

## Next acceptance work

Install the frozen candidate after preflight, then test the actual product prompt, AES policy/backing and enrollment/readback. Verify fresh authorized exact-byte use, cancellation and recreation, no replay after a new process, wrong binding/tamper/key-loss refusal, independent app storage and independent deletion. Preserve exact attempts and leave screen-lock credentials on the Googlebook. Device unlock or earlier RSA signing evidence cannot satisfy these checks. Backup/transfer behavior and hardware invalidation remain separate. Known-host trust, signature-protected IPC, engine authentication and the remaining P1-S3 lifecycle contracts are not completed by this increment.
