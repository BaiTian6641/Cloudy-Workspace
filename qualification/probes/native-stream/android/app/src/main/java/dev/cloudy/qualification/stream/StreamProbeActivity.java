package dev.cloudy.qualification.stream;

import android.app.AlertDialog;
import android.Manifest;
import android.os.Build;
import android.text.InputType;
import android.text.InputFilter;
import android.view.WindowManager;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import com.limelight.R;

/** Native diagnostic controls; local identity approval stays in the system prompt. */
public final class StreamProbeActivity extends FragmentActivity {
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private StreamProbeViewModel model;
    private BiometricPrompt prompt;
    private TextView status;
    private TextView identity;
    private TextView nativeStatus;
    private Button create;
    private Button authorize;
    private Button authorizeCredential;
    private Button refresh;
    private Button cancel;
    private Button delete;
    private Button load;
    private Button pair;
    private EditText pairingPin;
    private LinearLayout previewContainer;
    private PlaybackSurfaceOwner previewSurface;
    private TextView playbackStatus;
    private Button preview, cleanup, reconcile, catalog;
    private LinearLayout catalogList;
    private TextView catalogStatus;
    private java.util.List<CatalogEntry> renderedCatalog;
    private long renderedCatalogGeneration;
    private boolean idle, readyIdentity, restartRequired;
    private int selectedAppId;
    private long cancelledPromptToken;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        model = new ViewModelProvider(this).get(StreamProbeViewModel.class);
        // Reconstruct on every creation to replace AndroidX's callback for an
        // existing prompt. Never call authenticate() just to reattach it.
        prompt = newPrompt(model.promptTokenForReattach());
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, padding, padding, padding);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(padding + safe.left, padding + safe.top,
                    padding + safe.right, padding + safe.bottom);
            return insets;
        });
        label(content, R.string.cloudy_stream_title).setTextSize(22);
        label(content, R.string.cloudy_stream_scope);
        nativeStatus = label(content, R.string.cloudy_stream_ready);
        load = button(content, R.string.cloudy_stream_load, view -> model.loadNative());
        label(content, R.string.cloudy_identity_title).setTextSize(20);
        label(content, R.string.cloudy_identity_help);
        identity = label(content, R.string.cloudy_identity_ready);
        status = label(content, R.string.cloudy_identity_ready);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        create = button(content, R.string.cloudy_identity_create, view -> model.createIdentity());
        authorize = button(content, R.string.cloudy_identity_authorize, view -> model.authorizeIdentity());
        authorizeCredential = button(content, R.string.cloudy_identity_authorize_credential, view -> model.authorizeWithCredential());
        label(content,R.string.cloudy_pair_help);
        pairingPin=new EditText(this);
        pairingPin.setHint(R.string.cloudy_pair_pin);
        pairingPin.setContentDescription(getString(R.string.cloudy_pair_pin));
        pairingPin.setSingleLine(true);
        pairingPin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pairingPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        pairingPin.setSaveEnabled(false);
        pairingPin.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        pairingPin.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        content.addView(pairingPin);
        button(content,R.string.cloudy_pair_permission,view -> {
            if (Build.VERSION.SDK_INT>=37) requestPermissions(new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK},17);
        });
        pair=button(content,R.string.cloudy_pair_start,view -> {
            char[] pin=new char[pairingPin.length()];
            pairingPin.getText().getChars(0,pin.length,pin,0);
            pairingPin.getText().clear();
            model.pair(pin);
        });
        label(content,R.string.cloudy_catalog_help);
        catalog=button(content,R.string.cloudy_catalog_read,view -> model.readCatalog());
        catalogStatus=label(content,R.string.cloudy_catalog_ready);
        catalogList=new LinearLayout(this);catalogList.setOrientation(LinearLayout.VERTICAL);content.addView(catalogList);
        label(content,R.string.cloudy_play_title).setTextSize(20);
        label(content,R.string.cloudy_play_help);
        previewContainer=new LinearLayout(this);
        content.addView(previewContainer,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,dp(240)));
        installPreview();
        playbackStatus=label(content,R.string.cloudy_play_ready);
        preview=button(content,R.string.cloudy_play_start,view -> model.preview(previewSurface));
        cleanup=button(content,R.string.cloudy_play_retry,view -> model.retryPlaybackCleanup());
        reconcile=button(content,R.string.cloudy_network_reconcile,view -> new AlertDialog.Builder(this)
                .setTitle(R.string.cloudy_network_reconcile)
                .setMessage(R.string.cloudy_network_reconcile_warning)
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.cloudy_network_reconcile_confirm,(dialog,which) -> model.reconcileNetworkEvidence())
                .show());
        refresh = button(content, R.string.cloudy_identity_refresh, view -> model.refreshIdentity());
        cancel = button(content, R.string.cloudy_identity_cancel, view -> {
            model.cancel(); // Revoke the owner before cancelling framework authentication.
            prompt.cancelAuthentication();
        });
        delete = button(content, R.string.cloudy_identity_delete, view -> new AlertDialog.Builder(this)
                .setTitle(R.string.cloudy_identity_delete)
                .setMessage(R.string.cloudy_identity_delete_warning)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.cloudy_identity_delete_confirm, (dialog, which) -> model.deleteIdentity())
                .show());
        button(content, R.string.cloudy_stream_close, view -> {
            model.cancel(); prompt.cancelAuthentication(); finish();
        });
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        content.requestApplyInsets();
        model.state().observe(this, this::render);
    }

    private BiometricPrompt newPrompt(long token) {
        return new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        long authenticatedAt = SystemClock.elapsedRealtime();
                        model.authenticationSucceeded(token, authenticatedAt, result.getAuthenticationType());
                    }
                    @Override public void onAuthenticationError(int errorCode, CharSequence ignoredMessage) {
                        model.authenticationError(token, errorCode);
                    }
                    @Override public void onAuthenticationFailed() { model.authenticationFailed(token); }
                });
    }
    private void render(StreamProbeViewModel.UiState state) {
        selectedAppId=state.selectedAppId;idle=!state.busy; readyIdentity=state.identityState.equals("READY"); restartRequired=state.restartRequired;
        if (idle && !restartRequired && previewContainer.getChildCount()==0) installPreview();
        playbackStatus.setText(state.playbackStatus.isEmpty() ? getString(R.string.cloudy_play_ready) : state.playbackStatus);
        preview.setEnabled(idle && readyIdentity && !restartRequired && state.selectedAppId>0 && previewSurface!=null && previewSurface.ready());
        catalog.setEnabled(idle && readyIdentity);
        catalogStatus.setText(getString(R.string.cloudy_catalog_selection,state.catalogEntries.size(),state.selectedAppId));
        if (renderedCatalog!=state.catalogEntries || renderedCatalogGeneration!=state.catalogGeneration) {
            renderedCatalog=state.catalogEntries;renderedCatalogGeneration=state.catalogGeneration;
            catalogList.removeAllViews();
            final long generation=state.catalogGeneration;
            for (CatalogEntry entry:state.catalogEntries) {
                Button choice=button(catalogList,R.string.cloudy_catalog_choose,view -> model.selectCatalogApp(generation,entry.id));
                choice.setText(getString(R.string.cloudy_catalog_entry,entry.id,entry.title));
            }
        }
        for (int i=0;i<catalogList.getChildCount();i++) catalogList.getChildAt(i).setEnabled(idle);
        cleanup.setEnabled(state.cleanupRetry);
        reconcile.setEnabled(idle);
        identity.setText(getString(R.string.cloudy_identity_state, state.identityState, state.securityLevel));
        status.setText(getString(R.string.cloudy_identity_outcome, getString(state.message), state.detail));
        nativeStatus.setText(state.nativeMessage == R.string.cloudy_stream_failed
                ? getString(state.nativeMessage, state.detail) : getString(state.nativeMessage));
        create.setEnabled(!state.busy && state.identityState.equals("ABSENT"));
        authorize.setEnabled(!state.busy && (state.identityState.equals("READY") || state.identityState.equals("ENROLLMENT_PENDING")));
        authorizeCredential.setEnabled(!state.busy && (state.identityState.equals("READY") || state.identityState.equals("ENROLLMENT_PENDING")));
        pair.setEnabled(!state.busy && state.identityState.equals("READY"));
        pairingPin.setEnabled(!state.busy);
        refresh.setEnabled(!state.busy);
        delete.setEnabled(!state.busy);
        cancel.setEnabled(state.busy);
        load.setEnabled(!state.busy && !state.nativeTried);
        if (state.cancelPrompt && cancelledPromptToken != state.promptToken) {
            cancelledPromptToken = state.promptToken;
            prompt.cancelAuthentication();
        } else if (state.promptRequested && model.claimPrompt(state.promptToken)) {
            long token = state.promptToken;
            int authenticators = state.promptMode == StreamProbeViewModel.PromptMode.DEVICE_CREDENTIAL
                    ? BiometricManager.Authenticators.DEVICE_CREDENTIAL : AUTHENTICATORS;
            if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
                model.promptLaunchFailed(token);
                return;
            }
            prompt = newPrompt(token);
            try {
                prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.cloudy_identity_prompt_title))
                        .setSubtitle(getString(state.catalog ? R.string.cloudy_catalog_prompt_subtitle : state.playback ? R.string.cloudy_play_prompt_subtitle : state.pairing ? R.string.cloudy_pair_prompt_subtitle : state.promptMode == StreamProbeViewModel.PromptMode.DEVICE_CREDENTIAL
                                ? R.string.cloudy_identity_credential_subtitle : R.string.cloudy_identity_prompt_subtitle))
                        .setAllowedAuthenticators(authenticators).build());
            } catch (IllegalArgumentException | IllegalStateException unavailable) {
                model.promptLaunchFailed(token);
            }
        }
    }
    private void installPreview() {
        TextureView textureView=new TextureView(this);
        textureView.setContentDescription(getString(R.string.cloudy_play_surface));
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            private PlaybackSurfaceOwner owner;
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height) {
                owner=new PlaybackSurfaceOwner(StreamProbeActivity.this,texture,() -> previewContainer.removeView(textureView));
                previewSurface=owner;
                if (preview!=null) preview.setEnabled(idle && readyIdentity && !restartRequired && selectedAppId>0);
            }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                // Returning false transfers the real texture to the retained owner.
                // A detach request alone never permits release while the codec can use it.
                PlaybackSurfaceOwner destroyed=owner;
                if (previewSurface==destroyed) previewSurface=null;
                if (preview!=null) preview.setEnabled(false);
                if (destroyed!=null) model.surfaceDestroyed(destroyed);
                return false;
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height) { }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
        });
        previewContainer.addView(textureView,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT));
    }
    private TextView label(LinearLayout parent, int resource) {
        TextView view = new TextView(this);
        view.setText(resource);
        view.setTextSize(16);
        view.setPadding(0, dp(12), 0, dp(12));
        parent.addView(view);
        return view;
    }
    private Button button(LinearLayout parent, int resource, View.OnClickListener listener) {
        Button view = new Button(this);
        view.setText(resource);
        view.setOnClickListener(listener);
        parent.addView(view);
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onStart() { super.onStart(); model.onVisible(); }
    @Override protected void onStop() {
        // The native prompt may take window focus. Focus alone never revokes it.
        // A real background stop does; configuration recreation only reattaches.
        if (!isChangingConfigurations()) {
            model.onHidden();
            pairingPin.getText().clear();
            prompt.cancelAuthentication();
        }
        super.onStop();
    }
}
