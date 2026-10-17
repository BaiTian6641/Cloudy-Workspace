package dev.cloudy.qualification.x11broker;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Finite diagnostic controller; it creates no product-support claim. */
public final class BrokerActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Integer> launched = ConcurrentHashMap.newKeySet();
    private TextView status;
    private LinearLayout windows;
    private boolean refreshing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new java.io.File(getFilesDir(), "ssh-fixture").mkdirs();
        setTitle("Cloudy X11 broker probe");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setPadding(24, 20, 24, 20);
        status.setText(X11Environment.isRunning() ? R.string.status_running : R.string.status_stopped);
        root.addView(status);
        LinearLayout buttons = new LinearLayout(this);
        addButton(buttons, "Start server", this::startServer);
        addButton(buttons, "Start two-window demo", this::startDemo);
        addButton(buttons, "Start SSH X11 apps", this::startSsh);
        addButton(buttons, "Start VS Code + xterm", this::startVsCode);
        addButton(buttons, "Stop SSH", ignored -> worker.execute(SshX11Controller::stop));
        addButton(buttons, "Stop demo", ignored -> { X11Environment.stopDemo(); launched.clear(); });
        addButton(buttons, "Stop server", this::stopServer);
        root.addView(buttons);
        windows = new LinearLayout(this);
        windows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(windows);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void addButton(LinearLayout parent, String label, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(action);
        parent.addView(button);
    }

    private void startServer(View view) {
        status.setText(R.string.status_preparing);
        worker.execute(() -> {
            try {
                String result = X11Environment.start(getApplicationContext());
                main.post(() -> { status.setText(result); refreshWindows(); });
            } catch (Exception failure) {
                String type = failure.getClass().getSimpleName();
                main.post(() -> status.setText(getString(R.string.status_start_failed, type)));
            }
        });
    }

    private void startDemo(View view) {
        worker.execute(() -> {
            try {
                boolean started = X11Environment.startDemo();
                main.post(() -> {
                    status.setText(started ? getString(R.string.status_demo_admitted) : getString(R.string.status_demo_running));
                    if (started) main.postDelayed(this::openAllWindows, 500);
                    refreshWindows();
                });
            } catch (RuntimeException failure) {
                main.post(() -> status.setText(getString(R.string.status_demo_failed, failure.getClass().getSimpleName())));
            }
        });
    }

    private void startSsh(View view) {
        worker.execute(() -> {
            try {
                String result = SshX11Controller.start(getApplicationContext());
                main.post(() -> {
                    status.setText(result);
                    main.postDelayed(this::openAllWindows, 1500);
                    main.postDelayed(() -> {
                        int[] snapshot = X11Environment.listWindows();
                        android.util.Log.i("CloudyX11Broker", "SSH active channels=" +
                            SshX11Controller.activeChannels() + " brokerRecords=" +
                            (snapshot == null ? -1 : snapshot.length / 8));
                    }, 5000);
                    refreshWindows();
                });
            } catch (Exception failure) {
                android.util.Log.e("CloudyX11Broker", "SSH X11 startup failed", failure);
                main.post(() -> status.setText(getString(R.string.status_demo_failed,
                    failure.getClass().getSimpleName())));
            }
        });
    }

    private void startVsCode(View view) {
        worker.execute(() -> {
            try {
                String result = SshX11Controller.startVsCode(getApplicationContext());
                main.post(() -> {
                    status.setText(result);
                    main.postDelayed(this::openAllWindows, 1500);
                    refreshWindows();
                });
            } catch (Exception failure) {
                main.post(() -> status.setText(getString(R.string.status_demo_failed,
                    failure.getClass().getSimpleName())));
            }
        });
    }

    private void stopServer(View view) {
        worker.execute(() -> {
            X11Environment.stop();
            launched.clear();
            main.post(() -> { status.setText(R.string.status_stopped); windows.removeAllViews(); });
        });
    }

    private void openAllWindows() {
        if (!X11Environment.isRunning()) return;
        int[] records = X11Environment.listWindows();
        if (records == null) return;
        for (int index = 0; index + 7 < records.length; index += 8) {
            int xid = records[index];
            int parent = records[index + 1];
            int overrideRedirect = records[index + 2];
            if (parent != 0) {
                XWindowActivity.showTransient(parent, xid);
            } else if (overrideRedirect == 0 && launched.add(xid)) {
                XWindowActivity.open(this, xid);
            }
        }
    }

    private void refreshWindows() {
        if (refreshing || !X11Environment.isRunning()) return;
        refreshing = true;
        worker.execute(() -> {
            final int[] records = loadRecords();
            openAllWindows();
            main.post(() -> {
                refreshing = false;
                windows.removeAllViews();
                if (records == null) return;
                for (int index = 0; index + 7 < records.length; index += 8) {
                    int xid = records[index];
                    int parent = records[index + 1];
                    int overrideRedirect = records[index + 2];
                    int x = records[index + 3], y = records[index + 4];
                    int width = records[index + 5], height = records[index + 6];
                    TextView row = new TextView(this);
                    String kind = parent != 0 ? getString(R.string.window_transient, parent) :
                        overrideRedirect != 0 ? getString(R.string.window_override) : getString(R.string.window_top_level);
                    row.setText(getString(R.string.window_record, xid, width, height, x, y, kind));
                    row.setPadding(24, 12, 24, 12);
                    windows.addView(row);
                }
                if (X11Environment.isRunning()) main.postDelayed(this::refreshWindows, 1000);
            });
        });
    }

    private int[] loadRecords() {
        try { return X11Environment.listWindows(); }
        catch (RuntimeException failure) { return null; }
    }

    @Override protected void onResume() {
        super.onResume();
        main.postDelayed(this::refreshWindows, 250);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
