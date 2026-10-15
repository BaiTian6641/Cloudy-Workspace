package dev.cloudy.qualification.stream;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.limelight.BuildConfig;
import com.limelight.R;
import com.limelight.nvstream.jni.MoonBridge;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;
import org.json.JSONObject;

/** Finite local ABI observation. No network, private key or upstream component entry. */
public final class StreamProbeActivity extends Activity {
    private TextView status;
    private Button load;
    private boolean destroyed;
    private static final Object EVIDENCE_LOCK = new Object();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(padding + safe.left, padding + safe.top,
                    padding + safe.right, padding + safe.bottom);
            return insets;
        });
        TextView title = label(content, R.string.cloudy_stream_title);
        title.setTextSize(22);
        label(content, R.string.cloudy_stream_scope);
        status = label(content, R.string.cloudy_stream_ready);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        load = new Button(this);
        load.setText(R.string.cloudy_stream_load);
        load.setOnClickListener(view -> checkNative());
        content.addView(load);
        Button close = new Button(this);
        close.setText(R.string.cloudy_stream_close);
        close.setOnClickListener(view -> finish());
        content.addView(close);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        content.requestApplyInsets();
    }

    private TextView label(LinearLayout parent, int resource) {
        TextView view = new TextView(this);
        view.setText(resource);
        view.setTextSize(16);
        view.setPadding(0, 12, 0, 12);
        parent.addView(view);
        return view;
    }

    private void checkNative() {
        load.setEnabled(false);
        status.setText(R.string.cloudy_stream_running);
        File directory = new File(getFilesDir(), "native-load");
        // Native class initialization is JVM-once. Do not call MoonBridge.init a
        // second time: the upstream initializer owns its process-global references.
        new Thread(() -> {
            String failure = null;
            try {
                synchronized (EVIDENCE_LOCK) {
                    if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("evidence-directory");
                    String[] existing = directory.list();
                    if (existing == null || existing.length >= 16) throw new IllegalStateException("evidence-capacity");
                    long start = SystemClock.elapsedRealtimeNanos();
                    String metadata = null;
                    try {
                        metadata = MoonBridge.getLaunchUrlQueryParameters();
                        if (metadata == null || metadata.length() > 4096) throw new IllegalStateException("native-metadata-bounds");
                    } catch (Exception | LinkageError error) {
                        failure = error.getClass().getSimpleName();
                    }
                    JSONObject result = new JSONObject();
                    result.put("schemaVersion", 1);
                    result.put("buildRef", BuildConfig.QUALIFICATION_BUILD_REF);
                    result.put("nativeBuildRef", BuildConfig.NATIVE_BUILD_REF);
                    result.put("version", BuildConfig.VERSION_NAME);
                    result.put("status", failure == null ? "native-load-and-metadata-passed" : "native-load-failed");
                    result.put("failureClass", failure == null ? JSONObject.NULL : failure);
                    result.put("durationNanos", SystemClock.elapsedRealtimeNanos() - start);
                    result.put("nativeMetadata", failure == null ? metadata : JSONObject.NULL);
                    result.put("hostConnections", 0);
                    result.put("decodedFrames", 0);
                    File target = new File(directory, UUID.randomUUID() + ".json");
                    if (target.exists()) throw new IllegalStateException("evidence-collision");
                    // Atomic publication preserves both native success and failure.
                    // A storage failure never becomes a native-success UI result.
                    AtomicFile record = new AtomicFile(target);
                    FileOutputStream stream = null;
                    try {
                        stream = record.startWrite();
                        byte[] encoded = (result.toString(2) + "\n").getBytes(StandardCharsets.UTF_8);
                        stream.write(encoded);
                        record.finishWrite(stream);
                        if (!Arrays.equals(Files.readAllBytes(target.toPath()), encoded)) {
                            throw new IllegalStateException("evidence-publication");
                        }
                    } catch (Exception writeFailure) {
                        record.failWrite(stream);
                        throw writeFailure;
                    }
                }
            } catch (Exception | LinkageError error) {
                // No remote content or endpoint is involved; display only the class.
                failure = error.getClass().getSimpleName();
            }
            String outcome = failure;
            runOnUiThread(() -> {
                if (destroyed) return;
                if (outcome == null) status.setText(R.string.cloudy_stream_success);
                else status.setText(getString(R.string.cloudy_stream_failed, outcome));
            });
        }, "stream-native-load").start();
    }

    @Override public void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }
}
