# Native VNC patches

The pinned Android TLS/JPEG build has **no upstream patches**. The ordered empty patch set is declared in `sources.lock.json`; the helper rejects undeclared or mismatching patches. Supported `LZO_CFG_NO_UNALIGNED=1` is a compiler configuration selecting portable bundled miniLZO accesses, not a source patch or sanitizer suppression. It carries forward the bounded host regression configuration without rerunning the accepted 27 host cases.

Future patches need an exact ordered path, byte count and SHA256 in the lock, with a concrete integration reason and review. This directory does not implement a JNI adapter, app or server fixture.
