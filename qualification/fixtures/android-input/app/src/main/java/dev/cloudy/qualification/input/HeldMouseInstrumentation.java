package dev.cloudy.qualification.input;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/** Finite framework mouse stimulus; its result says nothing about remote host delivery. */
public final class HeldMouseInstrumentation extends Instrumentation {
    private static final String VNC_PACKAGE = "dev.cloudy.qualification.vnc";
    private Bundle arguments;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : new Bundle(args);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            String mode = arguments.getString("mode");
            if (!"inspect".equals(mode) && !"hold".equals(mode)) {
                throw new IllegalArgumentException("mode must be inspect or hold");
            }
            UiAutomation automation = connectAutomation();
            if ("inspect".equals(mode)) {
                Snapshot snapshot = inspectWindow(automation);
                result.putAll(snapshot.bundle());
                result.putString("result", "inspection_only_no_input");
            } else {
                runStimulus(automation);
                result.putString("result", "framework_down_and_up_accepted");
            }
            code = Activity.RESULT_OK;
        } catch (RuntimeException error) {
            // Only bounded diagnostic text is returned; no UI tree or credentials are collected.
            result.putString("result", "failed");
            result.putString("error", error.getClass().getSimpleName());
            result.putString("reason", boundedReason(error));
            // Preserve primary failure before framework finish may fail during disconnection.
            Bundle failure = new Bundle();
            failure.putString("event", "primary_failure");
            failure.putString("error", error.getClass().getSimpleName());
            failure.putString("reason", boundedReason(error));
            failure.putLong("uptimeMs", SystemClock.uptimeMillis());
            failure.putLong("elapsedRealtimeMs", SystemClock.elapsedRealtime());
            sendStatus(0, failure);
        } finally {
            finish(code, result);
        }
    }

    private static String boundedReason(RuntimeException error) {
        String message = error.getMessage();
        return message == null ? "unspecified" : message.substring(0, Math.min(256, message.length()));
    }

    private long number(String key, long minimum, long maximum) {
        String value = arguments.getString(key);
        if (value == null) throw new IllegalArgumentException("missing " + key);
        long parsed;
        try { parsed = Long.parseLong(value); }
        catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid " + key, error);
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException("out of range " + key);
        }
        return parsed;
    }

    private UiAutomation connectAutomation() {
        UiAutomation automation = getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        if (automation == null) throw new IllegalStateException("UiAutomation unavailable");
        AccessibilityServiceInfo service = automation.getServiceInfo();
        if (service == null) throw new IllegalStateException("accessibility inspection unavailable");
        service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(service);
        return automation;
    }

    private static Snapshot inspectWindow(UiAutomation automation) {
        final long deadline = SystemClock.elapsedRealtime() + 2000;
        String unavailable = "root unavailable";
        do {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                if (!VNC_PACKAGE.contentEquals(root.getPackageName())) {
                    throw new IllegalStateException("active root is not VNC");
                }
                Rect bounds = new Rect();
                root.getBoundsInScreen(bounds);
                AccessibilityWindowInfo window = root.getWindow();
                if (window != null) {
                    if (!window.isActive() || !window.isFocused()
                            || window.getDisplayId() != 0 || window.getId() < 0
                            || bounds.isEmpty() || bounds.left < 0 || bounds.top < 0
                            || bounds.right > 16384 || bounds.bottom > 16384) {
                        throw new IllegalStateException(
                                "active focused default-display VNC root unavailable");
                    }
                    return new Snapshot(bounds, window.getId(), SystemClock.uptimeMillis(),
                            SystemClock.elapsedRealtime());
                }
                unavailable = "window unavailable";
            } else {
                unavailable = "root unavailable";
            }
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) break;
            // Only missing snapshots are transient. A different app/focus/geometry never retries.
            SystemClock.sleep(Math.min(50, remaining));
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new IllegalStateException(unavailable + " after readiness deadline");
    }

    private static final class Snapshot {
        final Rect bounds;
        final int windowId;
        final long uptime;
        final long elapsedRealtime;

        Snapshot(Rect bounds, int windowId, long uptime, long elapsedRealtime) {
            this.bounds = bounds;
            this.windowId = windowId;
            this.uptime = uptime;
            this.elapsedRealtime = elapsedRealtime;
        }

        Bundle bundle() {
            Bundle value = new Bundle();
            value.putString("event", "inspection");
            value.putString("package", VNC_PACKAGE);
            value.putBoolean("active", true);
            value.putBoolean("focused", true);
            value.putInt("displayId", 0);
            value.putInt("windowId", windowId);
            value.putInt("left", bounds.left);
            value.putInt("top", bounds.top);
            value.putInt("right", bounds.right);
            value.putInt("bottom", bounds.bottom);
            value.putLong("inspectedUptimeMs", uptime);
            value.putLong("inspectedElapsedRealtimeMs", elapsedRealtime);
            return value;
        }
    }

    private static void requireFresh(long now, long inspected) {
        if (inspected > now || now - inspected > 30000) {
            throw new IllegalStateException("inspection expired or clock changed");
        }
    }

    private void runStimulus(UiAutomation automation) {
        int x = (int) number("x", 0, 16383);
        int y = (int) number("y", 0, 16383);
        long hold = number("holdMs", 2000, 30000);
        long inspectedUptime = number("inspectedUptimeMs", 0, Long.MAX_VALUE);
        long inspectedElapsed = number("inspectedElapsedRealtimeMs", 0, Long.MAX_VALUE);
        Rect expected = new Rect((int) number("left", 0, 16383),
                (int) number("top", 0, 16383), (int) number("right", 1, 16384),
                (int) number("bottom", 1, 16384));
        if (expected.isEmpty() || !expected.contains(x, y)) {
            throw new IllegalArgumentException("point outside inspected root");
        }
        Snapshot current = inspectWindow(automation);
        if (!current.bounds.equals(expected)) {
            throw new IllegalStateException("inspected VNC root bounds changed");
        }
        // Accessibility window IDs may change across separate automation registrations.
        // Retain the actual ID as diagnostic evidence; package/focus/bounds/clocks gate input.
        Bundle admission = current.bundle();
        admission.putString("event", "hold_admission_snapshot");
        sendStatus(0, admission);
        // Check both clocks after window inspection. Suspend cannot renew admission.
        requireFresh(SystemClock.uptimeMillis(), inspectedUptime);
        requireFresh(SystemClock.elapsedRealtime(), inspectedElapsed);
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = mouse(downTime, downTime, MotionEvent.ACTION_DOWN,
                MotionEvent.BUTTON_PRIMARY, x, y);
        boolean downAccepted = false;
        boolean upAccepted = false;
        // Even a thrown/false injection may have partially dispatched: always attempt release.
        try {
            try {
                downAccepted = automation.injectInputEvent(down, true);
            } finally {
                status("down", downAccepted, downTime, 0);
            }
            if (!downAccepted) throw new IllegalStateException("DOWN rejected");
            SystemClock.sleep(hold);
        } finally {
            down.recycle();
            long upTime = SystemClock.uptimeMillis();
            MotionEvent up = mouse(downTime, upTime, MotionEvent.ACTION_UP, 0, x, y);
            try {
                // This is cleanup of the held gesture even after focus moves to system UI.
                upAccepted = automation.injectInputEvent(up, true);
            } finally {
                up.recycle();
                status("up", upAccepted, upTime, upTime - downTime);
            }
        }
        if (!upAccepted) throw new IllegalStateException("UP rejected");
    }

    private static MotionEvent mouse(long downTime, long eventTime, int action,
            int buttons, int x, int y) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = x;
        coordinates.y = y;
        coordinates.pressure = action == MotionEvent.ACTION_UP ? 0 : 1;
        coordinates.size = 1;
        return MotionEvent.obtain(downTime, eventTime, action, 1,
                new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coordinates}, 0, buttons,
                1, 1, 0, 0, InputDevice.SOURCE_MOUSE, 0);
    }

    private void status(String event, boolean accepted, long uptime, long elapsed) {
        Bundle status = new Bundle();
        status.putString("event", event);
        status.putBoolean("injectionAccepted", accepted);
        status.putLong("uptimeMs", uptime);
        status.putLong("elapsedMs", elapsed);
        sendStatus(0, status);
    }
}
