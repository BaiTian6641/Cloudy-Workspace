package dev.cloudy.qualification.x11broker;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Independent native document task for one managed X11 top-level window. */
public final class XWindowActivity extends Activity {
    private static final String EXTRA_XID = "dev.cloudy.qualification.x11broker.XID";
    private static final Map<Integer, WeakReference<XWindowActivity>> WINDOWS = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<Integer>> PENDING = new ConcurrentHashMap<>();

    private int xid;
    private XWindowView content;
    private final Map<Integer, Dialog> dialogs = new ConcurrentHashMap<>();

    public static void open(Context context, int xid) {
        Intent intent = new Intent(context, XWindowActivity.class);
        intent.putExtra(EXTRA_XID, xid);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivity(intent);
    }

    public static boolean showTransient(int parentXid, int dialogXid) {
        WeakReference<XWindowActivity> reference = WINDOWS.get(parentXid);
        XWindowActivity parent = reference == null ? null : reference.get();
        if (parent == null) {
            PENDING.computeIfAbsent(parentXid, ignored -> new HashSet<>()).add(dialogXid);
            return false;
        }
        Set<Integer> pending = PENDING.remove(parentXid);
        if (pending != null) pending.remove(dialogXid);
        parent.runOnUiThread(() -> parent.showOwnedDialog(dialogXid));
        return true;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        xid = getIntent().getIntExtra(EXTRA_XID, 0);
        if (xid == 0) { finish(); return; }
        setTitle("Cloudy X11 • local probe / XID " + xid);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.HORIZONTAL);
        TextView identity = new TextView(this);
        identity.setText(getString(R.string.window_identity, xid));
        identity.setTextSize(15);
        identity.setPadding(20, 16, 20, 16);
        chrome.addView(identity, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button close = new Button(this);
        close.setText(R.string.wm_close);
        close.setOnClickListener(view -> content.requestDelete());
        chrome.addView(close);
        root.addView(chrome);
        content = new XWindowView(this, xid);
        content.setEndAction(this::finish);
        root.addView(content, new LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> content.requestDelete());
        WINDOWS.put(xid, new WeakReference<>(this));
        Set<Integer> pending = PENDING.remove(xid);
        if (pending != null) for (Integer dialogXid : pending) showOwnedDialog(dialogXid);
    }

    private void showOwnedDialog(int dialogXid) {
        if (dialogs.containsKey(dialogXid)) {
            Dialog existing = dialogs.get(dialogXid);
            if (existing != null) existing.show();
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.setTitle("Owned X11 dialog " + dialogXid + " • parent " + xid);
        XWindowView view = new XWindowView(this, dialogXid);
        view.setEndAction(dialog::dismiss);
        dialog.setContentView(view);
        dialog.setOnDismissListener(ignored -> { view.close(); dialogs.remove(dialogXid); });
        dialogs.put(dialogXid, dialog);
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(640, 480);
    }

    @Override protected void onDestroy() {
        WINDOWS.remove(xid);
        for (Dialog dialog : dialogs.values()) dialog.dismiss();
        dialogs.clear();
        if (content != null) content.close();
        super.onDestroy();
    }
}
