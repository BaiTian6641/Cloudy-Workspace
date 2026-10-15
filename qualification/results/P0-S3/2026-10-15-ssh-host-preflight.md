# P0-S3: temporary OpenSSH host fixture preflight

**Run:** 2026-10-15, 09:20–09:22 UTC. **Verdict:** the temporary host fixture completed real loopback public-key authentication and an exact **28-byte SFTP read** after an initial client configuration failure. **P0-S3 remains in progress; CAP-SSH and CAP-TERMINAL remain pending.** No Android SSH client or SSHJ dependency was used in this run.

## Package, backend and scope

The primary agent extracted the official Ubuntu **`openssh-server 1:10.2p1-2ubuntu3.8`**, `amd64`, into an ignored project cache. The **622,420-byte** package has SHA-256 `d14910ed045bd4a2a4c9a4eeb6b3edf08d527fb4cdee5cf527fe66b8bb364c31`. The evidence author independently hashed those bytes and matched both length and SHA-256 against the locally available APT metadata for that exact version. This is a package-byte/metadata check; this report does not claim a new archive-signature verification or security audit.

The preparation record identifies **`OpenSSH_10.2p1 Ubuntu-2ubuntu3.8`** as the server. The host client log identifies **`OpenSSH_10.2p1 Ubuntu-2ubuntu3.7`** with **OpenSSL `3.5.5 27 Jan 2026`**. The primary agent separately reported OpenSSL **3.5.5** for the extracted server. These distinct client/server package revisions are preserved rather than conflated.

This was a generated-key fixture on the selected Ubuntu 26.04.1 LTS host, bound only to loopback. No system package was installed and no system service was changed. The configuration test returned **0**. Inspected configuration enabled public-key authentication, disabled password and keyboard-interactive authentication, denied root login, retained strict modes, and used `internal-sftp`. TCP forwarding and X11 forwarding were disabled. PAM was disabled for this fixture; this does not select a production server policy or establish containment of a fully authenticated SSH account.

The [sanitized manifest](2026-10-15-ssh-host-preflight-manifest.json) binds **11 selected private artifacts** and the extracted package bytes. It publishes whitelisted settings and log observations, exact file hashes and timestamps. Local usernames, addresses/ports, key material, private-key digests, raw configuration and raw logs remain private. Both attempts used the host clock; no time difference is interpreted as network latency.

## Retained attempts

| Attempt | Observation | Boundary |
| --- | --- | --- |
| **1**, 09:20:49–09:20:50 UTC | The client exited **255**, with `Host key verification failed`; no authentication or SFTP subsystem request was observed and no download existed. The server exited **0** and its listener was recorded closed. | The initial known-hosts path contained spaces and was not quoted correctly in the client invocation. This was a client harness/configuration failure, retained as evidence. It was not an Android failure or a successful trust test. |
| **2**, 09:22:00–09:22:01 UTC | After correcting the client invocation, its log reported a known host-key match, public-key authentication, SFTP subsystem request and exit status **0**. The server independently logged accepted public-key authentication. The result record reports a 28-byte download matching the sentinel. | This establishes that this exact host fixture and host client performed the read. It does not establish unknown-key approval, changed-key rejection or Android trust behavior. |

The original generated sentinel and downloaded file were independently rehashed: each was **28 bytes**, with SHA-256 **`114d3935955b73a27a64f51412c84f668f2054e96f66ad07114de44682bfc6b6`**. This verifies the actual read contents beyond command exit status. It is a finite read, with no SFTP write, exclusive-create collision, overwrite, rename, concurrent publication or durability test.

Both result records report `systemServiceChanged=false`, server exit **0**, and `listenerClosed=true`. The corresponding server logs contain termination signal **15**. The primary agent performed the starts/stops and recorded listener closure; the evidence author reviewed those frozen records without starting a listener or issuing a service command.

## Review and next qualification boundary

The evidence author independently checked both result projections, the relevant client/server log events, the bounded server settings, all selected private artifact hashes, the original/download payload hashes, and the extracted package against local APT metadata. Primary and independent integration reviews accepted the report with no actionable finding, including all eleven artifact hash/size pairs and the exact package against local APT metadata. No Android build, device operation or Git operation was performed by this evidence author.

The [SSH/SFTP candidate brief](../../../docs/03-platform/googlebook/p0-s3-ssh-engine-qualification.md) proposes SSHJ **0.41.2** for the next isolated diagnostic, with explicit dependency/provider/license review before adoption. This host preflight removes one fixture uncertainty; it supplies no SSHJ compatibility or Android connection result. A later LAN fixture must use its own explicit scope/trust configuration and cleanup record. The Android experiment still needs actual app-route and permission behavior, host trust and authentication, negotiated algorithms, cancellation, exact file operations and lifecycle evidence. PTY transport, physical terminal input/rendering, jump/direct channels and X11 isolation remain separate cases. Continuous-session service policy and product licensing remain unresolved.
