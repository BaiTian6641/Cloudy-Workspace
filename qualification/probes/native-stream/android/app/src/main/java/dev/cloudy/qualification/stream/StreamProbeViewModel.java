package dev.cloudy.qualification.stream;

import android.app.Application;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.limelight.BuildConfig;
import com.limelight.R;
import com.limelight.nvstream.jni.MoonBridge;
import dev.cloudy.qualification.stream.identity.StreamIdentityStore;
import dev.cloudy.qualification.stream.pairing.PairingController;
import dev.cloudy.qualification.stream.playback.PlaybackController;
import dev.cloudy.qualification.stream.playback.PlaybackProfile;
import org.json.JSONArray;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Memory-only operation owner. No saved authorization or Activity reference; one finite foreground operation. */
public final class StreamProbeViewModel extends AndroidViewModel {
    public enum PromptMode {
        NONE, STRONG_OR_CREDENTIAL, DEVICE_CREDENTIAL;
        public boolean acceptsAuthenticationType(int type) {
            return this == DEVICE_CREDENTIAL ? type == 1
                    : this == STRONG_OR_CREDENTIAL && (type == 1 || type == 2);
        }
    }
    public static final class UiState {
        public final int message;
        public final int nativeMessage;
        public final String detail;
        public final String identityState;
        public final String securityLevel;
        public final boolean busy;
        public final boolean nativeTried;
        public final long promptToken;
        public final boolean promptRequested;
        public final boolean cancelPrompt;
        public final PromptMode promptMode;
        public final boolean pairing;
        public final boolean playback,restartRequired,cleanupRetry,catalog;
        public final java.util.List<CatalogEntry> catalogEntries;
        public final long catalogGeneration;
        public final int selectedAppId;
        public final String playbackStatus;
        public final String operationPhase,admissionBlocker,operationId;
        public final long authorizationDeadlineElapsedMillis,promptDeadlineElapsedMillis;
        public final boolean networkPermissionGranted;
        private UiState(int message, int nativeMessage, String detail, String identityState,
                String securityLevel, boolean busy, boolean nativeTried, long promptToken,
                boolean promptRequested, boolean cancelPrompt, PromptMode promptMode, boolean pairing,boolean playback,boolean restartRequired,boolean cleanupRetry,String playbackStatus,boolean catalog,java.util.List<CatalogEntry> catalogEntries,long catalogGeneration,int selectedAppId,String operationPhase,String admissionBlocker,String operationId,long authorizationDeadlineElapsedMillis,long promptDeadlineElapsedMillis,boolean networkPermissionGranted) {
            this.operationPhase=operationPhase;this.admissionBlocker=admissionBlocker;this.operationId=operationId;
            this.authorizationDeadlineElapsedMillis=authorizationDeadlineElapsedMillis;this.promptDeadlineElapsedMillis=promptDeadlineElapsedMillis;
            this.networkPermissionGranted=networkPermissionGranted;
            this.message = message;
            this.nativeMessage = nativeMessage;
            this.detail = detail;
            this.identityState = identityState;
            this.securityLevel = securityLevel;
            this.busy = busy;
            this.nativeTried = nativeTried;
            this.promptToken = promptToken;
            this.promptRequested = promptRequested;
            this.cancelPrompt = cancelPrompt;
            this.promptMode = promptMode;
            this.pairing = pairing; this.playback=playback; this.restartRequired=restartRequired; this.cleanupRetry=cleanupRetry; this.playbackStatus=playbackStatus; this.catalog=catalog;this.catalogEntries=catalogEntries;this.catalogGeneration=catalogGeneration;this.selectedAppId=selectedAppId;
        }
    }
    private enum Kind { REFRESH, CREATE, AUTHORIZE, DELETE, NATIVE, PAIR, PLAY, CATALOG, RECONCILE }
    private static final class Operation {
        final long token;
        final Kind kind;
        final PromptMode promptMode;
        final StreamIdentityStore.Attempt attempt;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final long started = SystemClock.elapsedRealtime();
        volatile String cancelReason = "OPERATOR";
        volatile File journal;
        File networkMarker;
        NetworkRetirementCoordinator promptRetirement;
        volatile String evidenceReason="NONE";
        PlaybackProfile playbackProfile;
        CatalogPeerProfile catalogPeer;
        volatile CatalogController catalogController;
        PlaybackSurfaceOwner surface;
        long surfaceGeneration;
        int selectedAppId;
        long catalogGeneration;
        volatile PlaybackController playbackController;
        volatile boolean playbackAdmitted,playbackReturned,workReturned;
        boolean surfaceFinishing,finalizing,playbackTerminal;

        char[] pin;
        PairingController.Profile profile;
        volatile PairingController controller;
        volatile boolean finalRetired;
        volatile boolean evidenceFailed;
        volatile boolean terminalEvidence;
        volatile Result lastResult;
        boolean workerRunning;
        boolean promptRequested;
        volatile boolean promptOutstanding;
        boolean completeEnrollment;
        long promptDeadline;
        volatile long authenticatedAt = -1;
        volatile int authenticationType = -1;
        Operation(long token, Kind kind, StreamIdentityStore.Attempt attempt, PromptMode promptMode) {
            this.token = token;
            this.kind = kind;
            this.promptMode = promptMode;
            this.attempt = attempt;
        }
    }
    private static final class Result {
        StreamIdentityStore.IdentityInfo info;
        StreamIdentityStore.Qualification qualification;
        boolean needsPrompt;
        String failure = "";
        String failureStage;
        String failureCauseClass;
        Integer keyStoreErrorCode;
        String nativeMetadata;
        long nativeDuration;
        boolean pairingCompleted, clientTlsRead;
        String pairingStage = "CREATED";
        int applicationListBytes;
        PlaybackController.Result playback;
        CatalogController.Result catalogResult;
        JSONArray reconciled;
    }
    private interface Action { Result run() throws Exception; }
    private static final StreamOperationLease PROCESS_OWNER = new StreamOperationLease();
    private static final AtomicBoolean NETWORK_BLOCKED = new AtomicBoolean();
    private static final AtomicReference<NetworkRetirementCoordinator> RETIRING_NETWORK=new AtomicReference<>();
    private static final AtomicReference<String> EVIDENCE_BLOCK_REASON=new AtomicReference<>("EVIDENCE_UNAVAILABLE");
    private static final String PROCESS_REF=UUID.randomUUID().toString();
    private static final Object JOURNAL_LOCK = new Object();
    // Main-thread-only visible models receive local cleanup-barrier state changes,
    // including when the retired owner belongs to a previously destroyed window.
    private static final java.util.Set<StreamProbeViewModel> VISIBLE_MODELS=new java.util.HashSet<>();
    private static void notifyAdmissionViews() {
        for (StreamProbeViewModel model:new java.util.ArrayList<>(VISIBLE_MODELS)) model.emit();
    }
    private static final long PROMPT_MILLIS = 120_000;
    private static final int MAX_RECORD_BYTES = 32_768;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final StreamIdentityStore store;
    private final MutableLiveData<UiState> ui = new MutableLiveData<>();
    private volatile boolean visible;
    private volatile boolean cleared;
    private volatile Operation active;
    private long sequence;
    private boolean initiallyRefreshed;
    private boolean nativeTried;
    private String admissionFailure="NONE";
    private String identityState = "UNKNOWN";
    private String securityLevel = "unavailable";
    private int message = R.string.cloudy_identity_ready;
    private int nativeMessage = R.string.cloudy_stream_ready;
    private String detail = "";
    private Runnable promptTimeout;
    private Runnable playbackPoll;
    private String lastPlaybackStatus="";
    private CatalogPeerProfile catalogPeer;
    private java.util.List<CatalogEntry> catalogEntries=java.util.List.of();
    private long catalogGeneration;
    private int selectedAppId;
    private PlaybackProfile selectedProfile;

    public StreamProbeViewModel(Application application) {
        super(application);
        store = new StreamIdentityStore(application);
        emit();
    }
    public LiveData<UiState> state() { return ui; }
    /** Re-evaluate admission after a public permission callback; no worker, journal or network action. */
    public void refreshAdmission() { admissionFailure="NONE";emit(); }

    public void onVisible() {
        visible = true;
        VISIBLE_MODELS.add(this);
        if (!initiallyRefreshed) { initiallyRefreshed = true; refreshIdentity(); }
    }
    public void onHidden() { visible = false;VISIBLE_MODELS.remove(this); cancel("BACKGROUND"); }

    public void refreshIdentity() {
        Operation op = start(Kind.REFRESH);
        if (op == null) return;
        submit(op, () -> { Result result = new Result(); result.info = store.prepare(); return result; });
    }
    public void createIdentity() {
        Operation op = start(Kind.CREATE);
        if (op == null) return;
        submit(op, () -> {
            Result result = new Result();
            result.info = store.beginEnrollment();
            result.needsPrompt = true;
            return result;
        });
    }
    public void authorizeIdentity() { authorizeIdentity(PromptMode.STRONG_OR_CREDENTIAL); }
    public void authorizeWithCredential() { authorizeIdentity(PromptMode.DEVICE_CREDENTIAL); }
    private void authorizeIdentity(PromptMode mode) {
        Operation op = start(Kind.AUTHORIZE, mode);
        if (op == null) return;
        submit(op, () -> {
            Result result = new Result();
            result.info = store.prepare();
            if (result.info.state == StreamIdentityStore.State.ABSENT) result.failure = "IDENTITY_ABSENT";
            else result.needsPrompt = true;
            return result;
        });
    }
    /** Caller transfers a native-field PIN copy; it is wiped on every terminal path. */
    public void pair(char[] pin) {
        if (!localNetworkGranted()) {
            if (pin!=null) Arrays.fill(pin,'\0');
            message=R.string.cloudy_identity_failed; detail="LOCAL_NETWORK_PERMISSION_REQUIRED"; emit(); return;
        }
        if (!PairingPinPolicy.valid(pin)) {
            if (pin != null) Arrays.fill(pin, '\0');
            message=R.string.cloudy_identity_failed; detail="INVALID_PAIRING_PIN"; emit(); return;
        }
        Operation op=start(Kind.PAIR,PromptMode.DEVICE_CREDENTIAL);
        if (op==null) { Arrays.fill(pin,'\0'); return; }
        op.pin=pin;
        submit(op,() -> {
            Result result=new Result(); result.info=store.prepare();
            if (result.info.state!=StreamIdentityStore.State.READY) { result.failure="READY_IDENTITY_REQUIRED"; return result; }
            op.profile=PairingProfile.read(getApplication());
            result.needsPrompt=true; return result;
        });
    }

    public void readCatalog() {
        if (active!=null || cleared) return;
        // A failed/empty/new-peer read must never leave a launchable old selection.
        catalogPeer=null;catalogEntries=java.util.List.of();selectedProfile=null;selectedAppId=0;catalogGeneration++;
        if (!localNetworkGranted()) { message=R.string.cloudy_identity_failed;detail="LOCAL_NETWORK_PERMISSION_REQUIRED";emit();return; }
        Operation op=start(Kind.CATALOG,PromptMode.DEVICE_CREDENTIAL); if (op==null) { emit();return; }
        submit(op,() -> {
            Result result=new Result();result.info=store.prepare();
            if (result.info.state!=StreamIdentityStore.State.READY) { result.failure="READY_IDENTITY_REQUIRED";return result; }
            op.catalogPeer=CatalogPeerProfile.read(getApplication());result.needsPrompt=true;return result;
        });
    }
    public void selectCatalogApp(long generation,int id) {
        if (active!=null || cleared || catalogPeer==null) return;
        boolean contained=catalogEntries.stream().anyMatch(entry -> entry.id==id);
        if (!CatalogPolicy.selected(catalogGeneration,generation,id,contained)) return;
        try { selectedProfile=catalogPeer.select(id);selectedAppId=id; }
        catch (Exception rejected) { selectedProfile=null;selectedAppId=0;message=R.string.cloudy_identity_failed;detail="CATALOG_SELECTION_UNAVAILABLE"; }
        emit();
    }
    public void preview(PlaybackSurfaceOwner surface) {
        admissionFailure=!localNetworkGranted() ? "NETWORK_PERMISSION_REQUIRED" : selectedProfile==null ? "CATALOG_REQUIRED" :
                surface==null || !surface.ready() ? "SURFACE_REQUIRED" : PlaybackController.requiresFreshProcess() ? "NATIVE_PROCESS_USED" : "NONE";
        if (!localNetworkGranted() || selectedProfile==null || surface==null || !surface.ready() || PlaybackController.requiresFreshProcess()) {
            message=R.string.cloudy_identity_failed; detail=PlaybackController.requiresFreshProcess() ? "PLAYBACK_REQUIRES_FRESH_PROCESS" : "CATALOG_SELECTION_PERMISSION_OR_SURFACE_REQUIRED"; emit(); return;
        }
        Operation op=start(Kind.PLAY,PromptMode.DEVICE_CREDENTIAL);
        if (op==null) return;
        op.surface=surface; op.surfaceGeneration=surface.generation();op.playbackProfile=selectedProfile;op.selectedAppId=selectedAppId;op.catalogGeneration=catalogGeneration;
        if (!surface.bind(op.token)) { op.surface=null; op.surfaceGeneration=0; op.workReturned=true; revoke(op,"SURFACE_UNAVAILABLE"); finishPlayback(op); return; }
        submit(op,() -> {
            Result result=new Result(); result.info=store.prepare();
            if (result.info.state!=StreamIdentityStore.State.READY) { result.failure="READY_IDENTITY_REQUIRED"; return result; }
            result.needsPrompt=true; return result;
        });
        playbackPoll=() -> {
            if (active!=op) return;
            updatePlaybackStatus(op); emit(); main.postDelayed(playbackPoll,250);
        };
        main.post(playbackPoll);
    }
    public void surfaceDestroyed(PlaybackSurfaceOwner surface) {
        surface.destroyed();
        Operation op=active;
        if (op==null || op.kind!=Kind.PLAY || op.surface!=surface || !surface.matches(op.token,op.surfaceGeneration)) {
            if (!surface.bound()) surface.release(true);
            return;
        }
        if (!op.surfaceFinishing) {
            revoke(op,"SURFACE_LOST");
            if (op.playbackController!=null) op.playbackController.surfaceLost();
        }
        finishPlayback(op);
    }
    /** Explicit local acknowledgement only; never replays or contacts the previous host. */
    public void reconcileNetworkEvidence() {
        Operation op=start(Kind.RECONCILE,PromptMode.NONE); if (op==null) return;
        submit(op,() -> {
            if (PlaybackController.processActive()) throw new IOException();
            Result result=new Result(); result.reconciled=NetworkEvidenceStore.reconcile(getApplication().getNoBackupFilesDir(),PROCESS_REF); return result;
        });
    }
    public void retryPlaybackCleanup() {
        NetworkRetirementCoordinator previous=RETIRING_NETWORK.get();
        if (previous!=null) { previous.retryTerminal();emit();return; }
        Operation op=active;
        if (op!=null && op.promptRetirement!=null && !op.workerRunning && !op.promptOutstanding) {
            revoke(op,"LOCAL_TERMINAL_RETRY");
            RETIRING_NETWORK.set(op.promptRetirement);
            op.promptRetirement.closeOwner();op.promptRetirement.retryTerminal();return;
        }
        if (op==null || op.kind!=Kind.PLAY || op.workerRunning || op.promptOutstanding || op.finalizing) return;
        if (op.playbackAdmitted && op.playbackController!=null && !op.playbackController.retired()) {
            op.workerRunning=true; op.workReturned=false;
            worker.execute(() -> {
                op.playbackController.retryCleanupBlocking();
                op.workReturned=true;
                main.post(() -> { op.workerRunning=false; finishPlayback(op); });
            });
        } else finishPlayback(op);
    }
    public void deleteIdentity() {
        Operation op = start(Kind.DELETE);
        if (op == null) return;
        submit(op, () -> {
            store.deleteIdentity();
            Result result = new Result(); result.info = store.prepare(); return result;
        });
    }
    public void loadNative() {
        if (nativeTried) return;
        Operation op = start(Kind.NATIVE);
        if (op == null) return;
        nativeTried = true;
        nativeMessage = R.string.cloudy_stream_running;
        emit();
        submit(op, () -> {
            Result result = new Result();
            long start = SystemClock.elapsedRealtimeNanos();
            // Upstream class initialization owns init exactly once per process.
            result.nativeMetadata = MoonBridge.getLaunchUrlQueryParameters();
            result.nativeDuration = SystemClock.elapsedRealtimeNanos() - start;
            if (result.nativeMetadata == null || result.nativeMetadata.length() > 4096) result.failure = "NATIVE_METADATA_BOUNDS";
            return result;
        });
    }

    private Operation start(Kind kind) {
        return start(kind, kind == Kind.CREATE || kind == Kind.AUTHORIZE
                ? PromptMode.STRONG_OR_CREDENTIAL : PromptMode.NONE);
    }
    private Operation start(Kind kind, PromptMode mode) {
        if (!visible || cleared || active != null) {
            admissionFailure=active!=null ? "OPERATION_ACTIVE" : "OWNER_NOT_VISIBLE";emit();return null;
        }
        if ((network(kind) && NETWORK_BLOCKED.get()) || !PROCESS_OWNER.claim(this)) {
            message=R.string.cloudy_identity_failed; detail=NETWORK_BLOCKED.get() ? EVIDENCE_BLOCK_REASON.get() : "PROCESS_OWNER"; admissionFailure=detail; emit(); return null;
        }
        try {
            StreamIdentityStore.Attempt attempt = kind == Kind.CREATE || kind == Kind.AUTHORIZE || kind == Kind.PAIR || kind==Kind.PLAY || kind==Kind.CATALOG ? store.newAttempt() : null;
            Operation op = new Operation(++sequence, kind, attempt, mode);
            if (kind==Kind.CATALOG || kind==Kind.PAIR) op.promptRetirement=new NetworkRetirementCoordinator(worker,() -> {
                if (op.journal==null) reserve(op);
                persist(op,op.lastResult==null ? new Result() : op.lastResult);
                if (!op.terminalEvidence) throw new IOException("terminal-readback-required");
            },() -> main.post(() -> {
                op.finalRetired=true;
                wipePin(op); PROCESS_OWNER.release(this);
                RETIRING_NETWORK.compareAndSet(op.promptRetirement,null);
                if (active==op) active=null;
                if (op.evidenceFailed) EVIDENCE_BLOCK_REASON.set("EVIDENCE_RESTART_REQUIRED");
                if (cleared) { store.close();worker.shutdown(); }
                else { message=op.evidenceFailed ? R.string.cloudy_identity_failed : R.string.cloudy_identity_cancelled;detail=op.evidenceFailed ? "EVIDENCE_RESTART_REQUIRED" : op.cancelReason; }
                notifyAdmissionViews();
            }),() -> main.post(() -> {
                evidenceFailure(op); detail="TERMINAL_EVIDENCE_UNCONFIRMED"; notifyAdmissionViews();
            }));
            admissionFailure="NONE";
            active = op;
            message = R.string.cloudy_identity_working;
            detail = "";
            emit();
            return op;
        } catch (RuntimeException failure) {
            PROCESS_OWNER.release(this);
            message = R.string.cloudy_identity_failed;
            detail = code(failure);
            emit();
            return null;
        }
    }

    private void submit(Operation op, Action action) {
        op.workReturned=false;
        op.workerRunning = true;
        if (op.promptRetirement!=null) op.promptRetirement.workerSubmitted();
        worker.execute(() -> {
            Result result = new Result();
            try {
                if (op.journal == null) {
                    try { reserve(op); }
                    catch (Exception failure) {
                        if (failure instanceof EvidenceAdmissionException) op.evidenceReason=((EvidenceAdmissionException)failure).reason;
                        evidenceFailure(op);
                        // Evidence capacity must never prevent deliberate key deletion.
                        if (op.kind != Kind.DELETE) throw failure;
                        result.failure = "EVIDENCE_UNAVAILABLE";
                    }
                }
                if (!op.cancelled.get() && !cleared && visible) {
                    String priorFailure = result.failure;
                    result = action.run();
                    if (!priorFailure.isEmpty()) result.failure = priorFailure;
                }
            } catch (Exception | LinkageError failure) {
                if (((op.kind==Kind.PAIR && op.controller!=null) || (op.kind==Kind.PLAY && op.playbackController!=null) || (op.kind==Kind.CATALOG && op.catalogController!=null)) && op.lastResult!=null) result=op.lastResult;
                result.needsPrompt=false;
                result.failure = code(failure);
                if (failure instanceof StreamIdentityStore.IdentityException) {
                    StreamIdentityStore.IdentityException identityFailure = (StreamIdentityStore.IdentityException) failure;
                    result.failureStage = identityFailure.stage;
                    result.failureCauseClass = identityFailure.causeClass;
                    result.keyStoreErrorCode = identityFailure.keyStoreErrorCode;
                }
            }
            if (op.cancelled.get() || cleared || !visible) result.needsPrompt = false;
            try { persist(op, result); }
            catch (Exception failure) { evidenceFailure(op); result.failure = "EVIDENCE_UNAVAILABLE"; result.needsPrompt = false; }
            op.lastResult = result;
            if ((op.kind==Kind.PAIR || op.kind==Kind.CATALOG) && !op.evidenceFailed && !result.needsPrompt && !op.promptOutstanding) op.finalRetired=true;
            Result completed = result;
            op.workReturned=true;
            if (op.promptRetirement!=null) op.promptRetirement.workerReturned();
            main.post(() -> completed(op, completed));
        });
    }

    private void completed(Operation op, Result result) {
        if (active != op) return;
        if (cleared && (op.kind==Kind.CATALOG || op.kind==Kind.PAIR)) { op.workerRunning=false;return; }
        if (cleared && op.kind!=Kind.PLAY) return;
        op.workerRunning = false;
        if (result.info != null) {
            identityState = result.info.state.name();
            securityLevel = result.info.securityLevel;
        }
        if (op.kind==Kind.PLAY && (!result.needsPrompt || !result.failure.isEmpty() || op.cancelled.get() || !visible || cleared)) {
            finishPlayback(op); return;
        }
        if (op.cancelled.get() || !visible) {
            if (!op.cancelled.get()) revoke(op, "BACKGROUND");
            // A cancellation crossing the previous write needs its own final record.
            finishCancellation(op);
            return;
        }
        if (result.needsPrompt && result.failure.isEmpty()) {
            op.completeEnrollment = result.info.state == StreamIdentityStore.State.ENROLLMENT_PENDING;
            op.promptRequested = true;
            op.promptDeadline = SystemClock.elapsedRealtime() + PROMPT_MILLIS;
            message = R.string.cloudy_identity_auth_wait;
            promptTimeout = () -> { if (active == op) cancel("PROMPT_TIMEOUT"); };
            main.postDelayed(promptTimeout, PROMPT_MILLIS);
            emit();
            return;
        }
        if ((op.kind==Kind.CATALOG || op.kind==Kind.PAIR) && !NetworkRetirementCoordinator.normalRetirementReady(op.terminalEvidence,op.workerRunning,op.promptOutstanding)) {
            message=R.string.cloudy_identity_failed;detail="TERMINAL_EVIDENCE_PENDING";emit();return;
        }
        if (op.kind==Kind.CATALOG && result.failure.isEmpty() && result.catalogResult!=null) {
            catalogPeer=op.catalogPeer;catalogEntries=result.catalogResult.entries;
        }
        if (op.kind==Kind.RECONCILE && result.failure.isEmpty()) NETWORK_BLOCKED.set(false);
        if (op.kind == Kind.NATIVE) nativeMessage = result.failure.isEmpty()
                ? R.string.cloudy_stream_success : R.string.cloudy_stream_failed;
        message = result.failure.isEmpty() ? (op.kind == Kind.CATALOG ? R.string.cloudy_catalog_complete : op.kind == Kind.RECONCILE ? R.string.cloudy_network_reconciled : op.kind == Kind.PAIR ? R.string.cloudy_pair_success : op.kind == Kind.DELETE ? R.string.cloudy_identity_deleted
                : result.qualification != null ? R.string.cloudy_identity_qualified : R.string.cloudy_identity_refreshed)
                : R.string.cloudy_identity_failed;
        detail = result.failure + (result.failureStage == null ? "" : " (" + result.failureStage + ")");
        if (op.kind==Kind.PAIR && op.controller!=null) detail += " · " + op.controller.stage().name() + " · pairingCompleted=" + op.controller.pairingCompleted() + " · clientTlsRead=" + result.clientTlsRead;
        retire(op);
    }

    /** Called only immediately before an explicit new authenticate(), never to replay a retained prompt. */
    public boolean claimPrompt(long token) {
        Operation op = active;
        if (op == null || op.token != token || !op.promptRequested || op.cancelled.get()
                || !visible || !store.checkCurrent(op.attempt)
                || SystemClock.elapsedRealtime() >= op.promptDeadline) return false;
        op.promptRequested = false;
        op.promptOutstanding = true;
        if (op.promptRetirement!=null) op.promptRetirement.promptStarted();
        emit();
        return true;
    }
    public long promptTokenForReattach() {
        return active != null && active.promptOutstanding ? active.token : 0;
    }
    public void authenticationSucceeded(long token, long authenticatedAtElapsedMillis, int authenticationType) {
        Operation op = active;
        if (op == null || op.token != token || !op.promptOutstanding) return;
        op.promptOutstanding = false;
        if (op.promptRetirement!=null) op.promptRetirement.promptRetired();
        if (cleared) { if (op.kind==Kind.PLAY) finishPlayback(op); else if (op.kind!=Kind.CATALOG && op.kind!=Kind.PAIR && op.finalRetired) PROCESS_OWNER.release(this); return; }
        clearTimeout();
        if (op.cancelled.get() || !visible || !store.checkCurrent(op.attempt)
                || SystemClock.elapsedRealtime() >= op.promptDeadline) {
            revoke(op, "STALE_AUTHORIZATION"); finishCancellation(op); return;
        }
        op.authenticatedAt = authenticatedAtElapsedMillis;
        op.authenticationType = authenticationType == 1 || authenticationType == 2 ? authenticationType : -1;
        // A framework success is not permission to substitute a different requested authenticator.
        if (!op.promptMode.acceptsAuthenticationType(op.authenticationType)) {
            store.cancel(op.attempt);
            submit(op, () -> { Result rejected = new Result(); rejected.failure = "AUTH_TYPE_MISMATCH"; return rejected; });
            return;
        }
        if (network(op.kind)) {
            promptTimeout=() -> { if (active==op) cancel("AUTHORIZATION_DEADLINE"); };
            main.postDelayed(promptTimeout,Math.max(0,30_000-(SystemClock.elapsedRealtime()-authenticatedAtElapsedMillis)));
        }
        message = R.string.cloudy_identity_working;
        emit();
        submit(op, () -> {
            Result result = new Result();
            if (op.kind==Kind.PAIR || op.kind==Kind.CATALOG) checkPairing(op);
            if (op.kind==Kind.PLAY) checkPlayback(op);
            result.qualification = op.completeEnrollment
                    ? store.completeEnrollment(op.attempt, authenticatedAtElapsedMillis)
                    : store.qualify(op.attempt, authenticatedAtElapsedMillis);
            if (op.kind==Kind.PAIR) {
                checkPairing(op);
                PairingController controller=new PairingController(op.profile,store.provider(op.attempt),
                        () -> checkPairing(op),authenticatedAtElapsedMillis,30_000);
                op.controller=controller;
                try {
                    checkPairing(op); // Cancellation may have won before controller publication.
                    PairingController.Result paired=controller.run(op.pin);
                    result.clientTlsRead=paired.clientTlsRead;
                    result.applicationListBytes=paired.applicationListBytes;
                } finally {
                    result.pairingCompleted=controller.pairingCompleted();
                    result.pairingStage=controller.stage().name();
                    op.lastResult=result;
                    controller.cancel();
                    wipePin(op);
                }
            }
            if (op.kind==Kind.CATALOG) {
                checkPairing(op);
                CatalogController controller=new CatalogController(op.catalogPeer,store.provider(op.attempt),
                        () -> checkPairing(op),authenticatedAtElapsedMillis,30_000);
                op.catalogController=controller;
                try { checkPairing(op);result.catalogResult=controller.run(); }
                finally { op.lastResult=result;controller.cancel(); }
            }
            if (op.kind==Kind.PLAY) {
                checkPlayback(op);
                PlaybackController controller=new PlaybackController(op.playbackProfile,store.provider(op.attempt),
                        () -> checkPlayback(op),authenticatedAtElapsedMillis,30_000);
                op.playbackController=controller;
                try {
                    checkPlayback(op);
                    op.playbackAdmitted=true;
                    result.playback=controller.run(op.surface.activity(),op.surface.surface());
                    if (result.playback.failure!=PlaybackController.Failure.NONE) result.failure="PLAYBACK_"+result.playback.failure.name();
                } finally {
                    op.playbackReturned=op.playbackAdmitted; controller.cancel();
                    result.playback=controller.snapshot(); op.lastResult=result;
                }
            }
            result.info = store.prepare();
            return result;
        });
    }
    private void checkPlayback(Operation op) throws IOException {
        checkPairing(op);
        PlaybackSurfaceOwner surface=op.surface;
        if (active!=op || surface==null || !surface.matches(op.token,op.surfaceGeneration) || !surface.ready()) throw new IOException("surface-or-owner-unavailable");
    }
    private boolean localNetworkGranted() {
        return Build.VERSION.SDK_INT<37 || getApplication().checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)==PackageManager.PERMISSION_GRANTED;
    }
    private void checkPairing(Operation op) throws IOException {
        if (active!=op || !localNetworkGranted() || !visible || cleared || op.cancelled.get() || !store.checkCurrent(op.attempt)
                || op.authenticatedAt<0 || SystemClock.elapsedRealtime()-op.authenticatedAt>=30_000) throw new IOException("pairing-owner-unavailable");
    }
    private void evidenceFailure(Operation op) {
        op.evidenceFailed=true;
        NETWORK_BLOCKED.set(true);
        EVIDENCE_BLOCK_REASON.set("NONE".equals(op.evidenceReason) ? "EVIDENCE_UNAVAILABLE" : op.evidenceReason);
        if (op.controller!=null) op.controller.cancel();
        if (op.catalogController!=null) op.catalogController.cancel();
        if (op.playbackController!=null) op.playbackController.cancel();
        store.cancel(op.attempt);
    }
    private static void wipePin(Operation op) { if (op.pin!=null) { Arrays.fill(op.pin,'\0'); op.pin=null; } }
    public void authenticationError(long token, int errorCode) {
        Operation op = active;
        if (op == null || op.token != token || !op.promptOutstanding) return;
        op.promptOutstanding = false;
        if (op.promptRetirement!=null) op.promptRetirement.promptRetired();
        if (cleared) { if (op.kind==Kind.PLAY) finishPlayback(op); else if (op.kind!=Kind.CATALOG && op.kind!=Kind.PAIR && op.finalRetired) PROCESS_OWNER.release(this); return; }
        revoke(op, errorCode >= 1 && errorCode <= 15 ? "AUTH_ERROR_" + errorCode : "AUTH_ERROR_UNKNOWN");
        finishCancellation(op);
    }
    public void authenticationFailed(long token) {
        if (active != null && active.token == token && active.promptOutstanding && !active.cancelled.get()) {
            message = R.string.cloudy_identity_auth_retry;
            emit();
        }
    }
    public void promptLaunchFailed(long token) {
        Operation op = active;
        if (op == null || op.token != token) return;
        op.promptOutstanding = false;
        if (op.promptRetirement!=null) op.promptRetirement.promptRetired();
        if (cleared) { if (op.kind==Kind.PLAY) finishPlayback(op);return; }
        revoke(op, "AUTH_UNAVAILABLE");
        finishCancellation(op);
    }
    public void cancel() { cancel("OPERATOR"); }
    private void cancel(String reason) {
        Operation op = active;
        if (op == null) return;
        revoke(op, reason);
        finishCancellation(op);
    }
    private void revoke(Operation op, String reason) {
        if (!op.cancelled.getAndSet(true)) op.cancelReason = reason;
        store.cancel(op.attempt);
        if (op.controller!=null) op.controller.cancel();
        if (op.catalogController!=null) op.catalogController.cancel();
        if (op.playbackController!=null) op.playbackController.cancel();
        op.promptRequested = false;
        clearTimeout();
        message = R.string.cloudy_identity_cancelling;
        detail = op.cancelReason;
        emit();
    }
    private void finishCancellation(Operation op) {
        if (cleared && op.promptRetirement!=null) { op.promptRetirement.closeOwner();return; }
        if (op.kind==Kind.PLAY) { finishPlayback(op); return; }
        if ((op.kind==Kind.PAIR || op.kind==Kind.CATALOG) && op.finalRetired && !op.workerRunning && !op.promptOutstanding) {
            message=R.string.cloudy_identity_cancelled; detail=op.cancelReason; retire(op); return;
        }
        if (op.workerRunning || op.promptOutstanding) { emit(); return; }
        op.workerRunning = true;
        if (op.promptRetirement!=null) op.promptRetirement.workerSubmitted();
        worker.execute(() -> {
            String failure = "";
            try { if (op.journal == null) reserve(op); persist(op, op.lastResult == null ? new Result() : op.lastResult); }
            catch (Exception error) { evidenceFailure(op); failure = "EVIDENCE_UNAVAILABLE"; }
            String finalFailure = failure;
            if (op.promptRetirement!=null) op.promptRetirement.workerReturned();
            main.post(() -> {
                if (cleared || active != op) return;
                op.workerRunning = false;
                if (op.kind == Kind.NATIVE) nativeMessage = R.string.cloudy_stream_cancelled;
                message = finalFailure.isEmpty() ? R.string.cloudy_identity_cancelled : R.string.cloudy_identity_failed;
                detail = finalFailure.isEmpty() ? op.cancelReason : finalFailure;
                if ((op.kind==Kind.CATALOG || op.kind==Kind.PAIR) && !NetworkRetirementCoordinator.normalRetirementReady(op.terminalEvidence,op.workerRunning,op.promptOutstanding)) { emit();return; }
                retire(op);
            });
        });
    }
    private void retire(Operation op) {
        if (op.kind==Kind.PLAY) throw new IllegalStateException("playback-retirement-required");
        if ((op.kind==Kind.CATALOG || op.kind==Kind.PAIR) && !NetworkRetirementCoordinator.normalRetirementReady(op.terminalEvidence,op.workerRunning,op.promptOutstanding)) throw new IllegalStateException("catalog-retirement-required");
        clearTimeout();
        store.cancel(op.attempt);
        if (op.controller!=null) op.controller.cancel();
        if (op.catalogController!=null) op.catalogController.cancel();
        wipePin(op);
        PROCESS_OWNER.release(this);
        if (active == op) active = null;
        // Stop can retry a previously failed terminal write without entering the
        // cleared-owner coordinator. Preserve the same honest recovery state.
        if (op.promptRetirement!=null && op.evidenceFailed) {
            EVIDENCE_BLOCK_REASON.set("EVIDENCE_RESTART_REQUIRED");
            message=R.string.cloudy_identity_failed;detail="EVIDENCE_RESTART_REQUIRED";
            notifyAdmissionViews();
        }
        emit();
    }
    private boolean playbackNativeSafe(Operation op) {
        return PlaybackCompletionPolicy.nativeSafe(op.workReturned && !op.workerRunning,op.playbackAdmitted,
                op.playbackController!=null && op.playbackController.retired(),op.promptOutstanding);
    }
    private void finishPlayback(Operation op) {
        if (active!=op || op.finalizing || op.finalRetired) return;
        updatePlaybackStatus(op);
        if (!playbackNativeSafe(op)) {
            message=R.string.cloudy_play_cleanup_pending; emit(); return;
        }
        PlaybackSurfaceOwner surface=op.surface;
        op.surfaceFinishing=true;
        if (surface!=null && !surface.detached()) {
            message=R.string.cloudy_play_surface_wait; emit();
            surface.requestDetach(); return; // Only the actual destruction callback confirms handoff.
        }
        try { if (surface!=null) surface.release(true); }
        catch (RuntimeException failure) {
            message=R.string.cloudy_play_cleanup_pending; detail="SURFACE_RELEASE_UNCONFIRMED"; emit(); return;
        }
        op.finalizing=true;
        worker.execute(() -> {
            boolean durable=false;
            Result result=op.lastResult==null ? new Result() : op.lastResult;
            try {
                if (op.journal==null) reserve(op);
                op.playbackTerminal=true;
                persist(op,result); durable=true;
            } catch (Exception failure) {
                op.playbackTerminal=false; evidenceFailure(op); result.failure="EVIDENCE_UNAVAILABLE";
            }
            boolean terminalReadBack=durable;
            main.post(() -> {
                op.finalizing=false;
                if (!terminalReadBack) {
                    message=R.string.cloudy_play_cleanup_pending; detail="TERMINAL_EVIDENCE_UNCONFIRMED"; emit(); return;
                }
                if (!PlaybackCompletionPolicy.mayReleaseLease(playbackNativeSafe(op),surface==null || surface.released(),terminalReadBack)) {
                    message=R.string.cloudy_play_cleanup_pending; emit(); return;
                }
                op.finalRetired=true;
                updatePlaybackStatus(op); clearTimeout();
                if (playbackPoll!=null) main.removeCallbacks(playbackPoll); playbackPoll=null;
                store.cancel(op.attempt); PROCESS_OWNER.release(this); active=null;
                message=op.evidenceFailed || !result.failure.isEmpty() ? R.string.cloudy_identity_failed : R.string.cloudy_play_finished;
                detail=result.failure;
                emit();
                if (cleared) worker.shutdown();
            });
        });
    }
    private void updatePlaybackStatus(Operation op) {
        PlaybackController controller=op.playbackController;
        if (controller==null) { lastPlaybackStatus="PREPARING · controller not invoked"; return; }
        PlaybackController.Result result=controller.snapshot();
        lastPlaybackStatus=result.phase.name()+" · "+result.failure.name()+" · "+result.stopReason.name()
                +"\nstarted="+result.started+" · cleanup="+result.cleanupComplete
                +" · surfaceReleased="+(op.surface!=null && op.surface.released())
                +"\n"+result.width+" × "+result.height+" · format="+result.format
                +" · codec="+result.codecName+" · hardwareReported="+result.hardwareReported
                +"\nunits="+result.decodeUnitSubmissions+" · releasedForRender="+result.outputBuffersReleasedForRender
                +" · renderedCallbacks="+result.frameRenderedCallbacks
                +"\nHTTP calls="+result.httpCalls+" · response bytes="+result.httpBytes;
    }
    private static JSONObject playbackJson(PlaybackController.Result result) throws Exception {
        return new JSONObject().put("phase",result.phase.name()).put("failure",result.failure.name())
                .put("stopReason",result.stopReason.name()).put("started",result.started).put("cleanupComplete",result.cleanupComplete)
                .put("nativeStartResult",result.nativeStartResult).put("terminationCode",result.terminationCode)
                .put("width",result.width).put("height",result.height).put("format",result.format)
                .put("codecName",result.codecName).put("hardwareReported",result.hardwareReported).put("glRendererKnown",result.glRendererKnown)
                .put("decodeUnitSubmissions",result.decodeUnitSubmissions).put("outputBuffersReleasedForRender",result.outputBuffersReleasedForRender)
                .put("frameRenderedCallbacks",result.frameRenderedCallbacks).put("httpCalls",result.httpCalls)
                .put("httpBytes",result.httpBytes).put("stageCallbacks",result.stageCallbacks);
    }
    private void clearTimeout() {
        if (promptTimeout != null) main.removeCallbacks(promptTimeout);
        promptTimeout = null;
    }
    private void emit() {
        if (cleared) return;
        Operation op = active;
        ui.setValue(new UiState(message, nativeMessage, detail, identityState, securityLevel,
                op != null, nativeTried, op == null ? 0 : op.token,
                op != null && op.promptRequested, op != null && op.cancelled.get() && op.promptOutstanding,
                op == null ? PromptMode.NONE : op.promptMode,op != null && op.kind==Kind.PAIR,op != null && op.kind==Kind.PLAY,PlaybackController.requiresFreshProcess() || (NETWORK_BLOCKED.get() && "EVIDENCE_RESTART_REQUIRED".equals(EVIDENCE_BLOCK_REASON.get())),
                (RETIRING_NETWORK.get()!=null && RETIRING_NETWORK.get().failed()) || (op!=null && op.promptRetirement!=null && !op.workerRunning && !op.promptOutstanding && (op.evidenceFailed || !op.terminalEvidence)) || (op != null && op.kind==Kind.PLAY && !op.workerRunning && !op.promptOutstanding && !op.finalizing),lastPlaybackStatus,op!=null && op.kind==Kind.CATALOG,catalogEntries,catalogGeneration,selectedAppId,
                operationPhase(op),admissionBlocker(op),op==null || op.journal==null ? "" : op.journal.getName().replace(".json",""),
                op==null || op.authenticatedAt<0 ? -1 : op.authenticatedAt+30_000,
                op==null || (!op.promptRequested && !op.promptOutstanding) || op.promptDeadline<=0 ? -1 : op.promptDeadline,localNetworkGranted()));
    }

    private String operationPhase(Operation op) {
        if (op==null && RETIRING_NETWORK.get()!=null) return "CLEANUP_PENDING";
        if (op==null) return PlaybackController.requiresFreshProcess() ? "RETIRED" : "IDLE";
        if (op.evidenceFailed || (op.kind==Kind.PLAY && op.playbackController!=null && !op.workerRunning && !op.playbackController.retired())) return "CLEANUP_PENDING";
        if (op.cancelled.get()) return "STOPPING";
        if (op.promptOutstanding) return "AUTHORIZING";
        if (op.promptRequested) return "AUTHORIZATION_REQUIRED";
        return op.authenticatedAt>=0 ? "RUNNING" : "PREPARING";
    }
    private String admissionBlocker(Operation op) {
        if (op!=null) return op.evidenceFailed ? EVIDENCE_BLOCK_REASON.get() : "CLEANUP_PENDING".equals(operationPhase(op)) ? "CLEANUP_PENDING" : "OPERATION_ACTIVE";
        if (RETIRING_NETWORK.get()!=null) return RETIRING_NETWORK.get().failed() ? "EVIDENCE_UNAVAILABLE" : "CLEANUP_PENDING";
        if (NETWORK_BLOCKED.get()) return EVIDENCE_BLOCK_REASON.get();
        if (PlaybackController.requiresFreshProcess()) return "NATIVE_PROCESS_USED";
        if (!localNetworkGranted()) return "NETWORK_PERMISSION_REQUIRED";
        if (!"READY".equals(identityState)) return "IDENTITY_REQUIRED";
        if (selectedProfile==null) return "CATALOG_REQUIRED";
        return admissionFailure;
    }

    private static boolean network(Kind kind) { return kind==Kind.PAIR || kind==Kind.PLAY || kind==Kind.CATALOG; }
    private File directory(Operation op) throws IOException {
        File parent = op.kind == Kind.NATIVE ? getApplication().getFilesDir() : getApplication().getNoBackupFilesDir();
        File directory = new File(parent, op.kind == Kind.NATIVE ? "native-load" : op.kind==Kind.PAIR ? "pairing-journal" : op.kind==Kind.CATALOG ? "catalog-journal" : op.kind==Kind.PLAY ? "playback-journal" : op.kind==Kind.RECONCILE ? "network-reconciliation" : "identity-journal");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException();
        return directory;
    }
    private void reserve(Operation op) throws Exception {
        synchronized (JOURNAL_LOCK) {
            File directory = directory(op);
            String[] records = directory.list((dir,name) -> name.endsWith(".json"));
            EvidenceReason capacity=EvidenceReason.capacity(records==null ? -1 : records.length);
            if (capacity!=EvidenceReason.NONE) throw new EvidenceAdmissionException(capacity.name());
            if (network(op.kind)) NetworkEvidenceStore.check(getApplication().getNoBackupFilesDir(),PROCESS_REF);
            op.journal = new File(directory, UUID.randomUUID() + ".json");
            if (op.journal.exists()) throw new IOException();
            JSONObject record = baseRecord(op).put("status", "in-progress");
            if (network(op.kind)) {
                record.put("closed",false);
                op.networkMarker=new File(op.journal.getPath()+".pending");
                atomic(op.networkMarker,baseRecord(op).put("closed",false).put("status","reservation"));
            }
            atomic(op.journal, record);
        }
    }
    private JSONObject baseRecord(Operation op) throws Exception {
        JSONObject record=new JSONObject().put("schemaVersion", 1).put("buildRef", BuildConfig.QUALIFICATION_BUILD_REF)
                .put("nativeBuildRef", BuildConfig.NATIVE_BUILD_REF).put("version", BuildConfig.VERSION_NAME)
                .put("operation", op.kind.name()).put("requestedAuthenticationMode", op.promptMode.name())
                .put("startedElapsedMillis", op.started);
        if (network(op.kind)) record.put("processRef",PROCESS_REF);
        if (op.kind!=Kind.PLAY && op.kind!=Kind.CATALOG) record.put("decodedFrames",0);
        if (!network(op.kind)) record.put("hostConnections",0);
        return record;
    }
    private void persist(Operation op, Result result) throws Exception {
        synchronized (JOURNAL_LOCK) {
            // Retirement must acknowledge this write, never an earlier terminal record.
            if (op.kind == Kind.PAIR || op.kind == Kind.CATALOG) op.terminalEvidence = false;
            if (op.journal == null) throw new IOException();
            JSONObject record = baseRecord(op).put("observedElapsedMillis", SystemClock.elapsedRealtime())
                    .put("status", op.cancelled.get() || cleared || !visible ? "cancelled"
                            : !result.failure.isEmpty() ? "failed" : result.needsPrompt ? "authorization-required"
                            : op.kind == Kind.NATIVE ? "native-load-and-metadata-passed" : "completed")
                    .put("failureCode", op.evidenceFailed ? "EVIDENCE_UNAVAILABLE" : result.failure.isEmpty() ? JSONObject.NULL : result.failure)
                    .put("cancelReason", op.cancelled.get() ? op.cancelReason : JSONObject.NULL);
            if (op.kind==Kind.PAIR) {
                PairingController controller=op.controller;
                record.put("closed",StreamOperationLease.closed(op.cancelled.get(),result.needsPrompt,op.promptOutstanding))
                        .put("pairingCompleted",controller!=null && controller.pairingCompleted())
                        .put("clientTlsRead",result.clientTlsRead).put("applicationListBytes",result.applicationListBytes)
                        .put("pairingStage",controller==null ? "CREATED" : controller.stage().name())
                        .put("pairingFailureAt",controller==null ? "CREATED" : controller.failureAt().name());
            }
            if (op.kind==Kind.CATALOG) {
                CatalogController controller=op.catalogController;
                record.put("closed",StreamOperationLease.closed(op.cancelled.get(),result.needsPrompt,op.promptOutstanding))
                        .put("catalogStage",controller==null ? "CREATED" : controller.stage().name())
                        .put("catalogFailureAt",controller==null ? "CREATED" : controller.failureAt().name())
                        .put("catalogCompleted",result.catalogResult!=null)
                        .put("applicationCount",result.catalogResult==null ? 0 : result.catalogResult.entries.size())
                        .put("httpCalls",controller==null ? 0 : controller.calls())
                        .put("applicationListBytes",controller==null ? 0 : controller.responseBytes());
            }
            if (op.kind==Kind.PLAY) {
                PlaybackController.Result playback=op.playbackController==null ? null : op.playbackController.snapshot();
                record.put("closed",op.playbackTerminal).put("runAdmitted",op.playbackAdmitted)
                        .put("workerActionReturned",op.workReturned).put("controllerRunReturned",op.playbackReturned)
                        .put("selectedApplicationId",op.selectedAppId).put("catalogGeneration",op.catalogGeneration)
                        .put("surfaceGeneration",op.surfaceGeneration).put("surfaceDetached",op.surface!=null && op.surface.detached())
                        .put("surfaceReleased",op.surface!=null && op.surface.released())
                        .put("surfacePresentation",op.surface==null ? JSONObject.NULL : op.surface.presentationJson());
                if (playback!=null) record.put("playback",playbackJson(playback));
                else record.put("playback",JSONObject.NULL);
            }
            if (op.kind==Kind.RECONCILE && result.reconciled!=null) {
                record.put("reconciledRecords",result.reconciled).put("hostState","UNKNOWN")
                        .put("networkReplayed",false).put("localProcessInterruptionAcknowledged",true);
            }
            if (op.authenticatedAt >= 0) record.put("authenticatedAtElapsedMillis", op.authenticatedAt)
                    .put("authenticationType", op.authenticationType);
            if (result.failureStage != null) record.put("failureStage", result.failureStage)
                    .put("failureCauseClass", result.failureCauseClass)
                    .put("keyStoreErrorCode", result.keyStoreErrorCode == null ? JSONObject.NULL : result.keyStoreErrorCode);
            if (result.info != null) record.put("identityState", result.info.state.name()).put("securityLevel", result.info.securityLevel);
            if (result.qualification != null) {
                StreamIdentityStore.Qualification q = result.qualification;
                record.put("qualification", new JSONObject().put("securityLevel", q.securityLevel)
                        .put("authenticationHardwareEnforced", q.authenticationHardwareEnforced)
                        .put("privateEncodingUnavailable", q.privateEncodingUnavailable).put("signVerified", q.signVerified)
                        .put("keyBits", q.keyBits).put("certificateBytes", q.certificateBytes)
                        .put("signatureBytes", q.signatureBytes).put("certificateSha256", q.certificateSha256));
            }
            if (op.kind == Kind.NATIVE) record.put("durationNanos", result.nativeDuration)
                    .put("nativeMetadata", result.failure.isEmpty() && result.nativeMetadata != null ? result.nativeMetadata : JSONObject.NULL)
                    .put("failureClass", result.failure.isEmpty() ? JSONObject.NULL : result.failure);
            atomic(op.journal, record);
            if (network(op.kind) && record.optBoolean("closed") && op.networkMarker!=null) {
                if (op.networkMarker.exists() && !op.networkMarker.delete()) throw new IOException();
                if (op.networkMarker.exists()) throw new IOException();
            }
            if ((op.kind==Kind.CATALOG || op.kind==Kind.PAIR) && record.optBoolean("closed")) op.terminalEvidence=true;
        }
    }
    private static void atomic(File target, JSONObject record) throws Exception {
        byte[] bytes = (record.toString(2) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_RECORD_BYTES) throw new IOException();
        AtomicFile file = new AtomicFile(target);
        FileOutputStream stream = null;
        boolean finished = false;
        try {
            stream = file.startWrite(); stream.write(bytes); file.finishWrite(stream); finished = true;
            try (FileInputStream input = file.openRead()) {
                if (!Arrays.equals(bytes, input.readNBytes(MAX_RECORD_BYTES + 1))) throw new IOException();
            }
        } catch (Exception failure) {
            if (!finished && stream != null) file.failWrite(stream);
            throw failure;
        }
    }
    private static String code(Throwable failure) {
        if (failure instanceof StreamIdentityStore.IdentityException) return ((StreamIdentityStore.IdentityException) failure).code.name();
        if (failure instanceof EvidenceAdmissionException) return ((EvidenceAdmissionException)failure).reason;
        if (failure instanceof IOException) return "IOException";
        if (failure instanceof SecurityException) return "SecurityException";
        if (failure instanceof LinkageError) return "LinkageError";
        if (failure instanceof IllegalStateException) return "IllegalStateException";
        if (failure instanceof java.security.GeneralSecurityException) return "GeneralSecurityException";
        return "UNEXPECTED_FAILURE";
    }
    @Override protected void onCleared() {
        VISIBLE_MODELS.remove(this);
        cleared = true;
        visible = false;
        clearTimeout();
        Operation op = active;
        if (op!=null && (op.kind==Kind.CATALOG || op.kind==Kind.PAIR)) {
            revoke(op,"OWNER_CLEARED");
            RETIRING_NETWORK.set(op.promptRetirement);
            store.close(); op.promptRetirement.closeOwner();
            super.onCleared(); return;
        }
        if (op!=null && op.kind==Kind.PLAY) {
            revoke(op,"OWNER_CLEARED");
            if (playbackPoll!=null) main.removeCallbacks(playbackPoll); playbackPoll=null;
            store.close(); main.post(() -> finishPlayback(op));
            super.onCleared(); return;
        }
        if (op != null) {
            op.cancelReason = "OWNER_CLEARED";
            op.cancelled.set(true);
            store.cancel(op.attempt);
            if (op.controller!=null) op.controller.cancel();
            if (op.catalogController!=null) op.catalogController.cancel();
            if ((op.kind==Kind.PAIR || op.kind==Kind.CATALOG) && op.finalRetired && !op.promptOutstanding) {
                wipePin(op); PROCESS_OWNER.release(this);
                store.close(); worker.shutdown(); super.onCleared(); return;
            }
            // Always queue the final cancellation after the current worker. It may
            // already have posted completion when onCleared wins the main queue.
            worker.execute(() -> {
                try { if (!((op.kind==Kind.PAIR || op.kind==Kind.CATALOG) && op.finalRetired)) { if (op.journal == null) reserve(op); persist(op, op.lastResult == null ? new Result() : op.lastResult); } }
                catch (Exception ignored) { evidenceFailure(op); }
                finally {
                    wipePin(op);
                    if (op.kind!=Kind.CATALOG || op.terminalEvidence) {
                        op.finalRetired=true; if (!op.promptOutstanding) PROCESS_OWNER.release(this);
                    }
                }
            });
        }
        store.close();
        worker.shutdown(); // Do not interrupt an in-flight atomic write or claim it has retired.
        super.onCleared();
    }
}
