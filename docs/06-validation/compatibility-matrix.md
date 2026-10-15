# Compatibility matrix: initial unqualified inventory

No physical runs have been performed by this repository initialization. All rows are **unqualified**, and unknown values must be filled from actual equipment.

| Client / host | Installed OS/build | Backend/compositor/driver | Workflow | Status | Evidence |
| --- | --- | --- | --- | --- | --- |
| HP Googlebook 14 | Unknown | WebView/ABI/decoder unknown | Keyboard/IME/resize/process death | Unqualified | None |
| HP Googlebook 14 | Unknown | Physical trackpad unknown | Relative CAD input/capture release | Unqualified | None |
| HP Googlebook 14 | Unknown | USB provider/probe unknown | Actual flash/verify/reset/owner reconciliation | Unqualified | None |
| MS-02Ultra | Unknown; do not infer Windows/Linux | GPU/driver/session unknown | Primary SSH/desktop/embedded workflow | Unqualified | None |
| Mac Studio | Unknown macOS version | Capture/input permissions/backend unknown | SSH/visual/own-job events | Unqualified | None |
| ASUS Zenbook Duo | Unknown | GPU/session unknown | Secondary host | Unqualified | None |
| HP ZBook Ultra G1a | Unknown | GPU/session unknown | Secondary host | Unqualified | None |
| Selected Linux environment | Unknown | Distribution + Wayland compositor/version unknown | Capture/input/lock/headless/notifications | Unqualified | None |

## Add a qualified row

Use a narrow combination: client SKU/build + app version + backend versions + host OS/session/compositor/GPU + input device + probe/tool/firmware + route/display profile. State the exact passing case IDs, run link, and exclusions. Partial support is a list of passing capabilities, not a green OS badge.

Separate native app, external backend, and lab-side fallback topologies. A future backend or OS update creates a new qualification row or a required regression run; it does not inherit every prior pass.
