package dev.cloudy.qualification.vnc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.util.HashMap;
import java.util.Map;

// Created only by VncActivity, never XML-inflated: a real retained session owner is required.
@SuppressLint("ViewConstructor")
public final class VncFrameView extends View {
    private final VncSession session;
    private final RectF destination=new RectF();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer,Integer> heldKeys=new HashMap<>();
    private final VncPointerState buttons=new VncPointerState();
    private final VncWheel wheel=new VncWheel();
    private VncSnapshot state;
    private int lastX,lastY;
    private boolean touching;
    public VncFrameView(Context context,VncSession session) {
        super(context); this.session=session; state=session.snapshot();
        setFocusable(true); setFocusableInTouchMode(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setContentDescription(context.getString(R.string.vnc_frame_description));
        paint.setColor(Color.WHITE);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,16,getResources().getDisplayMetrics()));
    }
    public void update(VncSnapshot value) {
        if (state.connected && !value.connected) clearOwnership(false);
        state=value; invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(Color.rgb(22,26,32));
        if (state.width>0 && state.height>0) {
            float scale=Math.min((float)getWidth()/state.width,(float)getHeight()/state.height);
            float width=state.width*scale,height=state.height*scale;
            destination.set((getWidth()-width)/2,(getHeight()-height)/2,(getWidth()+width)/2,(getHeight()+height)/2);
            if (session.draw(canvas,destination)) return;
        }
        destination.setEmpty(); canvas.drawText(getContext().getString(R.string.vnc_waiting_phase,state.detail),16,32,paint);
    }
    private boolean ready() { return isFocused() && hasWindowFocus() && state.connected; }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code=event.getKeyCode(); Integer held=heldKeys.get(code);
        boolean f8=code==KeyEvent.KEYCODE_F8;
        if (f8 && (event.getAction()==KeyEvent.ACTION_DOWN || event.getAction()==KeyEvent.ACTION_UP)) {
            session.marker(event.getAction()==KeyEvent.ACTION_DOWN ? VncDiagnostics.Marker.DOWN_SEEN : VncDiagnostics.Marker.UP_SEEN);
            if (event.getAction()==KeyEvent.ACTION_UP && held==null) session.marker(VncDiagnostics.Marker.UP_UNOWNED);
            if (event.getAction()==KeyEvent.ACTION_DOWN) {
                if (!isFocused()) session.marker(VncDiagnostics.Marker.REJECT_FOCUS);
                if (!hasWindowFocus()) session.marker(VncDiagnostics.Marker.REJECT_WINDOW);
                if (!state.connected) session.marker(VncDiagnostics.Marker.REJECT_CONNECTION);
                if (event.isMetaPressed()) session.marker(VncDiagnostics.Marker.REJECT_META);
            }
        }
        // Pair an owned UP before gates: native fields and window focus may have changed.
        if (event.getAction()==KeyEvent.ACTION_UP && held!=null) {
            heldKeys.remove(code); boolean queued=session.key(held,false);
            if (f8) session.marker(queued ? VncDiagnostics.Marker.UP_QUEUED : VncDiagnostics.Marker.QUEUE_REJECTED);
            return true;
        }
        if (event.getAction()!=KeyEvent.ACTION_DOWN || !ready() || event.isMetaPressed()) return super.dispatchKeyEvent(event);
        Integer symbol=held!=null ? held : VncKeyMap.special(code);
        if (symbol==null) {
            int meta=event.getMetaState() & ~(KeyEvent.META_CTRL_ON|KeyEvent.META_CTRL_LEFT_ON|KeyEvent.META_CTRL_RIGHT_ON);
            symbol=VncKeyMap.unicode(event.getUnicodeChar(meta));
        }
        if (symbol==null || heldKeys.size()>=64) return super.dispatchKeyEvent(event);
        if (!session.key(symbol,true)) {
            if (f8) session.marker(VncDiagnostics.Marker.QUEUE_REJECTED);
            return super.dispatchKeyEvent(event);
        }
        if (f8) session.marker(VncDiagnostics.Marker.DOWN_QUEUED);
        heldKeys.put(code,symbol); return true;
    }
    private boolean position(MotionEvent event,boolean outside) {
        if (destination.isEmpty() || state.width<=0 || state.height<=0) return false;
        if (!outside && !destination.contains(event.getX(),event.getY())) return false;
        lastX=Math.max(0,Math.min(state.width-1,(int)((event.getX()-destination.left)*state.width/destination.width())));
        lastY=Math.max(0,Math.min(state.height-1,(int)((event.getY()-destination.top)*state.height/destination.height())));
        return true;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return mouse(event);
        int action=event.getActionMasked();
        if (action==MotionEvent.ACTION_CANCEL || event.getPointerCount()>1) {
            if (touching) session.pointer(lastX,lastY,0); touching=false; return true;
        }
        if (action==MotionEvent.ACTION_UP) {
            if (!touching) return false;
            position(event,true); touching=false; session.pointer(lastX,lastY,0); performClick(); return true;
        }
        if (action==MotionEvent.ACTION_DOWN && state.connected && hasWindowFocus() && position(event,false)) {
            requestFocus(); touching=session.pointer(lastX,lastY,1); return touching;
        }
        if (action==MotionEvent.ACTION_MOVE && touching && ready() && position(event,true)) return session.pointer(lastX,lastY,1);
        return false;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return mouse(event);
        return super.onGenericMotionEvent(event);
    }
    private boolean mouse(MotionEvent event) {
        int action=event.getActionMasked(), before=buttons.mask();
        if (action==MotionEvent.ACTION_CANCEL) { clearOwnership(true); return true; }
        if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_BUTTON_RELEASE) {
            int after=buttons.update(event.getButtonState());
            if (before!=after) { position(event,true); session.pointer(lastX,lastY,after); return true; }
            return before!=0;
        }
        boolean press=action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_BUTTON_PRESS;
        if (press && state.connected && hasWindowFocus() && position(event,false)) requestFocus();
        if (!ready() || !position(event,before!=0)) return false;
        if (press) {
            int physical=event.getButtonState();
            int changed=event.getActionButton();
            if (changed==0 && action==MotionEvent.ACTION_DOWN) changed=MotionEvent.BUTTON_PRIMARY;
            int after=buttons.press(changed,physical);
            if (after==before) return true; // Touch/generic duplicate must not create a second click.
            return session.pointer(lastX,lastY,after);
        }
        if (action==MotionEvent.ACTION_MOVE || action==MotionEvent.ACTION_HOVER_MOVE) {
            return session.pointer(lastX,lastY,buttons.update(event.getButtonState()));
        }
        if (action==MotionEvent.ACTION_SCROLL) {
            float vertical=event.getAxisValue(MotionEvent.AXIS_VSCROLL), horizontal=event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            int[] steps=wheel.add(vertical,horizontal);
            if (steps==null) return false;
            int v=Math.abs(steps[0]),h=Math.abs(steps[1]);
            for (int i=0;i<v;i++) { if (!session.pointer(lastX,lastY,before|(steps[0]>0 ? 8:16)) || !session.pointer(lastX,lastY,before)) return false; }
            for (int i=0;i<h;i++) { if (!session.pointer(lastX,lastY,before|(steps[1]>0 ? 64:32)) || !session.pointer(lastX,lastY,before)) return false; }
            return true;
        }
        return false;
    }
    private void clearOwnership(boolean release) {
        heldKeys.clear(); buttons.clear(); wheel.clear(); touching=false;
        if (release) session.releaseInput();
    }
    @Override protected void onFocusChanged(boolean gain,int direction,android.graphics.Rect previous) {
        super.onFocusChanged(gain,direction,previous); if (!gain) clearOwnership(true);
    }
    @Override public void onWindowFocusChanged(boolean gain) { super.onWindowFocusChanged(gain); if (!gain) clearOwnership(true); }
}
