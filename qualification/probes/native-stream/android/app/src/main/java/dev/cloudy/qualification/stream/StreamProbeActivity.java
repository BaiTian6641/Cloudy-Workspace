package dev.cloudy.qualification.stream;

import android.app.AlertDialog;
import android.Manifest;
import android.os.Build;
import android.text.InputType;
import android.text.InputFilter;
import android.view.WindowManager;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.graphics.Rect;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.os.Handler;
import android.os.Looper;
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
    private FrameLayout previewContainer;
    private LinearLayout root, presentationChrome;
    private TextureView previewTexture;
    private TextView phaseStatus,deadlineStatus,previewReason,textureStatus;
    private StreamProbeViewModel.UiState lastState;
    private final Handler uiHandler=new Handler(Looper.getMainLooper());
    private boolean foreground;
    private final Runnable deadlineTick=new Runnable() {
        @Override public void run() {
            if (!foreground) return;
            updateDeadline();uiHandler.postDelayed(this,1000);
        }
    };
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
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        int padding=dp(12);
        root.setPadding(padding,padding,padding,padding);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(padding+safe.left,padding+safe.top,padding+safe.right,padding+safe.bottom);
            return insets;
        });
        presentationChrome=new LinearLayout(this);presentationChrome.setOrientation(LinearLayout.VERTICAL);
        phaseStatus=label(presentationChrome,R.string.cloudy_presentation_idle);phaseStatus.setMaxLines(2);
        phaseStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        deadlineStatus=label(presentationChrome,R.string.cloudy_presentation_no_deadline);deadlineStatus.setMaxLines(1);
        textureStatus=label(presentationChrome,R.string.cloudy_presentation_texture_none);textureStatus.setMaxLines(1);
        previewReason=label(presentationChrome,R.string.cloudy_presentation_surface);previewReason.setMaxLines(2);
        root.addView(presentationChrome);
        // The render target never belongs to the controls scroller and is never reparented live.
        previewContainer=new FrameLayout(this);previewContainer.setBackgroundColor(android.graphics.Color.BLACK);
        root.addView(previewContainer,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0));
        installPreview();
        LinearLayout playbackActions=new LinearLayout(this);
        preview=new Button(this);preview.setText(R.string.cloudy_play_start);
        preview.setOnClickListener(view -> model.preview(previewSurface));
        playbackActions.addView(preview,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        cancel=button(playbackActions,R.string.cloudy_presentation_stop,view -> {
            model.cancel();prompt.cancelAuthentication();
        });
        root.addView(playbackActions,0); // Stop stays reachable even when a very short window cannot fit video.
        LinearLayout content = new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
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
        playbackStatus=label(content,R.string.cloudy_play_ready);
        cleanup=button(content,R.string.cloudy_play_retry,view -> model.retryPlaybackCleanup());
        reconcile=button(content,R.string.cloudy_network_reconcile,view -> new AlertDialog.Builder(this)
                .setTitle(R.string.cloudy_network_reconcile)
                .setMessage(R.string.cloudy_network_reconcile_warning)
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.cloudy_network_reconcile_confirm,(dialog,which) -> model.reconcileNetworkEvidence())
                .show());
        refresh = button(content, R.string.cloudy_identity_refresh, view -> model.refreshIdentity());
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
        root.addView(scroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        root.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob) -> sizePreview(playbackActions));
        presentationChrome.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob) -> sizePreview(playbackActions));
        playbackActions.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob) -> sizePreview(playbackActions));
        previewContainer.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob) -> fitTexture());
        setContentView(root);
        root.requestApplyInsets();
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
        lastState=state;
        selectedAppId=state.selectedAppId;idle=!state.busy; readyIdentity=state.identityState.equals("READY"); restartRequired=state.restartRequired;
        if (idle && !restartRequired && previewContainer.getChildCount()==0) installPreview();
        playbackStatus.setText(state.playbackStatus.isEmpty() ? getString(R.string.cloudy_play_ready) : state.playbackStatus);
        refreshPresentationControls();
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
    private void sizePreview(LinearLayout actions) {
        int inner=root.getHeight()-root.getPaddingTop()-root.getPaddingBottom();
        int chrome=presentationChrome.getMeasuredHeight()+actions.getMeasuredHeight();
        int height=PresentationLayout.paneHeight(inner,chrome,dp(72));
        if (previewContainer.getLayoutParams().height!=height) {
            previewContainer.getLayoutParams().height=height;previewContainer.requestLayout();
        }
        fitTexture();
    }
    private void fitTexture() {
        TextureView view=previewTexture;if (view==null) return;
        PresentationLayout.Size fit=PresentationLayout.contain(previewContainer.getWidth(),previewContainer.getHeight());
        android.view.ViewGroup.LayoutParams params=view.getLayoutParams();
        if (params.width!=fit.width || params.height!=fit.height) {
            params.width=fit.width;params.height=fit.height;view.setLayoutParams(params);
        }
        sample(view,ownerFor(view),PresentationTracker.Edge.VISIBILITY);
        refreshPresentationControls();
    }
    private void refreshPresentationControls() {
        StreamProbeViewModel.UiState state=lastState;if (state==null || preview==null) return;
        if (previewTexture!=null) sample(previewTexture,ownerFor(previewTexture),PresentationTracker.Edge.VISIBILITY);
        boolean surfaceReady=previewSurface!=null && previewSurface.ready();
        String blocker=PresentationAdmissionPolicy.blocker(state.admissionBlocker,state.busy,state.cleanupRetry,
                state.restartRequired,state.networkPermissionGranted,readyIdentity,state.selectedAppId,surfaceReady);
        boolean allowed="NONE".equals(blocker);
        preview.setEnabled(allowed);
        setTextIfChanged(preview,getString(state.restartRequired && !state.busy ? R.string.cloudy_presentation_restart_button : R.string.cloudy_play_start));
        setTextIfChanged(previewReason,getString(blockerText(blocker)));
        previewReason.setContentDescription(getString(blockerText(blocker)));
        if (previewSurface==null) setTextIfChanged(textureStatus,getString(R.string.cloudy_presentation_texture_none));
        else setTextIfChanged(textureStatus,previewSurface.presentationSummary());
        setTextIfChanged(phaseStatus,getString(R.string.cloudy_presentation_phase,phaseText(state.operationPhase),
                state.operationId.isEmpty() ? getString(R.string.cloudy_presentation_no_operation) : state.operationId));
        updateDeadline();
    }
    private int blockerText(String blocker) {
        return switch (blocker) {
            case "NONE" -> R.string.cloudy_presentation_admitted;
            case "OWNER_NOT_VISIBLE" -> R.string.cloudy_presentation_not_visible;
            case "NATIVE_PROCESS_USED" -> R.string.cloudy_presentation_restart;
            case "NETWORK_PERMISSION_REQUIRED" -> R.string.cloudy_presentation_permission;
            case "IDENTITY_REQUIRED" -> R.string.cloudy_presentation_identity;
            case "CATALOG_REQUIRED" -> R.string.cloudy_presentation_catalog;
            case "SURFACE_REQUIRED" -> R.string.cloudy_presentation_surface;
            case "CLEANUP_PENDING" -> R.string.cloudy_play_cleanup_pending;
            case "OPERATION_ACTIVE" -> R.string.cloudy_presentation_busy;
            case "EVIDENCE_RESTART_REQUIRED" -> R.string.cloudy_presentation_evidence_restart;
            case "EVIDENCE_CAPACITY" -> R.string.cloudy_presentation_capacity;
            case "EVIDENCE_UNFINISHED_CURRENT" -> R.string.cloudy_presentation_current_evidence;
            case "EVIDENCE_PRIOR_PROCESS" -> R.string.cloudy_presentation_prior_evidence;
            case "EVIDENCE_MALFORMED" -> R.string.cloudy_presentation_malformed_evidence;
            case "EVIDENCE_UNAVAILABLE" -> R.string.cloudy_presentation_unavailable_evidence;
            case "PROCESS_OWNER" -> R.string.cloudy_presentation_owner;
            default -> R.string.cloudy_presentation_blocked;
        };
    }
    private String phaseText(String phase) {
        int resource=switch (phase) {
            case "PREPARING" -> R.string.cloudy_presentation_preparing;
            case "AUTHORIZATION_REQUIRED" -> R.string.cloudy_presentation_auth_required;
            case "AUTHORIZING" -> R.string.cloudy_presentation_authorizing;
            case "RUNNING" -> R.string.cloudy_presentation_running;
            case "STOPPING" -> R.string.cloudy_identity_cancelling;
            case "CLEANUP_PENDING" -> R.string.cloudy_play_cleanup_pending;
            case "RETIRED" -> R.string.cloudy_presentation_retired;
            default -> R.string.cloudy_presentation_idle;
        };
        return getString(resource);
    }
    private void updateDeadline() {
        StreamProbeViewModel.UiState state=lastState;if (state==null) return;
        long now=SystemClock.elapsedRealtime();
        if (state.busy && state.authorizationDeadlineElapsedMillis>=0) {
            setTextIfChanged(deadlineStatus,getString(R.string.cloudy_presentation_authorization_time,
                    PresentationLayout.remainingSeconds(now,state.authorizationDeadlineElapsedMillis)));
        } else if (state.busy && state.promptDeadlineElapsedMillis>=0) {
            setTextIfChanged(deadlineStatus,getString(R.string.cloudy_presentation_prompt_time,
                    PresentationLayout.remainingSeconds(now,state.promptDeadlineElapsedMillis)));
        } else setTextIfChanged(deadlineStatus,getString(R.string.cloudy_presentation_no_deadline));
    }
    private static void setTextIfChanged(TextView view,CharSequence text) {
        // Layout callbacks also refresh presentation; unchanged labels must not cause a layout loop.
        if (!android.text.TextUtils.equals(view.getText(),text)) view.setText(text);
    }
    private PlaybackSurfaceOwner ownerFor(TextureView view) {
        return view.getTag() instanceof PlaybackSurfaceOwner ? (PlaybackSurfaceOwner)view.getTag() : null;
    }
    private void sample(TextureView view,PlaybackSurfaceOwner owner,PresentationTracker.Edge edge) {
        if (owner==null) return;
        Rect visible=new Rect();boolean hasRect=view.getGlobalVisibleRect(visible);
        owner.observePresentation(edge,new PresentationTracker.Geometry(view.getWidth(),view.getHeight(),
                hasRect ? visible.width() : 0,hasRect ? visible.height() : 0,
                view.isAttachedToWindow(),view.isShown(),view.isHardwareAccelerated(),
                hasRect ? visible.left : 0,hasRect ? visible.top : 0));
    }
    private void installPreview() {
        TextureView textureView=new TextureView(this);previewTexture=textureView;
        textureView.setContentDescription(getString(R.string.cloudy_play_surface));
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            private PlaybackSurfaceOwner owner;
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height) {
                owner=new PlaybackSurfaceOwner(StreamProbeActivity.this,texture,() -> previewContainer.removeView(textureView));
                textureView.setTag(owner);if (previewTexture==textureView) previewSurface=owner;sample(textureView,owner,PresentationTracker.Edge.AVAILABLE);refreshPresentationControls();
            }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                PlaybackSurfaceOwner destroyed=owner;
                if (previewSurface==destroyed) previewSurface=null;
                if (previewTexture==textureView) { previewTexture=null;preview.setEnabled(false); }
                if (destroyed!=null) model.surfaceDestroyed(destroyed);
                return false; // Actual handoff; the retained owner waits for real decoder retirement.
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height) {
                sample(textureView,owner,PresentationTracker.Edge.VISIBILITY);refreshPresentationControls();
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                if (owner!=null && owner.textureUpdated()) sample(textureView,owner,PresentationTracker.Edge.FIRST_UPDATE);
            }
        });
        previewContainer.addView(textureView,new FrameLayout.LayoutParams(0,0,Gravity.CENTER));
        textureView.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob) -> {
            sample(textureView,ownerFor(textureView),PresentationTracker.Edge.VISIBILITY);refreshPresentationControls();
        });
        fitTexture();
    }
    private TextView label(LinearLayout parent, int resource) {
        TextView view = new TextView(this);
        view.setText(resource);
        view.setTextSize(16);
        view.setPadding(0, dp(4), 0, dp(4));
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
    @SuppressWarnings("deprecation") // Retain the existing native permission request route; no authorization is restored.
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode==17) model.refreshAdmission();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onStart() {
        super.onStart();foreground=true;uiHandler.removeCallbacks(deadlineTick);uiHandler.post(deadlineTick);model.onVisible();
    }
    @Override protected void onStop() {
        foreground=false;uiHandler.removeCallbacks(deadlineTick);
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
