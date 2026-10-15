# Native RDP framebuffer diagnosis and first displayed desktop

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4, still in progress. **Result:** the separate diagnostic displays changing real KDE desktop pixels at **2880 × 1800** and explicitly disconnects with native thread join and instance free. Remote input, performance, sustained stability and CAP-RDP remain unqualified. This is not the product Desktop application.

The [manifest](2026-10-15-rdp-framebuffer-manifest.json) binds both source snapshots, APK identities, test reports and 193 private evidence files. The earlier [four-attempt result](2026-10-15-rdp-first-sessions.md) and [native loading record](2026-10-15-rdp-apk-load.md) remain unchanged historical evidence. All four earlier attempt objects are unchanged in the final six-attempt journal.

## Exact tested route and packages

The same HP Googlebook 14, build **CL3B.260622.276.R2 / API37 / arm64-v8a**, connects through an owned USB ADB reverse to a finite, loopback-only server on Ubuntu26.04/KDE Wayland. The server is the privately extracted official **KRdp6.4.5-0ubuntu1 / FreeRDP server3.32.1+dfsg-0ubuntu0.26.04.1**, with KWin6.5.5 and KPipeWire6.5.5. The accepted native client remains **FreeRDP3.32.2**, revision `97eff6ddfbbfaa8e42a126d362686bd001ff8bdc`, with the same four reviewed patches.

One fresh finite host fixture was used for both new attempts, with the same approved leaf pin and ephemeral credentials. Its normal temporary desktop registration grants the declared KWin interfaces; no permission bypass or host package upgrade was used. In this exact KRdp version, `--monitor 0` selects the workspace capture path described in the earlier source review. The initial client request is 1280 × 720; the server subsequently supplies its real 2880 × 1800 workspace size.

| Diagnostic | Build reference | APK bytes / SHA256 | Local checks |
| --- | --- | --- | --- |
| 0.1.1 / code2 | `5bfa1214-e326-46b7-b7ed-b5cd99aef597` | 12,770,015 / `5fda5a08987867219b559912887081465826b3d951e5d0b057ece42963515dd5` | Compiler, Android Lint and 20 JVM tests pass |
| 0.1.2 / code3 | `832bfa1b-4d82-44a6-aa04-101d9c52e7e8` | 12,672,990 / `4094946f844a3c02a584c879d1c89377264b5770f00f82d2dae66906ca889ab6` | Compiler, Android Lint and 24 JVM tests pass; signature and 16 KiB ZIP alignment pass |

The final APK's eight native ELF payloads and 21 notice/source assets are byte-identical to the accepted 0.1.0 APK. No native rebuild, decoder replacement, dependency update or repeated wrong-pin case was needed. The manifest records 40 exact non-Markdown source inputs for 0.1.1 and 42 for 0.1.2, with matching private source snapshots. These inputs are based on `cdc91633636a6520970cd1e3ae3e87068e04990e` plus the recorded changes; that base commit alone is not the new source identity.

## Diagnose before changing the bound

0.1.1 retains the failing catch site's sink and native instance, an authored operation, a bounded exception-class identifier and relevant integer dimensions. An immutable first-cause record is captured synchronously; persistence remains on the session owner. Attempt tokens reject queued notifications from a reaped attempt, including native-address reuse. Stop and pre/post-join paths retain the first cause before cleanup. Guarded owner-operation exceptions use the same first-wins record. Messages, stacks, endpoint data, credentials, certificate contents and pixels are excluded.

The fifth attempt authenticates, connects and then records:

```text
operation: resize
exceptionClass: IllegalArgumentException
elapsedMillis: 317
width: 2880
height: 1800
bpp: 32
```

Its 5,184,000 pixels exceed the original 4,096,000-pixel guard. Zero frames and input events were recorded; native thread join and instance free both completed. This converts the earlier oversized-resize hypothesis into an observed adapter failure without blaming the decoder.

## Bounded correction and official API basis

0.1.2 retains a **4096-pixel limit on each edge** and changes the Android framebuffer budget to **20 MiB for one ARGB8888 bitmap**, equivalent to 5,242,880 pixels. The measured size needs 20,736,000 nominal bytes. Positive dimension checks precede `Long` arithmetic. A freshly allocated bitmap must also pass its actual allocation-size check before it is published under the existing synchronization.

Android documents four-byte ARGB8888 storage and `Bitmap.getAllocationByteCount()` from API19 for actual allocation accounting. These are [G128: allocation API](https://developer.android.com/reference/android/graphics/Bitmap#getAllocationByteCount()) and [G129: ARGB8888 configuration](https://developer.android.com/reference/android/graphics/Bitmap.Config#ARGB_8888), tracked as API44. The 20 MiB value is Cloudy's diagnostic policy, not a platform memory guarantee. The initial requested-configuration limit remains 4,096,000 pixels because it is separate from a later server resize.

The implementation does not force-recycle a bitmap still potentially referenced by Canvas/rendering. Old/new overlap, native GDI, codec and display allocations are additional memory; Android's [bitmap memory guidance, G130](https://developer.android.com/topic/performance/graphics/manage-memory) explains the ownership concern. This correction neither admits arbitrary 4K workspaces nor establishes a total-session memory budget.

## Actual frame delivery and cleanup

The sixth attempt, using 0.1.2, accepts the same certificate, completes NLA and records the resize to 2880 × 1800. Two private screenshots were each visually inspected immediately after capture. They show the real host desktop, readable application content and changed host content as the task UI advances; their displayed update counters are 157 and 1,062. No remote input was sent.

Explicit **Disconnect** produces a final journal with **1,751 framebuffer updates**, zero input events, no `firstFailure`, `stopReason: operator`, and both native join/free flags true. The attempt lasts 74,632 ms from recorded start to reap; stop-request to reap is 505 ms. These are journal lifecycle intervals, not input-to-photon latency, FPS or sustained performance measurements. Upstream reports cancellation and a `native-failure` terminal callback during this requested stop; the operator reason, joined cleanup and host session closure establish the observed outcome.

The host records RDPGFX8.1 AVC/YUV420 negotiation and software x264 output after its VAAPI buffer-filter failure. Client logs identify `c2.qti.avc.decoder`, public output `color-format: 19`, coded dimensions 2880 × 1808 and inclusive crop `(0, 0, 2879, 1799)`. Buffer logs report stride 2880 and vertical stride 1800. Internal `android._color-format: 2135033992` is a separate field. This result does not qualify the upstream flexible-YUV-to-NV12 assumption, prove exactly which format fields FreeRDP consumed, or measure hardware utilization. Color/text/crop correctness across controlled patterns remains useful in the next input case; no visible corruption was observed in these two screenshots.

Two app-scoped memory observations are retained:

| Sample | Total PSS, KiB | Native heap PSS, KiB | Graphics PSS, KiB |
| --- | ---: | ---: | ---: |
| Connected | 500,031 | 420,764 | 40,478 |
| After explicit disconnect | 142,354 | 57,632 | 40,626 |

The native/PSS decrease supports resource release, while graphics accounting is approximately unchanged. These are two snapshots, not a peak bound, leak test or proof that every allocation was released. Codec pool-capacity log values are not equivalent to committed PSS. The observed resource cost remains part of subsequent sustained-use qualification.

A platform warning attributes allowed JNI access to `Bitmap.mNativePtr` to the framebuffer call stack. Source review found only the [documented NDK bitmap functions, G131](https://developer.android.com/ndk/reference/group/bitmap), `AndroidBitmap_getInfo`, `AndroidBitmap_lockPixels` and `AndroidBitmap_unlockPixels`, in the selected bridge, with no direct hidden-field access in Cloudy's framebuffer or that graphics path. The warning is retained; no hidden-field workaround was introduced and the exact HP platform implementation was not reverse engineered.

The server exited zero, its listener closed, and its temporary desktop registration/service cache entry and SAM files were absent after teardown. The owned ADB reverse and private device configuration were removed; fresh host credential/config and certificate-key files were deleted. The diagnostic task was closed, its process is absent, and all six closed attempts remain unchanged after closure. Baseline0.7.2 and its records were untouched.

## Review, checks and next boundary

Direct Codex GPT6.1-Sol workers authored the bounded changes and independently reviewed callback ordering, token retirement, privacy, journal compatibility and geometry/allocation checks. The leader inspected their diffs and actual evidence. Parent and author review found and fixed a stale-detail edge case: owner token references are cleared after final journal/UI updates so a later configuration error cannot inherit an earlier summary. Independent follow-up review accepted the correction. The final compile/Lint/test run follows that fix.

Six added failure-policy tests cover immutable first cause, concurrent capture, stale identity/token handling, pre-allocation owner failure and bounded serialization. Four geometry tests cover the actual size and rotation, exact budget, the next row over budget, invalid/overflow-prone dimensions and actual allocation limits. They test Cloudy's changed policy; upstream native/protocol tests and unchanged physical trust evidence are carried forward under D12.

The prior source's [complete native CI run38782707586](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38782707586) passed at `cdc9163`, alongside Repository and baseline Android CI. It establishes the new hosted build route, not hosted validation of this later 0.1.2 edit. This increment's local compiler/Lint/JVM results and real-device observations are recorded separately.

Next, use a controlled host target to qualify remote pointer/keyboard/Unicode delivery, focus-loss release, cancellation and visible color/text/crop behavior. Keep physical keyboard/IME/accessibility, arbitrary resolution changes, sustained memory/latency, broader servers and Windows separate. P0-S4 remains in progress and no capability changes to supported. Termius remains the SSH/SFTP product and UX reference; these FreeRDP diagnostics do not change that decision.

The independent evidence reviewer matched all 193 private file hashes, both APK/source audits, 20/24-test XML results, original-journal preservation, both new attempt projections and cleanup records. The leader inspected the actual memory log fields and public-NDK bridge calls. Repository Python AST/Ruff and project/schema validation pass; schemas use the existing `.venv` interpreter.
