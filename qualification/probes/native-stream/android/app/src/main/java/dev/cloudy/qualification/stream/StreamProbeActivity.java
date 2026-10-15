package dev.cloudy.qualification.stream;

import android.app.AlertDialog;
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
    private long cancelledPromptToken;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
        identity.setText(getString(R.string.cloudy_identity_state, state.identityState, state.securityLevel));
        status.setText(getString(R.string.cloudy_identity_outcome, getString(state.message), state.detail));
        nativeStatus.setText(state.nativeMessage == R.string.cloudy_stream_failed
                ? getString(state.nativeMessage, state.detail) : getString(state.nativeMessage));
        create.setEnabled(!state.busy && state.identityState.equals("ABSENT"));
        authorize.setEnabled(!state.busy && (state.identityState.equals("READY") || state.identityState.equals("ENROLLMENT_PENDING")));
        authorizeCredential.setEnabled(!state.busy && (state.identityState.equals("READY") || state.identityState.equals("ENROLLMENT_PENDING")));
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
                        .setSubtitle(getString(state.promptMode == StreamProbeViewModel.PromptMode.DEVICE_CREDENTIAL
                                ? R.string.cloudy_identity_credential_subtitle : R.string.cloudy_identity_prompt_subtitle))
                        .setAllowedAuthenticators(authenticators).build());
            } catch (IllegalArgumentException | IllegalStateException unavailable) {
                model.promptLaunchFailed(token);
            }
        }
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
            prompt.cancelAuthentication();
        }
        super.onStop();
    }
}
