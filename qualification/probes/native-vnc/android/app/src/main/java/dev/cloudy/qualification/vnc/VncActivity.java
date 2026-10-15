package dev.cloudy.qualification.vnc;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Framework desktop diagnostic; credentials stay in native fields and session memory. */
public final class VncActivity extends Activity {
    private VncSession session;
    private VncFrameView frame;
    private TextView status;
    private EditText username,password;
    private Button connect,load,cancel,permission;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        Object retained=getLastNonConfigurationInstance();
        session=retained instanceof VncSession ? (VncSession)retained : new VncSession(getApplicationContext());
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        ScrollView scroll=new ScrollView(this);
        LinearLayout controls=new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL); controls.setPadding(16,8,16,8);
        TextView title=new TextView(this); title.setText(R.string.app_name); title.setTextSize(20); controls.addView(title);
        TextView explanation=new TextView(this); explanation.setText(R.string.vnc_explanation); controls.addView(explanation);
        status=new TextView(this); status.setTextIsSelectable(true); controls.addView(status);
        username=field(R.string.vnc_username,false); password=field(R.string.vnc_password,true);
        controls.addView(username); controls.addView(password);
        permission=button(R.string.vnc_permission,() -> { if (Build.VERSION.SDK_INT>=37) requestPermissions(new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK},7); }); controls.addView(permission);
        load=button(R.string.vnc_load,session::load); controls.addView(load);
        connect=button(R.string.vnc_connect,() -> {
            byte[] user=null,pass=null;
            try {
                user=encode(username,256); pass=encode(password,1024);
                session.connect(user,pass); user=null; pass=null;
                username.getText().clear(); password.getText().clear(); frame.requestFocus();
            } catch (CharacterCodingException | IllegalArgumentException error) { status.setText(R.string.vnc_credentials_invalid); }
            finally { if (user!=null) Arrays.fill(user,(byte)0); if (pass!=null) Arrays.fill(pass,(byte)0); }
        }); controls.addView(connect);
        cancel=button(R.string.vnc_disconnect,session::disconnect); controls.addView(cancel);
        scroll.addView(controls); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        frame=new VncFrameView(this,session); root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); root.requestApplyInsets(); session.setListener(this::render); render();
    }
    private EditText field(int hint,boolean secret) {
        EditText field=new EditText(this); field.setHint(hint); field.setContentDescription(getString(hint)); field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT|(secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        field.setSaveEnabled(false); field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        return field;
    }
    private Button button(int label,Runnable action) { Button button=new Button(this); button.setText(label); button.setOnClickListener(view -> action.run()); return button; }
    private static byte[] encode(EditText field,int limit) throws CharacterCodingException {
        char[] characters=new char[field.length()]; field.getText().getChars(0,characters.length,characters,0);
        ByteBuffer buffer=null;
        try {
            buffer=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(characters));
            if (buffer.remaining()<1 || buffer.remaining()>limit) throw new IllegalArgumentException();
            byte[] bytes=new byte[buffer.remaining()]; buffer.get(bytes);
            for (byte value:bytes) if (value==0) { Arrays.fill(bytes,(byte)0); throw new IllegalArgumentException(); }
            return bytes;
        } finally { Arrays.fill(characters,'\0'); if (buffer!=null && buffer.hasArray()) Arrays.fill(buffer.array(),(byte)0); }
    }
    private boolean lanGranted() { return Build.VERSION.SDK_INT<37 || checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)==PackageManager.PERMISSION_GRANTED; }
    private void render() {
        VncSnapshot value=session.snapshot(); boolean lan=lanGranted();
        status.setText(getString(R.string.vnc_status,value.state,value.detail,value.width,value.height,value.callbacks,value.converted,value.drawn,value.admitted,value.completed,value.written));
        load.setEnabled(!value.busy); connect.setEnabled(!value.busy && lan); cancel.setEnabled(value.busy);
        username.setEnabled(!value.busy); password.setEnabled(!value.busy); permission.setEnabled(!lan);
        frame.update(value);
    }
    @Override public Object onRetainNonConfigurationInstance() { return session; }
    @Override protected void onResume() { super.onResume(); if (session!=null) { if (!lanGranted()) session.hidden(); render(); } }
    @Override protected void onStop() { if (!isChangingConfigurations()) { session.hidden(); username.getText().clear(); password.getText().clear(); } super.onStop(); }
    @Override protected void onDestroy() { session.setListener(null); if (!isChangingConfigurations()) session.close(); super.onDestroy(); }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) { super.onRequestPermissionsResult(requestCode,permissions,grants); render(); }
}
