# Native RDP diagnostic APK and Googlebook loading

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4, still in progress. **Verdict:** the real FreeRDP diagnostic builds, packages, installs and loads on the identified Googlebook. The actual patched native queue passes its bounded device test. **No RDP connection, certificate callback, authenticated desktop, decoder output, input or latency has been qualified.** CAP-RDP remains pending.

## Exact target and artifact

| Item | Observed value |
| --- | --- |
| Device | HP Googlebook 14; arm64-v8a; Android API37 |
| Build | CL3B.260622.276.R2; incremental16560795; properties read again in this run |
| Application | `dev.cloudy.qualification.rdp`, version0.1.0/code1; separate from the unchanged baseline0.7.2/code11 |
| Build reference | `2b12a3d1-74aa-47ee-8238-d606181adbd5` |
| APK | 40,729,286 bytes; SHA256 `35fb24931dd5e3acdbd5a20898dbdb7c64de276743624c4b4d7469dba0d89371` |
| Native source | FreeRDP3.32.2, revision `97eff6ddfbbfaa8e42a126d362686bd001ff8bdc`, plus four locked patches |
| Native tools | Stable NDK29.0.14206865/Clang21.0.0; CMake4.1.2/Ninja1.12.1; native API29/arm64 |
| APK tools | Existing pinned AGP9.4.0/Gradle9.6.0/Kotlin2.2.10; Oracle JDK21.0.9; compile/target37, minimum35, Build Tools36.0.0 |
| Device outcome | Native version3.32.2, ready state, zero attempts, zero frame updates, zero input events |

The [manifest](2026-10-15-rdp-apk-load-manifest.json) binds the authored source snapshot, APK and private evidence. Raw logs, journal, screenshot, package/activity observations and native receipts remain in `qualification/results/private/native-rdp-probe/2026-10-15/`. The screenshot was visually inspected immediately and remains private because the desktop contains other application windows. Device serials, host identities, credentials and downloaded implementations are not published.

## Implemented boundary

The [diagnostic source and recipe](../../probes/native-rdp/README.md) provide an independently installed framework Activity, real native GDI/Bitmap view, explicit connect/cancel/disconnect, private operator configuration, endpoint-bound SHA256 leaf pin, required NLA, bounded physical-key/pointer and committed-text paths, and a finite single-owner session controller. An atomic journal records intent before native allocation, retains at most16 attempts and distinguishes actual thread start/join from instance freeing. Activity stop/destroy requests shutdown; focus loss releases held input. A recreated owner waits for the previous owner and cannot rewrite its journal while cleanup is pending.

The four patches bound OpenSSL build jobs, reuse maintained WinPR queue primitives with bounded ownership, expose explicit native join/external-certificate ownership, and reject unmapped scan codes. The authored Java wrapper preserves the required pinned JNI names and callbacks without copying the upstream profile/database/UI. No synthetic session or framebuffer substitutes for native output.

The external certificate policy has no ignore/TOFU/fingerprint-shortcut fallback. It checks configured host/port, ordinary direct-certificate flags, first-leaf validity and DER SHA256. Runtime callbacks still need the real server cases. Physical keys use the pinned WinPR extended-table bit where required; the deliberately bounded US mapping does not establish arbitrary layouts, native remote IME or physical-key acceptance. The separate native composer preserves submitted text because asynchronous delivery has no acknowledgment to its click handler. Cursor state and focus gating are implemented but require an active remote session to qualify.

## Executed checks

- All **2,975 original regular source files** match the pinned archive plus four patches. Actual native build04/05/06 completed with exit0 for ownership, certificate and key changes. The original build03 evidence remains frozen.
- Kotlin/Java compilation, Android Lint and **14 JVM tests** pass: seven actual key-map boundary tests and seven certificate-policy tests using a real public fixture certificate and deterministic dates. These tests are not network or device certificate evidence.
- The APK contains exactly **eight** intended native libraries. Static ELF checks require AArch64, 16KiB-compatible load segments, expected SONAMEs, approved dependency closure and no RPATH/RUNPATH. Actual APK ZIP alignment and signature verification pass. All **21** native/JVM notice, provenance and corresponding-source assets match their prepared bytes.
- The importer binds current source/patch hashes, actual CMake source directories, required exported JNI symbols and all eight binary hashes to the accepted build receipt. Four actual negative cases—changed library bytes, changed notice bytes, extra native library and symlink substitution—reject the changed inputs. Exact byte restoration passes. This is local build provenance/integrity checking, not an independently signed build attestation.
- The actual patched `android_event.c` test executable ran on the Googlebook and exited0:1024-event capacity, FIFO/wake behavior, failed-enqueue ownership,17 pending-event frees, real disconnect consumption and20,000 events from four concurrent producers. It links actual WinPR; it does not exercise JNI thread join, certificates, a protocol session or Android UI input. Its fresh temporary device directory was removed.
- The APK installed and launched. Layout and visual inspection show the real native version3.32.2 and ready state beneath the native desktop caption. The app-private journal has zero attempts. Closing the idle Activity through the observed system caption leaves no resumed RDP Activity. No endpoint/configuration or local-network permission grant was provisioned to this separate app.

The first Gradle attempt rejected a Provider passed to the old SourceSet API. The corrected module uses the documented variant source-directory API (G114), with a read-only pre-build integrity gate. The first complete build compiled/tests passed but lint reported backup-rule, icon, custom-view constructor and ChromeOS-ABI findings. Explicit Android12+ data-extraction exclusions and an icon resolve the first two. Declaration-local exceptions document the intentionally injected programmatic view owner and this arm64-only diagnostic; they do not claim ChromeOS/x86 support. Final build03 passes all54 tasks; asset-only build04 passes with unchanged compiler/JVM tasks reused.

The delivery helper records receipts only after an actual successful native target. Its accepted existing-graph run produces the same receipt bytes used by this APK. Review corrected fresh-build preflight and descendant cleanup: a real fresh outer configure passes without an inner cache, and a real TERM-ignoring child is absent after group cleanup. This is targeted helper evidence, not a second full fresh native build.

The first Android CLI run used an unsupported slash-form Activity selector and failed component selection. Retrying with the sole APK launcher selected installed/launched successfully. Both command outcomes are retained; the failure is not an application crash.

## Stage review and limits

Direct GPT6.1-Sol workers reviewed separate native/JNI, controller, key/trust/UI and packaging work. The parent inspected actual changes and ran builds, validation, artifact checks and device operations. Reviews found and corrected extended-key table selection, journal failures interrupting cleanup, a terminal-callback journaling race, stale initial binary-import acceptance, composer data loss, cursor-state handling and pointer reactivation after focus loss. A follow-up controller review found no remaining blocker in its bounded ownership scope; actual connection/lifecycle evidence is still required.

Native notices include Apache/MPL/cJSON/uriparser/embedded WinPR attribution; exact selected Kotlin2.2.10 and annotations13.0 notices accompany them. The actual modified bridge source and headers are supplied inside the APK assets with the recipe and MPL text. This diagnostic packaging does not decide Cloudy's product distribution license, streaming codec obligations or native VNC distribution gate. See [Mozilla's MPL text](https://www.mozilla.org/en-US/MPL/2.0/) and [P1-S4](../../../docs/05-delivery/phases/p1-foundation.md).

The host preparation downloaded and hash-verified official KRdp6.4.5-0ubuntu1 and `libfreerdp-server3-3`3.32.1+dfsg-0ubuntu0.26.04.1, extracting them only into a private cache. Loader relocation and offscreen help checks pass. An inherited Qt-platform help attempt timed out and was terminated; its initial stdout/stderr were not retained. No host installation, package upgrade, RDP listener, capture permission, service or ADB reverse was started. Existing Wayland capture and actual NLA compatibility are unqualified. The exact package hashes and loader/help records are private evidence; this finite candidate is not a production-server security certification.

Hosted Android CI currently builds the existing baseline module. This new native APK's compiler/lint/test evidence is **local**, and adding native-APK CI remains delivery work. Prior commit772d169 has successful Repository and Android baseline checks; those runs do not cover this new source increment.

Next: exercise a finite loopback-only existing-session KDE fixture with deliberate credentials/certificate and ADB reverse; prove wrong-pin rejection, NLA, changing pixels, physical/committed input, focus release, in-flight cancel and actual join/cleanup. Record negotiated decoder/output layout and measured performance separately. The MediaCodec implementation remains the experimental CPU YUV/GDI/Bitmap path. VNC, high-performance streaming, X11 and USB retain their independent gates.
