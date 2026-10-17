package dev.cloudy.qualification.x11broker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.text.InputType;
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** One native XID rendered into one Android view; no root-desktop cropping or shared XID authority. */
public final class XWindowView extends View {
    private final int xid;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Object bitmapLock = new Object();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cloudy-x11-view");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService inputWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cloudy-x11-input");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running = true;
    private volatile Bitmap bitmap;
    private volatile String status = "Waiting for X11 image";
    private long broker;
    private Runnable endAction;
    private int bitmapWidth;
    private int bitmapHeight;
    private final RectF destination = new RectF();

    public XWindowView(Context context) { this(context, 0); }

    public XWindowView(Context context, int xid) {
        super(context);
        this.xid = xid;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setContentDescription("Remote X11 window " + xid);
        if (!isInEditMode()) {
            broker = X11Environment.openBroker();
            worker.execute(this::renderLoop);
        } else {
            running = false;
            status = "X11 layout preview";
        }
    }

    private void renderLoop() {
        int[] size = new int[2];
        while (running && broker != 0) {
            try {
                if (bitmap != null && !NativeBroker.viewable(broker, xid)) {
                    Runnable action = endAction;
                    if (action != null) post(action);
                    return;
                }
                int[] geometry = NativeBroker.geometry(broker, xid);
                if (geometry != null) {
                    size[0] = geometry[2];
                    size[1] = geometry[3];
                    boolean rendered = false;
                    synchronized (bitmapLock) {
                        if (bitmap == null || bitmapWidth != size[0] || bitmapHeight != size[1]) {
                            bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
                            bitmapWidth = size[0];
                            bitmapHeight = size[1];
                        }
                        rendered = NativeBroker.render(broker, xid, bitmap);
                    }
                    if (rendered) {
                        status = null;
                        postInvalidate();
                    } else {
                        status = "X11 image unavailable";
                    }
                } else {
                    status = "X11 window ended";
                }
            } catch (RuntimeException failure) {
                status = "X11 view stopped";
            }
            try { TimeUnit.MILLISECONDS.sleep(100); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xff111714);
        Bitmap current = bitmap;
        if (current != null && status == null) {
            float scale = Math.min((float) getWidth() / current.getWidth(),
                (float) getHeight() / current.getHeight());
            float width = current.getWidth() * scale;
            float height = current.getHeight() * scale;
            destination.set((getWidth() - width) / 2, (getHeight() - height) / 2,
                (getWidth() + width) / 2, (getHeight() + height) / 2);
            synchronized (bitmapLock) { canvas.drawBitmap(current, null, destination, paint); }
        } else {
            String message = status == null ? "Waiting" : status;
            paint.setTextSize(28);
            paint.setColor(0xffe8e6df);
            canvas.drawText(message, 24, 48, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            requestFocus();
            android.util.Log.i("CloudyX11Broker", "Android MotionEvent received for XID " + xid);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP ||
            event.getAction() == MotionEvent.ACTION_MOVE) {
            float scaleX = bitmapWidth == 0 || destination.width() == 0 ? 1 : bitmapWidth / destination.width();
            float scaleY = bitmapHeight == 0 || destination.height() == 0 ? 1 : bitmapHeight / destination.height();
            int x = Math.round((event.getX() - destination.left) * scaleX);
            int y = Math.round((event.getY() - destination.top) * scaleY);
            int button = event.getAction() == MotionEvent.ACTION_MOVE ? 0 : 1;
            boolean down = event.getAction() == MotionEvent.ACTION_DOWN;
            inputWorker.execute(() -> {
                X11Environment.pointer(xid, x, y, button, down);
            });
            if (event.getAction() == MotionEvent.ACTION_UP) performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) inputWorker.execute(() -> NativeBroker.focusWindow(broker, xid));
    }

    @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_NONE;
        return new BaseInputConnection(this, false) {
            @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                inputWorker.execute(() -> X11Environment.text(text.toString()));
                return true;
            }
        };
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isLocalKey(keyCode)) return super.onKeyDown(keyCode, event);
        inputWorker.execute(() -> X11Environment.key(event.getScanCode(), keyCode, true));
        return true;
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isLocalKey(keyCode)) return super.onKeyUp(keyCode, event);
        inputWorker.execute(() -> X11Environment.key(event.getScanCode(), keyCode, false));
        return true;
    }

    private boolean isLocalKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH || keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
    }

    public void requestDelete() {
        inputWorker.execute(() -> NativeBroker.requestDelete(broker, xid));
    }

    public String currentStatus() { return status; }

    public void setEndAction(Runnable action) { endAction = action; }

    public void close() {
        running = false;
        worker.shutdownNow();
        inputWorker.shutdownNow();
        if (broker != 0) NativeBroker.close(broker);
        broker = 0;
        synchronized (bitmapLock) {
            if (bitmap != null) bitmap.recycle();
            bitmap = null;
        }
    }
}
