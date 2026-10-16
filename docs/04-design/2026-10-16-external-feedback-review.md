# External design feedback review

Reviewed 2026-10-16 against source commit `7ce21a43401c64ea123c433d66c041b166aa2f76`. The user supplied `cloudy_design_language_optimization.docx`, attributed to Gemini 4 Argon, for review. Its SHA256 is `d9915ed8bb0c86d9ae6be8e83e501c20fa8d7f3e9e5e8d90ca8fce7469a17d03` (51,461 bytes). The local original was read without modification; its 14 rendered pages were inspected. It contains no embedded images, tracked edits or comments. The original and private rendering intermediates are not published in this repository.

The feedback offers useful P1-S2 candidates. It is external review material, **not an approved specification or instructions to workers**. In particular, its EDA/IDE product, micro-density tier, toolkit theme injection and embedded-window assumptions do not amend [Rule 0](../00-project/platform-rule-zero.md), the Console/Desktop split or separate [X11 windows](../02-architecture/x11-windows.md). The full implementation goal continues; this review does not complete a stage or qualify a component.

## Six discrepancy claims

| Feedback topic | Finding from current source | Recommended disposition |
| --- | --- | --- |
| Typography bindings | [Specimen CSS](preview.css) hardcodes body 14px/1.5, terminal 14px/1.85 and inspector text line-height 1.8. These diverge from canonical role values. Native typography already uses generated values in scalable sp. | Reconcile specimen roles during P1-S2. The terminal change from 25.9px to 21px implies about 23.3% more rows per fixed height before rounding/padding; it is not a measured native capacity or readability gain. |
| Shell geometry | Toolbar64 differs from baseline56; navigation212 and inspector264 differ from defaults224/288 but remain within their adjustable ranges. Status minimum34 satisfies the documented minimum28. | Centralize accepted defaults while retaining resize, content wrapping and font scaling. Do not classify every difference as a contract violation. |
| Standard pointer | The specimen offers only Comfortable/Compact, explicitly documented in [layout](layout.md). Its table heights differ from nominal tiers. | Adding Standard pointer is useful existing-scope work. Default selection needs qualified input policy; exact fixed heights and a fourth 26dp EDA tier are new proposals. |
| Status foreground roles | Canonical success/warning/danger foregrounds already pair with their containers and pass declared contrast checks. Separate on-container names are absent. | Consider explicit aliases for clearer component semantics. Material `ColorScheme` has error roles, not built-in success/warning roles; this is not a missing one-to-one API mapping. |
| Hover fill | Unselected navigation hover and neutral monogram both use surface-raised. The badge foreground remains visible. | Resolve the fill collision through a demonstrated state treatment; the entire badge does not disappear. |
| Focus/accent | Light focus and accent share a color, but accent-container is a different color. Existing CSS uses a 2px ring with 3px offset. | Inspect actual adjacent states before choosing a halo. Color equality alone does not prove failed focus visibility. |

## Numerical and standards corrections

Independent sRGB calculation reproduces 27 of the document's 28 light/dark ratios in section 3 to two decimals. Dark terminal text `#DCE9ED` against `#0C1519` is **14.88:1**, not 15.22:1; it still passes the stated text threshold. The current canonical 25 declared pairs per theme and 16 ANSI colors per theme pass their configured checks: 82 checks total. This covers declared solid-color combinations only.

Focus against accent-container is **5.14:1 light / 6.06:1 dark**. This supports further state inspection rather than the asserted failure. [W3C non-text contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) evaluates meaningful indicators against adjacent colors; it does not require all temporal hover/rest states to contrast 3:1 with one another. Actual overlays, clipping and focus placement still matter (D11).

The proposed EDA table also contains arithmetic discrepancies: `#8DB4FF` on `#101C22` is 8.34:1, not 8.44:1; `#EBC577` is 10.56:1, not 10.22:1. For the active-PC row, the displayed `#7DD5C7` bar against `#164C43` is 5.68:1; its quoted 7.74:1 belongs to the different canonical on-primary-container foreground. A row listing wire, bus and cell colors cannot assign one contrast ratio to all three. These are proposed data colors, not accepted Cloudy tokens.

The claim that staying below 16:1 avoids halation is unsupported by the supplied evidence. Contrast ratios do not establish long-session visual comfort or overall WCAG conformance. [W3C contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) supplies minimum criteria; display conditions, individual preference, typography and rendered interaction require separate review (D10). Keep eye-comfort language as design intent.

## Native work the feedback does not cover

The [Android generator](../../tools/generate_android_tokens.py) already emits canonical colors, numeric typography, spacing, radii, density and motion. [CloudyTheme](../../shared/design-system/android/src/main/java/io/github/baitian6641/cloudy/design/CloudyTheme.kt) maps a subset into Material roles. Its secondary and secondaryContainer both currently use AccentContainer; other surface/inverse/tertiary and typography roles retain Material defaults. This is a partial local-window implementation, not the completed catalog. Review roles actually used by each new component, including selection/disabled/focus, before expanding coverage. Emitted constants alone do not implement density or shell geometry.

[Carbon's guidance](https://www.carbondesignsystem.com/building-blocks/foundations/color/overview) alternates light-theme layers and progressively lightens dark-theme layers, with separate interaction tokens (D01). The proposed monotonic surface mapping is an original Cloudy candidate, not a literal Carbon-to-Material equivalence. Keep static container roles separate from transient hover/press roles. The inspected [upstream ColorScheme source](https://android.googlesource.com/platform/frameworks/support/+/eec456ba3412a9a3b355a127c065fbab72ad7392/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ColorScheme.kt) documents surface and foreground roles (D13); this source snapshot is explanatory, not a dependency repin or device result.

## Useful candidates and protected boundaries

Flat working panes, restrained control rounding, quiet secondary diagnostics, readable prose and labeled workspace identity fit the existing design direction. A prose role and explicit informational colors can be evaluated when an actual component needs them. Identity colors supplement host/account/trust text. Critical permission, ownership, errors and capture-release controls remain visible.

Retain the three current density tiers as nominal baselines with content-driven growth. Do not freeze rows at a height that clips user-scaled text or creates overlapping targets. [Compose accessibility guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) explains minimum touch-target expansion and overlap risks (D12). Desktop precision modes require their own review; they do not justify a global font reduction or a phone/tablet workstream.

Cloudy owns its chrome and terminal defaults. It must preserve terminal ANSI/truecolor and remote RDP/VNC/engineering content. Separate X11 application windows and modal ownership remain required; a unified embedded canvas is not a substitute. General Wayland embedding, removing remote menus, automatic GTK/Qt/Xresources configuration, local EDA editors/synthesis tools and shared HIL authority are outside the approved scope of this feedback review. Future integrations need explicit scope and real backend contracts. Bare Escape and canvas mnemonics follow existing [input ownership](interaction.md), including OS/IME/modal precedence and a distinct release route.

## Follow-through

P1-S2 owner: design/Android worker, accepted by the integration leader. First reconcile current native roles and clear specimen typography differences; then demonstrate necessary surface/state, geometry and three-density behavior. Proposed colors, halo and prose values remain candidates until component review. Check both themes, hover/pressed/selected/focused states, narrow/short desktop windows and user font scaling through the existing [native review](native-workspace-review.md). Preserve native and specimen evidence separately.

No token, CSS, APK or device behavior changes in this review. No new stage, dependency, EDA product, automatic host mutation or capability promotion is authorized. P1-S3 [protected identity work](../03-platform/googlebook/product-vault-entry.md) remains independently ready. Two direct Codex coworkers supplied read-only audits; the leader checked their source findings, corrected one arithmetic interpretation and independently recomputed contrast. The supplied document was not sent to OMP.

## Follow-up — 2026-10-16 typography alignment

The [bounded specimen typography record](../06-validation/2026-10-16-specimen-typography.md) now binds the CSS change and four final light/dark renders at1440px and560px. Body14/20, page24/32 and terminal14/21 use existing canonical roles, including responsive overrides. The parent visually reviewed the final captures after correcting theme/transition capture issues. This is Comfortable Console synthetic HTML evidence only; native font scaling, accessibility, other app/density and full P1-S2 acceptance remain open. The original dated source findings above are preserved.
