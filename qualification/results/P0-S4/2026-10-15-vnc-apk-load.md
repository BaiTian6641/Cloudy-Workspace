# VNC Android package and native-library loading

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4. **Result:** the real VNC diagnostic builds, installs and loads its three native libraries on the HP Googlebook. One closed load-only attempt is preserved. No VNC connection, authentication, framebuffer or input result is claimed. The [manifest](2026-10-15-vnc-apk-load.json) binds the exact source, package and private evidence.

## Exact package and device

Package `dev.cloudy.qualification.vnc` is **0.1.0/code1**, build reference **`90d674e2-4fef-49e2-a0e3-954fed5b2d7c`**. APK: **10,871,807 bytes**, SHA256 **`e7c903651b445c33e2946b1b7c2cbfd0d23d575e74e89f0b1ebdc7c252d6eb48`**. It uses Java17, minSDK35, target/compileSDK37 and the existing reviewed SDK/Gradle toolchain.

Actual device: **HP Googlebook14, Android17/API37, arm64-v8a**, build **`CL3B.260622.276.R2`**, incremental16560795, security patch2026-09-01. The separate app was absent before installation. Other diagnostic packages and their data were not replaced.

The package imports the unchanged [accepted adapter](2026-10-15-vnc-adapter-build.md), native build reference `vnc-adapter-x509plain-2026-10-15`, from `.cache/native-vnc/adapter/entry-02/receipt.json`:

| Library | Bytes | SHA256 |
| --- | ---: | --- |
| `libcloudy-vnc-jni.so` | 3,522,024 | `199d99a527305209c8e540c0a137958a51550c90ed523a4f970ed6d0ea941594` |
| `libssl.so` | 1,205,848 | `66288be31a05675f6a319632d6abb137eb38b07f10eea302a8b128bd6ed7d5c0` |
| `libcrypto.so` | 6,020,992 | `46c0eb8c3c601f3c9d8746e051aefe61e7a324bf152a535feac3f97472deb2d8` |

Patched LibVNC/JPEG objects are already statically embedded in the JNI library. The original shared `libvncclient.so` is absent. Actual Java runtime dependency closure is empty; JUnit/Hamcrest and Lint dependencies remain build/test-only. Seven selected native notice assets are byte-matched. Complete corresponding-source and final distribution closure remain under ADR0010.

## Implemented integration and reviewed limits

The [authored Android module](../../probes/native-vnc/android/README.md) contains a real framework Activity, native credential fields, retained application-context owner and finite session path. A process-wide lease covers JNI ownership and atomic journals. The owner serializes native operations off the main thread; cancellation is distinct from join and free. Failed joins retain ownership. Callback frames coalesce into one pending owned RGBA array; software Canvas consumption and bitmap mutation remain main-thread operations.

The actual session path parses an immutable numeric endpoint, exact leaf pin and10–600s duration from app-private configuration; credentials remain in native fields/session memory, excluded from saved state, autofill and journals. Only the accepted VeNCrypt X509Plain adapter route is available. Physical keyboard mapping uses X11/Unicode keysyms; pointer events are absolute RFB input. This does not supply an RDP-relative or3D-input claim. Input admission, upstream send return, frame conversion and unique View draws have distinct counters. No counter alone proves host delivery or display latency.

The two direct Codex workers had separate Java/UI and packaging/helper ownership. Independent review found an executor-retirement race; the worker repaired enqueue rejection, credential wiping and lease release before the accepted build. Journal admission now rejects unfinished/malformed prior records and AtomicFile residue, preserving them for explicit recovery. This is a fail-closed admission policy, not completed process-loss recovery. The parent inspected both implementations and the actual adapter ABI. Native sources and the27 prior upstream regression cases were carried forward unchanged.

## Executed build and device evidence

Final stage is `.cache/native-vnc-android/entry-03/`; package audit is `.cache/native-vnc-android/audit-01/apk-audit.json`. Actual compilation, **seven JVM tests**, **zero Android Lint findings**, and five focused preparation-helper tests pass. The audit confirms signature,16KiB native ZIP alignment, exact native bytes, one owned Activity, no other components, backup disabled and only `INTERNET`/`ACCESS_LOCAL_NETWORK` permissions. No network permission was requested for the load-only device check.

The first build compiled and ran tests but failed two Lint findings: property-value escaping and the programmatically constructed View's missing XML constructor. The preparer now leaves internal spaces literal. A class-local `ViewConstructor` suppression documents that the View requires its real retained owner and is never XML-inflated; no fake constructor was added. The next build passed and generated the actual dependency lock. That reviewed lock was incorporated into fresh entry03, which passed without lock generation. Failed and intermediate stages remain intact.

On-device attempt **`52109479-3fd7-4f1e-bb27-fd51430f7015`** records `load-passed`, `zero-connections`, `closed=true`, elapsed14ms and zero connection/frame/input counters. The visible native UI displayed that result. This exercises `System.loadLibrary` for crypto, SSL and the JNI adapter; it does not exercise `create/start/join/free` or protocol calls. Final explicit stop leaves no app process, and the journal remains byte-identical afterward.

Private device evidence is `qualification/results/private/native-vnc/2026-10-15-load-01/`. The first post-launch layout query did not include the owned window and was rejected; a fresh query found the actual diagnostic. That operator timing failure remains recorded. No screenshot, personal desktop content or secret was required for this load check.

## Next bounded server work

The official [TigerVNC Xvnc manual](https://tigervnc.org/doc/Xvnc.html) documents `SecurityTypes=X509Plain`, `X509Cert`, `X509Key`, `PlainUsers`, `PAMService`, interface binding and finite lifetime options. The Ubuntu archive candidate **1.15.0+dfsg-2build1** was downloaded, hash-verified and extracted privately; ELF dependency resolution passed. Package bytes1,239,416, SHA256 `912df371f896aa26b5ec9bd4c2e0b239253ff64e21a1638206fa4d979b6337b4`; executable bytes2,999,888, SHA256 `e59b12d25e9a9341db3b54b2da365c0964faeb52b5b720fb50674631d3fcac84`. Evidence is `.cache/native-vnc-host/preflight-01/`. No TigerVNC server was launched or globally installed by this checkpoint.

Next establish a finite isolated protected server, verify actual wrong-pin rejection before credential transmission, then real authentication/framebuffer/input and cancel/join/free. X509Plain uses PAM credentials; a VNC password file is not equivalent. Never request credentials in chat, weaken the selected security type, create a fake authentication success, or infer interoperability from the loader check. Physical input/IME/accessibility, complete lifecycle, product credential storage and CAP-VNC remain unqualified.
