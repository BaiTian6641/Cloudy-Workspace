package com.termux.x11;

import android.util.Log;

/** Native-link class for libXlorie; not the Cloudy activity or a hidden-API launcher. */
@SuppressWarnings("unused")
public final class MainActivity {
    private MainActivity() { }
    private static native void nativeSetLaunchWindowingModeFreeform(Object activityOptions);
    void clientConnectedStateChanged() {
        Log.d("CloudyX11Engine", "Upstream renderer connection callback (not used by broker)");
    }
}
