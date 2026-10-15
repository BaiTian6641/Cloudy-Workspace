package dev.cloudy.qualification.vnc;

/** Only a fresh explicit press acquires ownership; motion cannot re-arm a lost drag. */
public final class VncPointerState {
    private int owned;
    public int press(int actionButton, int physical) {
        owned |= actionButton & physical & 7;
        return mask();
    }
    public int update(int physical) { owned &= physical & 7; return mask(); }
    public int clear() { owned = 0; return 0; }
    public int mask() { return (owned & 1) | ((owned & 2) << 1) | ((owned & 4) >> 1); }
}
