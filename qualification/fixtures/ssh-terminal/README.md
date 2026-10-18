# Local password SSH terminal fixture

**Scope:** construction fixture for the Console terminal adapter. It is a real Apache MINA SSHD
2.20.0 loopback server with password authentication and a real local interactive shell, used to
exercise the product's retained transport, channel, renderer output and explicit close on a
device. It is **not** an OpenSSH interoperability, PTY/resize, privilege-isolation or product
acceptance result.

The MINA process factory starts util-linux `script --return --quiet --flush --command
'/bin/sh -i' /dev/null`. `script` supplies a **real kernel PTY** and its CR/Enter line discipline;
the outer MINA process connection still uses pipes. SSH window-change requests are **not**
propagated to this nested PTY, so this fixture does not qualify initial geometry, resizing or
job-control behavior. Use the separate [ssh-pty fixture](../ssh-pty/README.md) for real OpenSSH.
The shell runs as the invoking host user; this is not a sandbox.

## Prerequisites

- Linux host with JDK 21 (`javac`, `java`) and `python3` for the standard-library pin check.
- `/usr/bin/script` from util-linux and `/bin/sh`; parent observed util-linux 2.41.3. No shell
  transcript file is created (`/dev/null`); the utility's [upstream implementation](https://github.com/util-linux/util-linux/blob/v2.41.3/term-utils/script.c)
  owns the real PTY and command lifetime.
- The four reviewed MINA/SLF4J jars in `.cache/research-sshd-2.20.0/`: `sshd-core-2.20.0.jar`,
  `sshd-common-2.20.0.jar`, `slf4j-api-2.0.20.jar`, `slf4j-nop-2.0.20.jar`, with sizes and
  SHA-256 values pinned by the existing
  [session fixture dependency record](../ssh-session/dependencies.json). The launcher never
  downloads or substitutes a jar.

## Run

```bash
qualification/fixtures/ssh-terminal/bring-up.sh
```

The launcher:

1. Verifies each of the four selected jars is a regular (non-symlink) file whose exact byte size
   and SHA-256 match `dependencies.json`, before compiling or launching anything. A missing
   record, absent jar, non-regular file, size mismatch or hash mismatch aborts with a fixed
   basename-only error. No credential is printed.
2. Compiles the fixture with `javac -Xlint:all -Werror` into a fresh `mktemp` directory under the
   ignored `.cache/ssh-terminal-fixture/` root.
3. Creates a fresh `mktemp` run directory under the ignored
   `qualification/results/private/ssh-terminal-01/` root and sets both directories to 0700. It
   never deletes or reuses earlier build/run directories, so prior evidence is preserved.
4. Starts the Java fixture, which revalidates that the run directory is a real, non-symlink,
   empty directory with exactly 0700 permissions. Any other existing content or permissions are
   rejected before credentials are generated or written.

The Java fixture writes `fixture.properties` with mode 0600 at creation, before the password is
stored, and prints two non-secret lines:

```text
fixture.properties=/abs/path/to/run/fixture.properties
READY port=NNNNN fingerprint=SHA256:...
```

The path is printed rather than assumed because the workspace path may contain spaces; use the
remaining text after the `=` prefix. `fixture.properties` contains generated keys `schema`, `host`,
`port`, `user`, `password` and `fingerprint`; it never contains the host private key. The password
and private key are never printed. Treat the properties file as private test material and never
publish or reuse it as a real credential.

## Real operation and bounds

- The server uses `SshServer.setUpDefaultServer()` from the pinned Apache MINA SSHD 2.20.0 and
  binds `127.0.0.1` with port 0 (kernel-assigned random loopback port). After `start()` it checks
  that every bound address is a loopback address and that the bound port is nonzero; otherwise it
  fails before printing `READY`. It never binds a public address.
- Each run generates a fresh in-memory EC P-256 host key and a fresh 24-byte random Base64url
  password. The password authenticator accepts only the current invoking user name plus that
  password, compared in constant time. No host account, system service, configuration file,
  namespace or private key file is created.
- The shell factory is the maintained `InteractiveProcessShellFactory` with an explicit
  `/usr/bin/script` argv, wrapping the real `/bin/sh -i` child. It uses a real kernel PTY for
  input line discipline, not fake shell responses. MINA does not propagate SSH geometry into it.
- `RejectAllForwardingFilter` rejects forwarding, subsystem factories are empty, and no exec
  command factory is configured. The shell channel is the only interactive service.
- The fixture waits at most 600 seconds after starting. It also polls for a file named `stop`
  inside the run directory every 200 ms. On deadline, stop file, main-thread interruption or JVM
  shutdown it closes the actual `SshServer` with `stop(true)`, which awaits MINA's configured
  immediate-stop timeout and performs the real acceptor/session close, then prints
  `STOP reason=<deadline|stop-file|interrupted|shutdown|failed|finally> server=<closed|not-started|close-failed>`.
  A close-confirmation timeout is reported as `close-failed`. The `stop` file is read-only
  evidence and is not deleted.
- Stdin is not a control channel; EOF on the parent's stdin does not stop the fixture.
- Normal deadline and stop-file retirement exit 0 only when the actual `SshServer.stop(true)`
  reports success; a reported close failure exits 2. A main-thread `InterruptedException` exits
  130. Startup/configuration/lifecycle failure prints `FIXTURE_FAILED` and exits 1.
  `SIGTERM`/`SIGINT` run the JVM shutdown hook, which performs the real close; the shell's exit
  status then reflects the signal. `SIGKILL` cannot run the hook or print `STOP`, so a hard-killed
  process is cleanup uncertainty, not a successful close.
- There is no permanent parent-process killer, `pkill`, or arbitrary descendant sweep. The parent
  owns any forced process/listener verification if a run is hard-killed.

## Cleanup and evidence

Build and run directories are intentionally left in place. The launcher and fixture delete
nothing outside their own newly created `mktemp` directories and never rewrite an existing run
directory. Delete a particular private run directory only as a deliberate parent action after
review. The sibling `ssh-session` and `ssh-pty` fixtures, shared caches and unrelated results are
not touched.

## Reproduce the real Console workflow

Build the Console debug and instrumentation APKs, start the fixture above, then use its printed
private properties path with an explicitly owned target:

```bash
python3 tools/run_console_terminal_smoke.py \
  --fixture qualification/results/private/ssh-terminal-01/RUN/fixture.properties \
  --serial emulator-5580
```

Replace `RUN` with the actual freshly created run directory; no credential belongs in the command.
The controller installs the actual APKs and reserves its reverse port with `--no-rebind`, refusing
an existing mapping. Each workflow gets a fresh0700 host evidence directory; its remote shell writes
the0600 `remote-effect.proof` there with `umask 077` and noclobber, not in a world-writable temporary
directory. The path is POSIX-quoted and is not a credential. The collector opens owned regular files
with no symlink following and bounded reads; oversized/public-mode/symlink/FIFO evidence is refused.
Only an independent exact-byte host proof authorizes atomic publication of the app-private witness.
The test starts at real Home search/selection/Connect and authenticates without a test refresh. It
requires real command effect, protected pixels, explicit exit and credential-draft recreation without
replay. Restoration accounts only for the owned row's exact marker mutation and restores the complete
logical profile/trust baseline; unrelated Activities and data are not cleanup targets.

Receipts, host proofs, captures, images and hashes remain under
`qualification/results/private/console-terminal-resume/`. SIGINT/SIGTERM or the120s observation
deadline requests Android-side cancellation and gives it45s to unwind; terminating the adb client alone
never proves Android retirement. The controller checks the instrumentation terminal result before
removing inputs. It verifies the exact owned reverse destination before release, verifies app-private
input/witness removal, and records `cleanupErrors`, `uncertainPublications` and `cleanupConfirmed`.
Unconfirmed Android retirement preserves its dependencies and cannot pass. SIGKILL cannot run cleanup.
Artifact exports are bounded and validated as PNG/JSON before a local evidence flag is set.
The controller never stops a host listener or resets app data. Stop the owned fixture separately with
its run-local `stop` file. A failed receipt requires inspection, not blind replay. Emulator, host-only,
injected-pointer/key, particular system-IME and actual Googlebook results retain distinct scopes.

### Extended interaction check

Add `--interactions` to the controller command to exercise the actual local menu keyboard path,
rich-clip plain-text preview/Cancel, a20,000-byte paste, real Find/count and Copy. It creates a fresh
private host capture directory and correlated UUID, runs the maintained `exercise.py` through the
real terminal, and publishes a witness only after independently comparing actual captured bytes.
The expected capture contains only the large paste: menu keys and Cancel must contribute no bytes.
This is an opt-in qualification procedure, not an assertion that every current candidate passes.

`exercise.py` was adopted from the preserved real P0 terminal exercise, not a new fake server.
It uses actual POSIX termios/curses/stdin, requires all three descriptors to be the same PTY and
an existing owned0700 output directory, and emits exclusive0600 bounded JSON. Capture allows
at most64KiB; its262KiB record bound accommodates hexadecimal evidence. A supplied `--run` must
be a canonical UUID4 and appears in the readiness marker, preventing an older visible marker
from qualifying a new attempt. Use only synthetic input; raw captured bytes stay private.
The input-only flush and terminal restoration do not claim to prevent later bytes becoming shell input.

Parent helper checks used a real local kernel PTY:65536 bytes reached the byte limit with exact
content/hash and restoration; a separate UUID-correlated17,007-byte UTF-8 sample matched at its
finite timeout. Those are helper checks, not SSH, Android or physical-input qualification.


## Boundary

The fixture has no SFTP subsystem, no forwarding, no exec command and no virtual filesystem. It
is not an OpenSSH compatibility, sandbox, resize or remote-privilege test, and it does not verify
Android physical input, IME or TalkBack behavior. See the terminal construction result for what
was actually exercised on a device.

## Parent qualification — 2026-10-17

The parent reviewed the DeepSeek Flash proposal, corrected the selected SLF4J nop jar version
and executable argv, and exercised the real launcher. All four pins passed and
`javac -Xlint:all -Werror` compiled the fixture. The parent then added the real util-linux PTY
wrapper and serialized the actual close/result publication: a concurrent shutdown-hook caller
cannot report success before `server.stop(true)` finishes.

Observed toolchain: JDK/javac **21.0.9**, util-linux **2.41.3**, Apache MINA SSHD **2.20.0**,
SLF4J **2.0.20**. A system OpenSSH **10.2p1 Ubuntu-2ubuntu3.8** client, with the fixture's exact
host-key fingerprint verified before password use, ran:

```sh
[ -t 0 ] && [ -t 1 ] && printf 'CLOUDY_FIXTURE_KERNEL_PTY_OK\n'; exit $?
```

The command was terminated with a real CR/Enter. Exit status was **0** and the marker was
observed; stdin and stdout were required to be terminals. This qualifies the fixture's actual
shell/line-discipline operation, not an OpenSSH server, SSH resize or product workflow.
The final local receipt is `qualification/results/private/ssh-terminal-01/run-7v9h3Q/host-final.json`
and binds Java source SHA-256 `703413e454a490044283d01734dca010e2fdab82d43d4a178380d47fafaf9cf4`.
A separate wrong-password attempt exited **255**, reported authentication refusal and produced
no shell output. The earlier `run-3DX2Fj/host-final.json` remains historical evidence.
Stop-file and deadline runs retired with exit 0; hard-kill and forced close-timeout paths were
not tested.

The attempted separate OpenSSH namespace fixture failed to write `/proc/self/uid_map`
(`Operation not permitted`); no host security setting or retained X11 fixture was changed.
That failure does not invalidate or broaden the earlier M1 OpenSSH evidence.
