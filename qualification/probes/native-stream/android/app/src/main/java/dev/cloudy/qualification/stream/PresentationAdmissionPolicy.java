package dev.cloudy.qualification.stream;

/** Presentation must not hide a retained-owner/evidence failure behind an ordinary prerequisite. */
public final class PresentationAdmissionPolicy {
    private PresentationAdmissionPolicy() {}
    public static String blocker(String ownerBlocker,boolean busy,boolean cleanupPending,boolean nativeUsed,
            boolean networkGranted,boolean identityReady,int selectedId,boolean surfaceReady) {
        if (ownerBlocker==null) return "UNKNOWN";
        if (ownerBlocker.startsWith("EVIDENCE_") || ownerBlocker.equals("CLEANUP_PENDING")
                || ownerBlocker.equals("PROCESS_OWNER") || ownerBlocker.equals("OWNER_NOT_VISIBLE")) return ownerBlocker;
        if (busy) return cleanupPending ? "CLEANUP_PENDING" : "OPERATION_ACTIVE";
        if (nativeUsed) return "NATIVE_PROCESS_USED";
        // Ordinary reasons are derived again from current facts, so an available Surface
        // does not remain blocked by a stale rejected-click reason.
        if (!networkGranted) return "NETWORK_PERMISSION_REQUIRED";
        if (!identityReady) return "IDENTITY_REQUIRED";
        if (selectedId<=0) return "CATALOG_REQUIRED";
        if (!surfaceReady) return "SURFACE_REQUIRED";
        return switch (ownerBlocker) {
            case "NONE", "OPERATION_ACTIVE", "NETWORK_PERMISSION_REQUIRED", "IDENTITY_REQUIRED",
                    "CATALOG_REQUIRED", "SURFACE_REQUIRED" -> "NONE";
            default -> ownerBlocker; // Unknown admission states fail closed.
        };
    }
}
