# Native streaming: maintained Linux host entry

**Checked:** 2026-10-15, America/Chicago. **Owner/stage:** Desktop/native integration leader, P0-S4 then P1-S5. **Decision:** use the extracted official stable Sunshine AppImage as the next bounded host-launch candidate on the selected Ubuntu/Wayland KDE workstation. Its ELF dependency resolution passed without a system upgrade. The same release's Ubuntu26.04 DEB has concrete dependency incompatibilities and remains retained as a failed compatibility route. No Sunshine entry point, capture, encoder, input injection or listener was started by this preflight.

This carries forward the [Android engine entry](native-stream-adoption-entry.md), [primary host prerequisites](../linux-primary-host-qualification.md), [release/master distinction](../../07-reference/2026-10-15-sunshine-source-drift.md), [library reuse policy](../../05-delivery/library-reuse-policy.md) and [open-source Desktop direction](../../adr/0010-desktop-open-source-distribution.md). A maintained host replaces no independent RDP/VNC qualification gate and requires no custom Cloudy host agent.

## Exact selected release and actual package observations

The official [stable release](https://github.com/LizardByte/Sunshine/releases/tag/v2026.914.233613) is **v2026.914.233613**, source revision **`63d35f702ee9e362e43263742981836ec0710384`**. Use its pinned [setup](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/docs/getting_started.md) and [configuration](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/docs/configuration.md), rather than moving master behavior. Scoped PATH/dpkg/Flatpak application-metadata checks did not establish an installed Sunshine instance; no existing settings or credentials were read.

Both downloaded artifacts were byte-count/SHA256 verified **before extraction**. The URLs are the official release's `/releases/download/v2026.914.233613/` assets with the exact filenames below; the DEB URL encodes its `+` as `%2B`. Private download records preserve the complete URLs.

| Artifact / private run | Verified bytes | SHA256 | Actual result |
| --- | ---: | --- | --- |
| `sunshine_2026.914.233613-1+ubuntu26.04_amd64.deb` / `preflight-01` | 14,345,086 | `b8a8ceeeb2b3f30a18a994ad268c94e0c91170f33a864c7aad31eed927ad7d47` | Extraction passed; dependency compatibility failed |
| `Sunshine_2026.914.233613_x86_64.AppImage` / `preflight-02` | 43,883,000 | `6f80297a853bcf8cda114ff131b5c0a8c9a4d96a2a9c3dee1f04c39c54206846` | Extraction and actual ELF loader dependency resolution passed |

The DEB's extracted executable is 31,293,272 bytes, SHA256 `5e21c08a303116a0a8559cf781c3b03cd6c576be93fc67ecf6335ce334735c1a`. Its actual ELF requires `libminiupnpc.so.21`, absent from the inspected loader cache, and a `Qt_6.10` symbol version. Package control requires QtCore≥6.10.2; installed QtCore/GUI/Widgets are 6.9.2. A matching distro filename does not establish runtime compatibility. Do not upgrade the system Qt stack merely to satisfy this fixture.

The AppImage's extracted executable is 27,596,969 bytes, SHA256 `6946d8ac8bb0e99da1e51b374029c8b24dcabb315265a0e7004d046eb6f143fd`. Its own QtCore and `libminiupnpc.so.17` resolve the DEB blockers. Actual loader `--list` passed both with an explicit AppDir library path and with the packaged ordinary search paths. The receipt hashes 79 resolved bundled libraries. This invokes the ELF loader's inspection mode without executing Sunshine's application entry point; it does not initialize plugins, encoders or desktop capture.

## Executed operations and retained evidence

All download/extraction/inspection output stays under ignored `.cache/native-stream-host/`. No third-party implementation or host inventory is copied into tracked source. The only executed AppImage mode was `--appimage-extract`; neither `AppRun`, `--install` nor Sunshine itself was invoked.

```text
dpkg-deb --extract preflight-01/sunshine.deb preflight-01/extracted
dpkg-deb --control preflight-01/sunshine.deb preflight-01/control
readelf -d DEB_EXECUTABLE
readelf -l DEB_EXECUTABLE
readelf --version-info DEB_EXECUTABLE
/sbin/ldconfig -p
dpkg-deb --info preflight-01/sunshine.deb
# In the fresh preflight-02 directory:
./Sunshine.AppImage --appimage-extract
readelf -d squashfs-root/usr/bin/sunshine
readelf --version-info squashfs-root/usr/bin/sunshine
/lib64/ld-linux-x86-64.so.2 --library-path APPDIR/usr/lib --list APPDIR/usr/bin/sunshine
/lib64/ld-linux-x86-64.so.2 --list APPDIR/usr/bin/sunshine
```

The command block abbreviates paths for readability; receipts retain the actual argument vectors. Read-only package-version queries established the relevant installed Qt/libc/VA-API identities. A separately authorized outside-sandbox `stat`/access check inspected only the specific device nodes, Wayland socket and scoped session identifiers; it did not open devices or enumerate unrelated environment data.

| Private receipt | Bytes | SHA256 at this entry freeze |
| --- | ---: | --- |
| `.cache/native-stream-host/preflight-01/preflight.json` | 9,869 | `136e7bdb343d55eb93e170917fc1fe3896e61ebcb8e600a9bf015fdf1f2ce206` |
| `.cache/native-stream-host/preflight-02/preflight.json` | 18,845 | `5fea7862d33e54243dfd038e556bf3de43055027fbf4607fdbe76f070f78ef21` |

Each run retains `download.json` and actual inspection outputs; preflight-01 additionally retains extracted package control/scripts without executing them. Preflight-02 retains `extraction.log`, `inspection-1.txt` through `inspection-4.txt` and the extracted AppDir. These cache receipts are private preflight evidence, not a published binary distribution or a completed host qualification report.

## Actual host availability and permission boundary

The scoped host check reports Wayland/KDE. Outside the sandbox, **`/dev/uinput` and `/dev/dri/renderD128` exist and are readable/writable**, and the selected Wayland socket exists and is accessible. The earlier sandbox checks could not see these nodes. This establishes access availability only: no successful ioctl, encoder initialization, capture or remote input is inferred. No device permission repair is currently justified by the preflight.

Select **`capture=kwin`** first. The stable [KWin implementation](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/src/platform/linux/kwingrab.cpp) uses `zkde_screencast_unstable_v1 → PipeWire`; this is not a generic RemoteDesktop portal authorization claim. It checks a desktop-file permission for the executable and may create/update a permission file in the user's XDG applications directory and clean stale temporary entries. A newly written permission file may not be recognized until Sunshine restarts. Before launch, the parent must identify the exact file/ownership and cleanup behavior; a fresh private configuration alone does not contain this side effect. Do not set `KWIN_WAYLAND_NO_PERMISSION_CHECKS` to disable compositor protection.

The AppImage does not support KMS. KMS would require `cap_sys_admin`; do not switch to it or grant capabilities to manufacture a pass. The [AppRun install path](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/packaging/linux/AppImage/AppRun) writes system input/module rules using sudo and installs integration files. Those actions were not performed and are unnecessary to assume from extraction. The pinned [input rules](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/src_assets/linux/misc/60-sunshine.rules) explain uinput access; current access must be tested before proposing any udev change. Gamepad/uhid scope is separate from the first keyboard/mouse fixture.

## Next finite fixture and stop conditions

The parent-owned launch should use the extracted official executable, a fresh private directory and explicit absolute `file_apps`, `file_state`, `credentials_file`, `pkey`, `cert` and `log_path` values. Use a minimal finite test-target app list. Do not read, import or overwrite any existing Sunshine identity, credentials or enrolled-client state. Set `upnp=disabled`, `origin_web_ui_allowed=pc` and an explicitly chosen `bind_address`; the default all-interface binding is not an isolation policy. Use a reviewed finite foreground process group with a deadline/log bound and verified cleanup; enable no service or autostart. Certificate/admin provisioning and listener exposure belong to that next explicitly controlled fixture, not this preflight.

For the AMD route, try the actual maintained VA-API encoder and record the encoder/capture backend selected in the log. A software fallback is a separately labelled result and does not qualify hardware encode performance. Stop and report an actual dynamic-library/plugin, render-node, KWin permission/protocol, encoder or uinput error rather than disabling protections or changing the desktop stack.

Unresolved gates are actual executable/plugin initialization; KWin permission recognition and changing captured output; actual VA-API format/profile/encode; controlled keyboard and relative-pointer delivery/release; finite close/restart; real app-owned Googlebook pairing/TLS/trust and decoder/Surface integration; and measured latency/performance. Availability and loader success are useful prerequisites, not CAP-STREAM, physical input, desktop capture or hardware-encoding passes. Final compatible source/notices/build-material release closure remains under ADR0010.
