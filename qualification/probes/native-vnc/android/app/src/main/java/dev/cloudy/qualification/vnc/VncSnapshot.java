package dev.cloudy.qualification.vnc;

public final class VncSnapshot {
    public final String state, detail;
    public final boolean busy, connected;
    public final long callbacks, converted, drawn, admitted, completed, written;
    public final int width, height;
    VncSnapshot(String state, String detail, boolean busy, boolean connected, long[] counts, int width, int height) {
        this.state = state; this.detail = detail; this.busy = busy; this.connected = connected;
        callbacks = counts[0]; converted = counts[1]; drawn = counts[2]; admitted = counts[3]; completed = counts[4]; written = counts[5];
        this.width = width; this.height = height;
    }
}
