# Platform questions and capability watchlist

These questions are planned work, not requests that block repository preparation.

| Question | Current evidence | Next experiment / owner | Product fallback |
| --- | --- | --- | --- |
| Can an ordinary app attach to the built-in Linux VM? | No supported third-party control/attach API established in reviewed guides | Search official SDK and compile only documented surfaces; platform, P0-S2 | Remote SSH environments |
| Does physical trackpad relative capture match CAD needs? | Mouse capture API documented; specific trackpad behavior untested | Event-source trace and real CAD orbit/pan; desktop/device, P0-S4 | Qualified mouse or absolute mode |
| Which SSH engine works best on this ARM64 OS/WebView combination? | Library/packaging selection unresolved | Same trust/IME/TUI/forward tests on candidates; terminal, P0-S3 | Swap adapter/renderer |
| What valid background model supports each connection type? | Type/duration restrictions differ by operation and target | Build operation matrix and timeout probe; Android, P0-S2/P1-S3 | Host durability + visible reconnect |
| Can external streaming/USB apps be controlled through a supported interface? | No API is presumed from installation alone | Check upstream docs/source/version and launch a minimal flow; integrations, P0-S4/S5 | Manual handoff with instructions |
| Are the original broad Sunshine macOS warnings current? | Current getting-started source no longer carries the original blanket experimental label in its macOS section | Pin a release and qualify permissions/capture/input; macOS, P0-S4 | Explicit unsupported capability per result |
| Which Wayland combinations work? | Upstream lists capture methods; actual session/compositor untested | Record GNOME/KDE/wlroots + version + capture/input path; Linux, P0-S4 | Supported backend/compositor matrix |
| Can the primary debug probe survive reset and reenumeration? | Android USB server availability does not prove this cable | Flash/verify actual hardware with device identity trace; embedded, P0-S5 | Lab-side provider or vendor server |
| Which adaptive library features are production-ready? | Overview mixes stable and experimental examples | Pin artifacts and inspect annotations/releases; Android, P0-S2/P1-S1 | Stable window constraints/layout primitives |
| Which display/decoder modes are sustained? | No measurements | Enumerate, stream, profile, record thermal/power behavior; QA, P0-S4/S6 | H.264 balanced profile |

Revisit Google's Linux roadmap and API 37 continuity independently. A platform feature shipping does not automatically expose a public third-party API for Cloudy. See the maintained [source watchlist](../../07-reference/watchlist.md).
