# Sunshine application-version compatibility overlay

This fourth overlay applies **after the accepted catalog overlay** to the staged owned `PlaybackPolicy.java`. The original authored policy, earlier overlays and their locks remain byte-for-byte unchanged. The [source lock](sources.lock.json) binds the catalog prerequisite, patch, exact staged target before/after hashes and new focused test. The [patch](finite-compatibility.patch) changes only `serverVersion` validation; it does not change native media, TLS, authorization, launch, counters or diagnostics interfaces.

## Concrete failure and official source

The first 0.1.8 playback attempt completed same-key credential qualification and one 565-byte HTTPS server-info read, then retired before a launch/native stage. It retained START, an unchanged native-start result sentinel and zero stage/frame counters. The generic START outcome did not preserve the thrown exception's specific cause, so the exact failing branch remains an inference from the retained sequence and source.

The authored unsigned-only version check rejects `7.1.431.-1`. This is the exact VERSION constant at the pinned Sunshine revision `63d35f702ee9e362e43263742981836ec0710384`: [src/nvhttp.h](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/src/nvhttp.h#L30-L40) explicitly documents that the negative fourth component identifies Sunshine. Its [server-info handler](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/src/nvhttp.cpp#L1122-L1125) emits VERSION as appversion and the separate unsigned GFE version. Earlier retained server-info evidence from this exact fixture release also contains that application version. No private response or machine inventory is copied here.

Pinned Moonlight Android [NvHTTP.getServerAppVersionQuad](https://github.com/moonlight-stream/moonlight-android/blob/b48494cb96bff23d8886c4775cc4f39a1075495d/app/src/main/java/com/limelight/nvstream/http/NvHTTP.java) parses each component with `Integer.parseInt`. The accepted native [moonlight-common-c Misc.c](https://github.com/moonlight-stream/moonlight-common-c/blob/874ac9548f1bd6f095ef2b435c42cdde460e7821/src/Misc.c#L87-L103) uses signed `strtol` in `extractVersionQuadFromString`; Connection.c passes serverInfoAppVersion through that parser. Cloudy's extra unsigned-only guard is therefore an authored compatibility restriction, rather than an upstream inability to read the sentinel.

## Narrow admitted syntax

When `optional=false` (the existing required application-version call), retain ordinary four unsigned decimal components and additionally allow exactly `-1` in the fourth component. The first three components keep their existing one-to-five-digit bounds. No other negative component, signed spelling, arbitrary suffix, whitespace, extra component or size expansion is accepted.

When `optional=true` (the existing optional GFE-version call), retain the original unsigned four-component syntax and permit null. The Sunshine sentinel is rejected for that field. A missing required application version remains rejected. The exact accepted string is passed upstream unchanged; the overlay does not rewrite the sentinel to zero or relax peer trust.

## Checks and limits

`ServerVersionCompatibilityTest` executes the real staged patched policy. Cases cover the exact official Sunshine version, the actual unsigned GFE version, unchanged ordinary versions/null behavior, rejection of the sentinel in the optional field, other negative/sign variants, component counts, length/overflow-like inputs, Unicode minus and trailing text. Existing playback policy tests are run against that same composed policy to check the unchanged lifetime/deadline/input bounds. Exact fourth-patch replay must match its locked after hash without fuzz.

These are source/policy checks, not runtime playback evidence. The integration leader owns preparation order, full Android compilation/Lint/JVM checks, package/version audit and a fresh finite device attempt. In particular, the fourth patch must run after the staged owned policy has been copied, and it must not be overwritten by a later source-copy step. Do not rebuild unchanged native libraries for this Java policy change. The earlier failure remains retained, and advancement to launch/native stages or real video/audio is pending qualification. No diagnostics framework, provider fallback, trust relaxation or capability promotion belongs to this increment.
