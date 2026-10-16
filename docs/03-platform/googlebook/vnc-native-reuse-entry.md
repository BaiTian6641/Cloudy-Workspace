# Explicit VNC retained-native reuse

The VNC module can verify a previously compiled native closure after the reviewed Java owner cleanup repair without rebuilding JNI or upstream libraries. This is an explicit, source-bound local construction route. The [2026-10-16 result](../../../qualification/results/P1-S4/2026-10-16-vnc-native-reuse.md) records an actual helper check and actual canonical module `:vnc:verifyNative` task. It does not register the module in the root graph, embed it in Desktop or qualify an Android runtime session.

## Default and explicit modes

`tools/prepare_desktop_vnc.py --check` retains the complete historical `module_inputs()` equality guard. With no reuse properties, the module still calls that mode. A fresh preparation whose full sources match its receipt continues through the existing route. The changed Java owner does not silently become compatible with the historical full-source receipt.

An invocation may instead supply all three Gradle properties:

| Property | Required value |
| --- | --- |
| `cloudy.vnc.reuseReceiptSha` | Exact SHA256 of the selected immutable original native receipt. |
| `cloudy.vnc.reuseDeclaration` | Absolute path to an independently reviewed declaration directly under `.cache/vnc-product-engine-01/`. |
| `cloudy.vnc.reuseDeclarationSha` | Exact SHA256 of that declaration. |

A partial property set refuses configuration. No individual historical receipt digest or workstation path is embedded in the portable module. The explicit branch invokes `--check-native-reuse` with the receipt/declaration bindings; it does not call a compiler or change the original receipt. Resolve actual values and inspect current state before a future invocation; a historical successful command is not an operation queue.

The declaration has exactly `schema: 1`, `scope: OWNER_JAVA_ONLY_NATIVE_REUSE`, `originalReceiptSha256` and `expectedCurrentSources`. Its complete current-source map must match actual current hashes and the historical source path set. Duplicate fields, oversized declarations and symlink inputs refuse. Only these four exact source paths may differ:

- `adapters/desktop/vnc/android/src/main/java/io/github/baitian6641/cloudy/desktop/vnc/VncEngine.java`
- `adapters/desktop/vnc/android/src/test/java/io/github/baitian6641/cloudy/desktop/vnc/VncEngineTest.java`
- `tools/prepare_desktop_vnc.py`, the current checker implementation
- `adapters/desktop/vnc/android/build.gradle.kts`, the explicit check-entry recipe

The declaration authorizes verification of a reviewed current source set; it does not certify arbitrary Java behavior. The bridge, native CMake, other module files and helper inputs remain unchanged. New/deleted source paths or drift outside this allowlist refuse.

## Native checks and result meaning

Reuse still verifies the selected original receipt/pins/imports, generated namespace port, policy/staged hashes, stage receipt, exact prepared file inventory, published hashes, three-library closure, ELF architecture/alignment/dependencies and nine JNI exports. The existing selected toolchain verifier runs read-only. Current sources and the original receipt/declaration are checked again before success.

The returned report identifies the original receipt, declaration, complete current hashes, original/current source changes and original library hashes. It explicitly reports `nativeRebuilt: false`, `wholeModuleUnchanged: false` and `releaseClosureApproved: false`. It is a new check report, not a rewritten native-build receipt or a claim that the whole module is unchanged.

On 2026-10-16 the direct helper check passes in0.411seconds with empty stderr. The canonical module's Kotlin DSL/property branch and actual Exec task then pass under the existing isolated Gradle root, offline with strict dependency verification. That task alone establishes the explicit canonical check entry. It does not establish a new canonical Java build, assembly, JVM run or Android Lint run.

The separately accepted [owner repair](../../../qualification/results/P1-S4/2026-10-16-vnc-cleanup-repair.md) retains ten actual JVM passes and debug/release Lint0 from its affected source-set projection. Those Java sources and accepted AAR/native payloads remain unchanged; their results are carried as historical evidence and were not repeated here. The earlier failed aggregate and first offline inspector failure remain intact.

Actual device load, silent-peer cancellation, genuine uncertainty, framebuffer/authentication/input/UI behavior, Desktop integration and phase/capability gates remain open. D16's open-source Desktop direction persists; final release license/corresponding-source/notices closure is still a separate review. Inherited RDP receipt metadata in the notices assets supports reused SSL provenance only.
