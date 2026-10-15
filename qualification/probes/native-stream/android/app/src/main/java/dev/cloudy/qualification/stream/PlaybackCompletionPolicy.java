package dev.cloudy.qualification.stream;

/** An unused/CREATED controller is not retirement evidence while its worker can still publish it. */
public final class PlaybackCompletionPolicy {
    private PlaybackCompletionPolicy() {}
    public static boolean nativeSafe(boolean workerReturned,boolean runAdmitted,boolean controllerRetired,boolean promptOutstanding) {
        return workerReturned && !promptOutstanding && (!runAdmitted || controllerRetired);
    }
    public static boolean mayReleaseLease(boolean nativeSafe,boolean surfaceReleased,boolean terminalReadBack) {
        return nativeSafe && surfaceReleased && terminalReadBack;
    }
    public static boolean sameSurface(long operation,long boundOperation,long generation,long expectedGeneration) {
        return operation>0 && operation==boundOperation && generation==expectedGeneration;
    }
}
