# Windows installation identity — permanent-file profile

**2026-10-16; host core0.1.2, scoped construction accepted. Native Windows execution is pending.** This profile replaces Windows initialization's required read-only directory flush with a separately reviewed permanent-file operation. Linux/macOS retain the [v1 directory profile](host-installation-identity.md), including its required sync operations. The [result and manifest](../../qualification/results/P4-S1/2026-10-16-host-windows-identity.md) bind the actual source and checks. No host pairing, service, job, event or interactive adapter follows from this increment.

## Claim and record

The caller supplies an existing trusted app-owned state directory. Windows init retains `os.Root`, opens that root's `.` directory for queries, and requires actual handle-based observations of local fixed NTFS before claiming anything. Failed, unknown, oversized or unsupported observations refuse the operation. No filesystem inference from a drive letter or user-supplied path is accepted.

The **same literal `installation-identity-v1` leaf** used by v1's directory becomes a v2 permanent regular file on Windows. `Root.OpenFile(O_WRONLY | O_CREATE | O_EXCL | O_SYNC)` must win exclusive creation before UUID generation. Both old directory creation and new file creation contend for that one namespace entry. There is no alternate leaf, retry after collision, replacing rename, truncation, migration or reset. Only the successful claimant generates the cryptographic UUIDv4.

The strict JSON record is bounded to512 bytes and requires exactly `identityRecordVersion: 2`, `storageProfile: "windows-ntfs-anchor-file-v1"` and a canonical lowercase UUIDv4 `installationId`. Unknown, duplicate, missing or mistyped fields, trailing values and invalid records are rejected. Installation ID remains nonsecret self-identification, never hardware identity, authentication, pairing authority, OS boot or user logon identity.

Complete record appearance is **not atomic**. A concurrent reader can see an empty/partial file and fail. Every failure after exclusive creation preserves the claimed file, even if empty or malformed. Failed write, sync, identity recheck, close or output cannot authorize regeneration. The implementation checks full write count, required `File.Sync`, opened/path identity and close. Successful Init reports only `write-through-file-flush-completed`; it does not promise power-loss durability.

## Read, version dispatch and compatibility

Windows Read first observes the anchor through `Root.Lstat`. A directory uses the strict existing v1 read-only route. A regular file must contain the strict v2 profile. There is no automatic upgrade and no v1 JSON accepted in a regular-file anchor. The initial observation is compared to the opened handle before bytes are read, followed by additional path/handle checks. Observed symlink/reparse, nonregular, oversized or changed objects fail.

Windows local identity output becomes `localIdentityVersion: 2`, with explicit `recordVersion` and `storageProfile`. Legacy reads identify `legacy-directory-v1`; new reads identify the stored Windows profile. Every Read reports `durability: "not-assessed"`: a valid record does not establish the filesystem currently containing a copied record or settle an earlier uncertain flush. Linux/macOS keep output version1 and the original directory layout. The CLI executable version is0.1.2 on all targets.

The trusted directory/no hostile same-user mutation boundary remains essential. `os.Root` confines resolution but is not a reviewed ACL installation policy or immunity to all concurrent mutations. Local fixed NTFS is necessary for new Windows init, not certification of arbitrary cloud/filter-backed paths. Complete-anchor deletion and cloned directories remain outside identity continuity. Recovery requires a future explicit reviewed operation; there is no deletion workaround in this profile.

## Dependencies and official API mapping

The selected compiler remains **Go1.27.2**. The first external module is **`golang.org/x/sys v0.49.0`**, used only by the Windows source for maintained public API wrappers. Its official origin/tag, authenticated module and go.mod sums, BSD-3-Clause license bytes and minimum Go1.26 requirement were inspected before adoption. `go.sum` and `THIRD_PARTY_NOTICES.txt` are tracked; third-party implementation is not vendored. The actual closure is checked with `go mod verify` and readonly builds. CI permits the exact download step, then returns module network access to off; this is not a general dependency/toolchain upgrade grant.

Checked2026-10-16. R314–R321 in the [source register](../07-reference/sources.json) supplement R309–R313. Host symbols do not change the Android API tracker.

| Official symbol / source | Selected use and availability | Refusal / qualification boundary |
| --- | --- | --- |
| Go1.27.2 `Root.OpenFile`, pinned Windows `openat` mapping (R314), `NtCreateFile` (R315) | Public scoped Go operation; selected implementation maps exclusive creation to `FILE_CREATE` and `O_SYNC` to `FILE_WRITE_THROUGH` | Same-leaf file/directory contention must execute on Windows. Request flags are not observed power-loss behavior. |
| `GetVolumeInformationByHandleW` (R316) | Filesystem name from retained actual directory handle; official minimum Vista/Server2008 | Non-NTFS/failed query refuses before claim. API minimum is not Cloudy's supported Windows minimum. |
| `GetFinalPathNameByHandleW`, `VOLUME_NAME_GUID` (R317) | Actual handle, fixed32768-WCHAR buffer and strict GUID-root extraction; official minimum Vista/Server2008 | Zero/oversized/malformed/unsupported results refuse before claim; no drive-letter fallback. |
| `GetDriveTypeW` (R318) | Actual returned volume root with trailing separator; require `DRIVE_FIXED`; official minimum XP/Server2003 | Remote/removable/other types refuse; native GUID-root compatibility remains a test requirement. |
| `CreateFileW` write-through semantics (R319), `File.Sync` / `FlushFileBuffers` (R313) | Go owns file creation/lifetime; inspect selected upstream mapping rather than replace it with handwritten syscalls | Complete write, flush and close are required. No stronger durability or hardware cache claim. |
| `golang.org/x/sys/windows` v0.49.0 (R320), Go module authentication (R321) | Exact maintained wrappers, official-origin and signed-checksum verification | Changed dependency/version/API requires review; no unpinned download in offline checks. |

## Review and remaining gates

The final local verification05 runs15 checks,45 named Linux race-test/subtest passes with zero skips/failures, and Windows/Darwin vet/product/test compilation. Independent review repaired an initial-observation mismatch in Read, then corrected its regression fixture to use eager `Root.Lstat` observations rather than Windows `os.Lstat`'s potentially lazy file-ID lookup. The earlier findings and verification outputs are preserved.

Native Windows eligibility, init/read, mixed-profile contention, failure preservation, reparse handling and process-exit cases must run on the exact committed source. The compiled contention fixture reproduces the earlier directory-claim algorithm; it is not an actual old0.1.1 executable race. Windows legacy-negative coverage is narrower than the retained Unix v1 suite. Native macOS behavior also remains separate. P4-S1 stays planned until its broader integration requirements are met.
