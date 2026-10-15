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
        private UiState(int message, int nativeMessage, String detail, String identityState,
                String securityLevel, boolean busy, boolean nativeTried, long promptToken,
                boolean promptRequested, boolean cancelPrompt, PromptMode promptMode, boolean pairing) {
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
            this.pairing = pairing;
        }
    }
    private enum Kind { REFRESH, CREATE, AUTHORIZE, DELETE, NATIVE, PAIR }
    private static final class Operation {
        final long token;
        final Kind kind;
        final PromptMode promptMode;
        final StreamIdentityStore.Attempt attempt;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final long started = SystemClock.elapsedRealtime();
        volatile String cancelReason = "OPERATOR";
        File journal;
        char[] pin;
        PairingController.Profile profile;
        volatile PairingController controller;
        volatile boolean finalRetired;
        volatile boolean evidenceFailed;
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
    }
    private interface Action { Result run() throws Exception; }
    private static final StreamOperationLease PROCESS_OWNER = new StreamOperationLease();
    private static final AtomicBoolean NETWORK_BLOCKED = new AtomicBoolean();
    private static final Object JOURNAL_LOCK = new Object();
    private static final long PROMPT_MILLIS = 120_000;
    private static final int MAX_RECORD_BYTES = 32_768;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final StreamIdentityStore store;
    private final MutableLiveData<UiState> ui = new MutableLiveData<>();
    private volatile boolean visible;
    private volatile boolean cleared;
    private Operation active;
    private long sequence;
    private boolean initiallyRefreshed;
    private boolean nativeTried;
    private String identityState = "UNKNOWN";
    private String securityLevel = "unavailable";
    private int message = R.string.cloudy_identity_ready;
    private int nativeMessage = R.string.cloudy_stream_ready;
    private String detail = "";
    private Runnable promptTimeout;

    public StreamProbeViewModel(Application application) {
        super(application);
        store = new StreamIdentityStore(application);
        emit();
    }
    public LiveData<UiState> state() { return ui; }

    public void onVisible() {
        visible = true;
        if (!initiallyRefreshed) { initiallyRefreshed = true; refreshIdentity(); }
    }
    public void onHidden() { visible = false; cancel("BACKGROUND"); }

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
        if (!visible || cleared || active != null) return null;
        if ((kind==Kind.PAIR && NETWORK_BLOCKED.get()) || !PROCESS_OWNER.claim(this)) {
            message=R.string.cloudy_identity_failed; detail=NETWORK_BLOCKED.get() ? "PAIRING_EVIDENCE_BLOCKED" : "PROCESS_OWNER_BUSY"; emit(); return null;
        }
        try {
            StreamIdentityStore.Attempt attempt = kind == Kind.CREATE || kind == Kind.AUTHORIZE || kind == Kind.PAIR ? store.newAttempt() : null;
            Operation op = new Operation(++sequence, kind, attempt, mode);
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
        op.workerRunning = true;
        worker.execute(() -> {
            Result result = new Result();
            try {
                if (op.journal == null) {
                    try { reserve(op); }
                    catch (Exception failure) {
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
                if (op.kind==Kind.PAIR && op.controller!=null && op.lastResult!=null) result=op.lastResult;
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
            if (op.kind==Kind.PAIR && !op.evidenceFailed && !result.needsPrompt && !op.promptOutstanding) op.finalRetired=true;
            Result completed = result;
            main.post(() -> completed(op, completed));
        });
    }

    private void completed(Operation op, Result result) {
        if (cleared || active != op) return;
        op.workerRunning = false;
        if (result.info != null) {
            identityState = result.info.state.name();
            securityLevel = result.info.securityLevel;
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
        if (op.kind == Kind.NATIVE) nativeMessage = result.failure.isEmpty()
                ? R.string.cloudy_stream_success : R.string.cloudy_stream_failed;
        message = result.failure.isEmpty() ? (op.kind == Kind.PAIR ? R.string.cloudy_pair_success : op.kind == Kind.DELETE ? R.string.cloudy_identity_deleted
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
        if (cleared) { if (op.finalRetired) PROCESS_OWNER.release(this); return; }
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
        if (op.kind==Kind.PAIR) {
            promptTimeout=() -> { if (active==op) cancel("PAIRING_AUTH_DEADLINE"); };
            main.postDelayed(promptTimeout,Math.max(0,30_000-(SystemClock.elapsedRealtime()-authenticatedAtElapsedMillis)));
        }
        message = R.string.cloudy_identity_working;
        emit();
        submit(op, () -> {
            Result result = new Result();
            if (op.kind==Kind.PAIR) checkPairing(op);
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
            result.info = store.prepare();
            return result;
        });
    }
    private boolean localNetworkGranted() {
        return Build.VERSION.SDK_INT<37 || getApplication().checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)==PackageManager.PERMISSION_GRANTED;
    }
    private void checkPairing(Operation op) throws IOException {
        if (!localNetworkGranted() || !visible || cleared || op.cancelled.get() || !store.checkCurrent(op.attempt)
                || op.authenticatedAt<0 || SystemClock.elapsedRealtime()-op.authenticatedAt>=30_000) throw new IOException("pairing-owner-unavailable");
    }
    private void evidenceFailure(Operation op) {
        op.evidenceFailed=true;
        NETWORK_BLOCKED.set(true);
        if (op.controller!=null) op.controller.cancel();
        store.cancel(op.attempt);
    }
    private static void wipePin(Operation op) { if (op.pin!=null) { Arrays.fill(op.pin,'\0'); op.pin=null; } }
    public void authenticationError(long token, int errorCode) {
        Operation op = active;
        if (op == null || op.token != token || !op.promptOutstanding) return;
        op.promptOutstanding = false;
        if (cleared) { if (op.finalRetired) PROCESS_OWNER.release(this); return; }
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
        op.promptRequested = false;
        clearTimeout();
        message = R.string.cloudy_identity_cancelling;
        detail = op.cancelReason;
        emit();
    }
    private void finishCancellation(Operation op) {
        if (op.kind==Kind.PAIR && op.finalRetired && !op.workerRunning && !op.promptOutstanding) {
            message=R.string.cloudy_identity_cancelled; detail=op.cancelReason; retire(op); return;
        }
        if (op.workerRunning || op.promptOutstanding) { emit(); return; }
        op.workerRunning = true;
        worker.execute(() -> {
            String failure = "";
            try { if (op.journal == null) reserve(op); persist(op, op.lastResult == null ? new Result() : op.lastResult); }
            catch (Exception error) { evidenceFailure(op); failure = "EVIDENCE_UNAVAILABLE"; }
            String finalFailure = failure;
            main.post(() -> {
                if (cleared || active != op) return;
                op.workerRunning = false;
                if (op.kind == Kind.NATIVE) nativeMessage = R.string.cloudy_stream_cancelled;
                message = finalFailure.isEmpty() ? R.string.cloudy_identity_cancelled : R.string.cloudy_identity_failed;
                detail = finalFailure.isEmpty() ? op.cancelReason : finalFailure;
                retire(op);
            });
        });
    }
    private void retire(Operation op) {
        clearTimeout();
        store.cancel(op.attempt);
        if (op.controller!=null) op.controller.cancel();
        wipePin(op);
        PROCESS_OWNER.release(this);
        if (active == op) active = null;
        emit();
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
                op == null ? PromptMode.NONE : op.promptMode,op != null && op.kind==Kind.PAIR));
    }

    private File directory(Operation op) throws IOException {
        File parent = op.kind == Kind.NATIVE ? getApplication().getFilesDir() : getApplication().getNoBackupFilesDir();
        File directory = new File(parent, op.kind == Kind.NATIVE ? "native-load" : op.kind==Kind.PAIR ? "pairing-journal" : "identity-journal");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException();
        return directory;
    }
    private void reserve(Operation op) throws Exception {
        synchronized (JOURNAL_LOCK) {
            File directory = directory(op);
            String[] records = directory.list();
            if (records == null || records.length >= 16) throw new IOException();
            if (op.kind==Kind.PAIR) for (String name:records) {
                File previous=new File(directory,name);
                if (!name.endsWith(".json") || !previous.isFile() || previous.length()>MAX_RECORD_BYTES) throw new IOException();
                try (FileInputStream input=new FileInputStream(previous)) {
                    byte[] bytes=input.readNBytes(MAX_RECORD_BYTES+1);
                    JSONObject prior=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                    if (bytes.length>MAX_RECORD_BYTES || !prior.getBoolean("closed") || "EVIDENCE_UNAVAILABLE".equals(prior.optString("failureCode"))) throw new IOException();
                }
            }
            op.journal = new File(directory, UUID.randomUUID() + ".json");
            if (op.journal.exists()) throw new IOException();
            JSONObject record = baseRecord(op).put("status", "in-progress");
            if (op.kind==Kind.PAIR) record.put("closed",false);
            atomic(op.journal, record);
        }
    }
    private JSONObject baseRecord(Operation op) throws Exception {
        JSONObject record=new JSONObject().put("schemaVersion", 1).put("buildRef", BuildConfig.QUALIFICATION_BUILD_REF)
                .put("nativeBuildRef", BuildConfig.NATIVE_BUILD_REF).put("version", BuildConfig.VERSION_NAME)
                .put("operation", op.kind.name()).put("requestedAuthenticationMode", op.promptMode.name())
                .put("startedElapsedMillis", op.started)
                .put("decodedFrames", 0);
        if (op.kind!=Kind.PAIR) record.put("hostConnections",0);
        return record;
    }
    private void persist(Operation op, Result result) throws Exception {
        synchronized (JOURNAL_LOCK) {
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
        if (failure instanceof IOException) return "IOException";
        if (failure instanceof SecurityException) return "SecurityException";
        if (failure instanceof LinkageError) return "LinkageError";
        if (failure instanceof IllegalStateException) return "IllegalStateException";
        if (failure instanceof java.security.GeneralSecurityException) return "GeneralSecurityException";
        return "UNEXPECTED_FAILURE";
    }
    @Override protected void onCleared() {
        cleared = true;
        visible = false;
        clearTimeout();
        Operation op = active;
        if (op != null) {
            op.cancelReason = "OWNER_CLEARED";
            op.cancelled.set(true);
            store.cancel(op.attempt);
            if (op.controller!=null) op.controller.cancel();
            if (op.kind==Kind.PAIR && op.finalRetired && !op.promptOutstanding) {
                wipePin(op); PROCESS_OWNER.release(this);
                store.close(); worker.shutdown(); super.onCleared(); return;
            }
            // Always queue the final cancellation after the current worker. It may
            // already have posted completion when onCleared wins the main queue.
            worker.execute(() -> {
                try { if (!(op.kind==Kind.PAIR && op.finalRetired)) { if (op.journal == null) reserve(op); persist(op, op.lastResult == null ? new Result() : op.lastResult); } }
                catch (Exception ignored) { evidenceFailure(op); }
                finally { wipePin(op); op.finalRetired=true; if (!op.promptOutstanding) PROCESS_OWNER.release(this); }
            });
        }
        store.close();
        worker.shutdown(); // Do not interrupt an in-flight atomic write or claim it has retired.
        super.onCleared();
    }
}
