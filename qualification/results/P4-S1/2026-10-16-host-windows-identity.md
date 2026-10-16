# HOST-IDENTITY Windows0.1.2 construction result

**2026-10-16; accepted source/local construction, native Windows and macOS execution pending.** The [profile](../../../docs/03-platform/host-windows-identity-profile.md) and [source/artifact manifest](2026-10-16-host-windows-identity.json) describe the same-anchor Windows permanent-file implementation. This is a bounded host increment parallel to Googlebook client work; no P4 stage, remote protocol or product capability is promoted.

## Actual implementation and checks

Windows init now queries the actual retained root's filesystem/volume/drive type, exclusively claims the existing anchor name as a regular file, generates UUIDv4 only after winning, writes strict v2 JSON and requires write-through request, full write, flush, identity checks and close. Partial/uncertain records remain claimed and cannot be regenerated. Read preserves strict legacy-directory dispatch and binds the first observation to the actual opened handle. Linux/macOS retain their v1 layout/output and sync requirements. Host executable version is0.1.2.

Verification05 passes **15 actual commands** using Go1.27.2: compiler/module closure, formatting, Linux vet/race tests, three product builds, Windows/Darwin vet and both packages' test-binary compilation. Raw Linux output contains **45 named passes, zero skips and zero failures**. The executable package has no unit-test file; it is not a skipped test case. Seven final artifacts and sixteen exact source hashes were independently matched. Native Windows/macOS binaries have not yet run in this result; cross-build success cannot qualify their storage operations.

The separate Linux CLI smoke check in verification03 exercised version/inspect/init/read and repeat-init refusal against owned ignored state. Later04 changes only Windows production/test source, and05 changes only its regression fixture; Linux-relevant executable inputs remain identical. Those process observations are carried forward within that boundary, not described as repeated verification05 commands. Windows-specific tests are compiled only: native storage eligibility/init/read, legacy dispatch, injected failures, concurrent new/legacy-algorithm claims, process exit and reparse cases remain awaiting execution. Injected faults and process exit are not power-loss tests.

The exact `golang.org/x/sys v0.49.0` module was checked against the public checksum database, official origin/tag and BSD-3-Clause license. Tracked go.sum and notices bind adoption. The first invalid compiler-path/cache attempts failed before adoption and remain retained. No downloaded third-party source is committed.

## Review and corrected findings

Direct Codex GPT-6.1 Sol authored source and another direct Sol independently reviewed it; the parent inspected actual source and evidence before acceptance. Review03 found that Windows Read could replace its first object observation with a later one before checking identity. Verification04 corrects that production gap. Its new regression test initially used `os.Lstat`, whose pinned Windows implementation may defer file-ID lookup. Review04 identified that mismatch; verification05 opens the retained root first and uses `Root.Lstat` for both original and current metadata. The regression retains its negative original-object/replacement-handle and positive current-object controls. Final independent review closes both findings at source level, with native test execution still pending.

All earlier builds/findings are preserved. The current CI workflow adds only the exact authenticated module download, readonly vet/test/build commands and0.1.2 version assertion; independent workflow review found no blocker. Its hosted result belongs to the subsequent exact commit, not this local receipt.

## Actual external review

OMP18.7.0 completed the bounded public design/reference packet on **`zai/glm-5.3`** in47.856seconds, normal stop/exit0, with zero tool events/calls/results and no fallback. The packet contained the new mandatory real-implementation preamble and review/security instructions, with no project implementation, private device transcript or credentials. The coordinator inspected only final answer text for disposition; raw model reasoning is not reproduced.

Useful questions about handle lifetime, bounded queries, complete write/flush/close and preserved claims informed review. Unsupported claims were rejected: a missing Root handle accessor does not preclude `Root.Open(".")` plus a public file handle; pinned Go source supplies the O_SYNC mapping; a drive-letter documentation example does not prohibit a GUID root. Arbitrary proposed buffer sizes and reopen-based flag proofs were not adopted. This was a context-only design review, not source verification or evidence of a code defect. No OMP implementation was integrated.

## Remaining limitations

Native Windows/macOS execution, installation ACL/privacy, hostile same-user mutation, arbitrary cloud/filter paths and power-loss durability remain unqualified. A successful Read reports present bytes only, never an earlier uncertain commit's durability. The mixed-version test models the old claim algorithm; it does not run an historical binary. Windows legacy-negative tests cover less than the Unix v1 suite. Recovery/reset, pairing, authorization, boot/logon identity, services, jobs, events and interactive adapters remain separate work. No device, listener, service or user state was changed by this host increment.
