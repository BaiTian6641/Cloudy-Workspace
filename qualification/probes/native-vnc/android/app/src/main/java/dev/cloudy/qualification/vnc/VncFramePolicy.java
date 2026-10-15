package dev.cloudy.qualification.vnc;

/** The adapter's single-frame limit, not a bound on process memory. */
public final class VncFramePolicy {
    private VncFramePolicy() {}
    public static boolean valid(int width, int height, int length) {
        return width > 0 && height > 0 && width <= 4096 && height <= 4096
                && (long) width * height <= 8_388_608L
                && (long) width * height * 4 == length;
    }
    public static void row(byte[] rgba, int offset, int[] argb) {
        for (int i = 0; i < argb.length; i++) {
            int p = offset + i * 4;
            argb[i] = 0xff000000 | ((rgba[p] & 255) << 16)
                    | ((rgba[p + 1] & 255) << 8) | (rgba[p + 2] & 255);
        }
    }
}
