# VNC native reuse: explicit helper and canonical check entry

On 2026-10-16, the reviewed explicit retained-native route passes both its direct helper check and the canonical VNC module's `:vnc:verifyNative` task. The [matching manifest](2026-10-16-vnc-native-reuse.json) binds adopted sources, declaration, original receipt, actual outputs and carried Java evidence. The [entry contract](../../../docs/03-platform/googlebook/vnc-native-reuse-entry.md) defines the scope and three optional properties.

## What changed and why

The historical native receipt includes the whole module, so the accepted Java cleanup repair makes default full-source `--check` refuse even though the JNI compiler inputs and packaged native/source-notice bytes remain unchanged. The new route makes that distinction explicit. Default checking still requires complete source equality; fresh normal preparation/CI does not depend on one workstation's historical receipt.

An explicitly selected invocation provides all three portable Gradle properties: `cloudy.vnc.reuseReceiptSha`, `cloudy.vnc.reuseDeclaration` and `cloudy.vnc.reuseDeclarationSha`. The hash-bound declaration requires exact current hashes and the same historical source path set. Only the two owner Java/test paths, checker implementation and Gradle check-entry implementation may differ. Native CMake, the JNI bridge and all other declared sources remain exact. No historical receipt digest or workstation path is hardcoded into the module.

All original selected-import, namespace/policy/staged, exact published inventory/hash and ELF/library/export checks remain active. Success returns a separate scoped report with `nativeRebuilt=false`, `wholeModuleUnchanged=false` and `releaseClosureApproved=false`; it never rewrites the old receipt or calls a native compiler.

## Actual evidence

Parent adopts the exact three independently reviewed helper/test/module proposal files. Fourteen helper Python methods pass, as do the34-file project AST check and full project Ruff. Those terminal checks were observed in parent tool chunk `3d676c`; no separately retained raw log file exists for them, and none is invented by this report. The two new metadata/receipt-policy methods use explicit test seams and do not establish native behavior.

The direct actual CLI check exits0 in0.411seconds, with empty stderr and unchanged current sources/original receipt. It binds original native receipt SHA `06bc8873a11f37bbc6faae798a29568d8e0acb11e8ec3a0180a6a76b109dae26` and new immutable declaration SHA `d1d05142944e77607fbfa4754cd86bfa88dbab139307eba4969aff1d64895aeb`. Its report contains exactly the four allowed source changes and confirms unchanged original libraries and staged/published bytes.

The actual canonical module `:vnc:verifyNative` task then exits0 under the existing isolated Gradle root, offline with strict dependency verification, in8.014seconds. The canonical task result SHA is `53ce9fd9b140bed265e64d38943748baa21c3244e31a9604c1e4c91f331c3b4f`. This executes the real module's Kotlin DSL/property/Exec branch. It is **task-entry verification only**: no Java/JVM/Lint/assembly task, JNI/upstream rebuild or device operation runs in this invocation.

The declaration, actual CLI result, canonical task report and current module source hashes agree. The three retained native-library hashes remain unchanged. The [earlier owner repair](2026-10-16-vnc-cleanup-repair.md) separately supplies ten actual JVM passes, both Lint0 reports and accepted repaired AARs. Their Java bytes are unchanged and their evidence is carried, not rerun. Historical aggregate `passed=false` and the first offline inspection-size-cap failure remain immutable; this result does not rewrite either outcome or turn the previous projection into a canonical build.

## Remaining boundaries

The module remains outside root settings and the Desktop dependency graph. This check opens a legitimate explicit canonical verification route; complete root adoption/build packaging and actual wrapper/JNI runtime still need their own reviewed inputs and evidence. There is no new product APK, install/grant, listener, authentication, frame/input/UI/latency result or phase/capability promotion.

No upstream validation battery was recreated. The final release license/source/notices closure remains open, including inherited RDP native-receipt metadata used only for reused SSL provenance. The full P1/P3 integration and independent product-release gates remain unchanged.
