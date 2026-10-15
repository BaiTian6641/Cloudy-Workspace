package dev.cloudy.qualification.stream.playback;

/** Pure bounds for this first finite preview, not a general product streaming policy. */
public final class PlaybackPolicy {
    private PlaybackPolicy() {}
    public static long deadline(long now,long authenticatedAt,long duration) {
        if (authenticatedAt<0 || authenticatedAt>now || duration<=0 || duration>30_000
                || authenticatedAt>Long.MAX_VALUE-duration || now-authenticatedAt>=duration) throw new IllegalArgumentException("authorization-window");
        return authenticatedAt+duration;
    }
    public static boolean permits(long now,long authenticatedAt,long deadline,boolean cancelled) {
        return !cancelled && now>=authenticatedAt && now<deadline;
    }
    public static boolean sessionUrl(String address,int port,String value) {
        try {
            java.net.URI uri=new java.net.URI(value);
            return ("rtsp".equals(uri.getScheme()) || "rtspenc".equals(uri.getScheme()))
                    && address.equals(uri.getHost()) && uri.getPort()==port && uri.getRawUserInfo()==null
                    && uri.getRawQuery()==null && uri.getRawFragment()==null
                    && (uri.getRawPath()==null || uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()));
        } catch (Exception rejected) { return false; }
    }
    public static boolean serverVersion(String value,boolean optional) {
        return value==null ? optional : value.matches("[0-9]{1,5}(\\.[0-9]{1,5}){3}");
    }
    public static boolean port(int port) { return port>=1 && port<=65535; }
    public static boolean requestPath(String path) { return "/serverinfo".equals(path) || "/launch".equals(path) || "/resume".equals(path); }
}
