SSH observation runtime licenses and notices

This directory adds notices for the retained seven P0-S3 runtime JARs used for credential-free observation:
SSHJ 0.41.2, bcprov/bcpkix/bcutil-jdk18on 1.86, SLF4J API/NOP 2.0.20 and
asn-one 0.6.0. It does not replace other dependency notices
or select a product distribution license.

../ssh-provenance.json binds binary/POM/source digests and copied notice bytes.
The parent build must compare its actual locked debug/release graph with this
inventory and verify every asset byte in the final APK. Until that check,
these source assets do not prove APK inclusion or Android compatibility.

SSHJ and asn-one JARs lack embedded license/notice texts; their pinned upstream
texts are retained. SSHJ NOTICE contains additional derived-code terms.
The three BC JARs have byte-identical embedded LICENSE.md; retained once.
SLF4J API and NOP 2.0.20 embed their module MIT license. The exact published
JAR text and pinned source text are both retained, with their distinct byte
representations recorded rather than silently normalizing them.
Only license/notice/copyright material is copied; no third-party implementation
is vendored. Application-owned sanitized errors remain required even though
SLF4J NOP deliberately emits no library log messages.
