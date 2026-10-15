package dev.cloudy.qualification.stream;

/** Memory-only fixture PIN bounds; no normalization or retention of caller storage. */
public final class PairingPinPolicy {
    private PairingPinPolicy() {}
    public static boolean valid(char[] value) {
        if (value == null || value.length != 4) return false;
        for (char digit:value) if (digit < '0' || digit > '9') return false;
        return true;
    }
    public static boolean endpoint(String id,String address,int http,int https) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,64}") && "127.0.0.1".equals(address)
                && http > 0 && http <= 65535 && https > 0 && https <= 65535 && http != https;
    }
}
