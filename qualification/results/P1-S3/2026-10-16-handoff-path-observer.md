# Handoff path observer: fixture compile and pure mapping evidence

2026-10-16. **Fixture-only compile and pure mapping checks passed. No listener, authentication, SFTP callback or Googlebook runtime qualification ran.** This result adds no host product capability claim and does not amend older runtime receipts.

The fixture records actual public directory/file handle paths into a separate bounded journal. The independent source review found no blocker; all three reviewed source hashes match the build receipt. The [machine-readable receipt](2026-10-16-handoff-path-observer.json) records exact source byte lengths/SHA256, the original build receipt SHA256, locked dependency-manifest SHA256 and compiler/output log hashes. Original evidence is `.cache/files-handoff-02/host-path-observer/build-01/`; its successful source-unchanged receipt binds this result. No rebuild was performed for this documentation.

The actual JDK21 `javac -Xlint:all -Werror` compilation included fixture main, helper and pure test, exited0 with empty stderr. All five existing locked dependency JAR byte lengths/hashes were verified. The pure test main exited0 with exactly `PATH_MAPPING_PASS_14`. Its fourteen assertions are:

| Input | Expected classification/outcome |
| --- | --- |
| `absolute /nested` | `nested-directory` |
| `absolute /nested/nested.txt` | `nested-file` |
| `absolute /nested/./nested.txt` | `nested-file` |
| `relative nested/nested.txt` | `other` |
| `absolute /nested.txt` | `other` |
| `absolute /nested/nested.txt.extra` | `other` |
| `absolute /nested/other.txt` | `other` |
| `absolute /` | `other` |
| `foreign rooted filesystem /nested/nested.txt` | `other` |
| `ordinary provider /nested/nested.txt` | `other` |
| `read length 1; no failure` | `completed` |
| `read length 0; no failure` | `empty` |
| `read length -1; no failure` | `eof` |
| `read length 12; IOException present` | `failed` |

These assertions construct in-memory rooted paths and map read outcomes. They execute no filesystem read, server callback, journal write, authentication or network operation. Journal overflow, disk faults and shutdown were not exercised.

## Callback meaning and privacy

Pinned Apache MINA SSHD2.20.0 commit `2221febd396cd3daafdfbf2ff982c6d0eb69a203` provides the public [SftpEventListener](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-sftp/src/main/java/org/apache/sshd/sftp/server/SftpEventListener.java), [Handle.getFile](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-sftp/src/main/java/org/apache/sshd/sftp/server/Handle.java), [SftpSubsystem](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-sftp/src/main/java/org/apache/sshd/sftp/server/SftpSubsystem.java) and [directory entry helper](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-sftp/src/main/java/org/apache/sshd/sftp/server/AbstractSftpSubsystemHelper.java). `readingEntries` observes a requested directory page; `readEntries` observes entries produced for that handle. `reading` observes a requested file read; `read` supplies actual read length/failure. A completed page/chunk does not establish client reception, full enumeration or exact UI preview.

The [RootedPath](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-common/src/main/java/org/apache/sshd/common/file/root/RootedPath.java) and [RootedFileSystem](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-common/src/main/java/org/apache/sshd/common/file/root/RootedFileSystem.java) mapping requires an absolute rooted path whose root equals the selected trusted fixture root. Only normalized `/nested` and `/nested/nested.txt` receive special classes. Other paths emit `other`; lexical classification does not replace maintained filesystem access control.

`path-events.jsonl` appends at most512 records with schema1, sequence, existing bounded session ID and finite operation/pathClass/completion literals. No raw path, filename listing, handle, file bytes, exception text or credential is emitted. Writes are forced; `path-summary.json` is atomically replaced with count/overflow/ioFailed. Both files use private0600 permissions inside the fresh0700 fixture leaf. The original events journal and summary names/count meanings remain separate. Path overflow/I/O failure makes overall fixture status unsuccessful; an absent/stale summary or inconsistent sequence/count must fail acceptance, even when a disk error prevents persisting its own status.

Later parent qualification must compile the helper alongside main, bind exact source/dependencies, distinguish preparation from fresh measured authenticated sessions, require matching nested directory/file completions and actual session closure, and correlate exact client UI preview. Neither all-session aggregate counts nor this pure result proves sender binding continuity, receiver-engine retirement or a completed handoff. See the [fixture contract](../../fixtures/ssh-session/README.md).
