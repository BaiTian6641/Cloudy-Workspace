package dev.cloudy.qualification.stream;

import java.io.IOException;

/** Fixed diagnostic reason codes; never expose a filesystem path or parser message. */
final class EvidenceAdmissionException extends IOException {
    private static final long serialVersionUID=1L;
    final String reason;
    EvidenceAdmissionException(String reason) { super(reason);this.reason=reason; }
}
