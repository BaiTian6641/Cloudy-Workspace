# P1-S2 — Visible committed window context

**2026-10-16. Bounded pass; P1-S2 remains in progress.** Console/Desktop0.1.1/code2 display the committed local window name as a semantic heading, with app identity beneath it. Editing an unapplied draft does not replace the committed context. This follows the P1-S1 observation that the Googlebook system caption shows only the application label.

The change uses the existing native typography/colors, text scaling, wrapping, scrolling and caption/IME insets. It adds no remote controls, toolbar, shortcut interceptor or dependency. The local name is not a verified remote host identity; future engine-backed context must carry its actual profile/trust state.

## Actual evidence

The base is `6017877fa5b2bc0169ffec740e33eb0115cc7bf6`; [manifest](2026-10-16-committed-context.json) binds the new source and private build/device evidence. Build13 passes strict verification, compiler, nine JVM tests and zero app Lint, and compiles both changed Activity test APKs. The [fresh-build review](../../../docs/06-validation/2026-10-16-foundation-clean-build.md) separately records dependency-metadata corrections without changing selected versions.

| App | Installed product SHA256 |
| --- | --- |
| Console0.1.1/code2 | `e9aa9841b08a340f2ba15308f83aa2be1c8defb2f2c5bada77dfe4baaf39af26` |
| Desktop0.1.1/code2 | `dc1308c6c589064d1a1a6adf93f67678b8a2109eef5db61dc49125b0100acbff` |

Each new APK passes signature/package checks and installs as an update over its existing0.1.0 product. On the same HP Googlebook14 Android17/API37 arm64-v8a build `CL3B.260622.276.R2`, incremental16560795, two actual Activity cases per app pass. The existing restoration case now asserts a displayed semantic heading after Apply, while a different unsaved draft is present, and after recreation. Committed owner/identity/title/task label, new Activity generation, draft and selection remain checked; the invalid-edit case still passes. These four executions use framework test stimulus, not a physical keyboard or IME.

Both final packages report0.1.1/code2 and are stopped. The two temporary test packages are removed; no product test package remains. Boot ID/count are unchanged. No old qualification package, credential, network fixture or device lock setting is changed. The earlier independent installation and Desktop New/Close observations carry forward under D12; they are not repeated or relabeled as new physical observations.

## Review and continuation

The parent reviewed the worker diff and a second direct Codex reviewer found no actionable heading/test issue. The test deliberately selects a heading semantic, so an editable-field match cannot satisfy it accidentally. Source-only review, compiler and actual Android assertions are separate evidence.

This is the first P1-S2 increment, not the complete desktop component catalog. Narrow/short windows, two themes, font/display scaling, keyboard/menu/context interactions, accessibility and engine-backed layouts remain required. P1-S3 has a [reviewed vault entry](../../../docs/03-platform/googlebook/product-vault-entry.md), with no implemented vault or authorization claim. All feature/platform capability gates remain unchanged.
