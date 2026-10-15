package dev.cloudy.qualification.stream.pairing;

import android.net.InetAddresses;
import android.os.Looper;
import android.os.SystemClock;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Finite real upstream pairing plus one client-certificate HTTPS read; no playback. */
public final class PairingController implements NvHTTP.CloudyControl {
    public interface Guard { void check() throws IOException; }
    public enum Stage { CREATED, SERVER_INFO, PAIRING, CLIENT_TLS, COMPLETED, FAILED }
    public static final class Profile {
        final String id, address;
        final int httpPort, httpsPort;
        final X509Certificate certificate;
        final LeafPolicy leaf;
        public Profile(String id, String numericAddress, int httpPort, int httpsPort,
                X509Certificate certificate, byte[] sha256) throws CertificateException {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || httpPort < 1 || httpPort > 65535
                    || httpsPort < 1 || httpsPort > 65535 || httpPort == httpsPort) {
                throw new IllegalArgumentException("invalid-profile");
            }
            this.id = id;
            address = new okhttp3.HttpUrl.Builder().scheme("http")
                    .host(InetAddresses.parseNumericAddress(numericAddress).getHostAddress()).build().host();
            this.httpPort = httpPort; this.httpsPort = httpsPort;
            if (certificate == null) throw new CertificateException("certificate-required");
            // Freeze public bytes rather than retaining a caller's mutable certificate subclass.
            this.certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certificate.getEncoded()));
            leaf = new LeafPolicy(this.certificate, sha256);
        }
    }
    public static final class Result {
        public final boolean paired, clientTlsRead;
        public final int applicationListBytes;
        private Result(int bytes) { paired = true; clientTlsRead = true; applicationListBytes = bytes; }
    }
    private static final int MAX_BODY = 1024 * 1024;
    private final Profile profile;
    private final LimelightCryptoProvider provider;
    private final Guard owner;
    private final AttemptLease lease;
    private final AtomicBoolean used = new AtomicBoolean();
    private volatile Stage stage = Stage.CREATED;
    private volatile Stage failureAt = Stage.CREATED;
    private volatile boolean pairingCompleted;

    public PairingController(Profile profile, LimelightCryptoProvider qualifiedProvider, Guard owner,
            long authenticatedAtElapsedMillis, long maximumDurationMillis) {
        if (profile == null || qualifiedProvider == null || owner == null) throw new IllegalArgumentException("owner-required");
        this.profile = profile; provider = qualifiedProvider; this.owner = owner;
        lease = new AttemptLease(SystemClock::elapsedRealtime, authenticatedAtElapsedMillis, maximumDurationMillis);
    }
    public Stage stage() { return stage; }
    public Stage failureAt() { return failureAt; }
    /** Enrollment may remain real even if the later HTTPS read fails. */
    public boolean pairingCompleted() { return pairingCompleted; }
    /** Nonblocking OkHttp cancellation. Worker retains ownership until run() returns. */
    public void cancel() { lease.cancel(); }
    @Override public void check() throws IOException {
        lease.remaining(); owner.check();
        try { provider.getClientCertificate(); }
        catch (RuntimeException unavailable) { throw new IOException("identity-unavailable"); }
    }
    @Override public void verifyLeaf(X509Certificate certificate) throws CertificateException {
        profile.leaf.verify(certificate);
    }
    /** Caller runs off main, passes four-digit memory-only PIN, and retires before replacement. */
    public Result run(char[] pin) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("worker-required");
        if (!used.compareAndSet(false, true)) throw new IllegalStateException("single-use");
        char[] owned = pin == null ? new char[0] : pin.clone();
        try {
            if (owned.length != 4) throw new IOException("invalid-pin");
            for (char digit : owned) if (digit < '0' || digit > '9') throw new IOException("invalid-pin");
            check();
            NvHTTP http = new NvHTTP(new ComputerDetails.AddressTuple(profile.address, profile.httpPort),
                    profile.httpsPort, profile.id, profile.certificate, provider, this);
            stage = Stage.SERVER_INFO;
            String serverInfo = http.cloudyBootstrapServerInfo();
            check(); stage = Stage.PAIRING;
            PairingManager.PairState outcome = http.getPairingManager().pair(serverInfo, new String(owned));
            if (outcome != PairingManager.PairState.PAIRED) throw new IOException("pairing-not-completed");
            pairingCompleted = true;
            check(); stage = Stage.CLIENT_TLS;
            String apps = http.getAppListRaw();
            // Parse the real response, including upstream protocol status validation.
            NvHTTP.cloudyGetAppListByReader(new java.io.StringReader(apps));
            check(); stage = Stage.COMPLETED;
            return new Result(apps.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        } catch (Exception failure) {
            failureAt = stage; stage = Stage.FAILED;
            // Never expose upstream URL/query/body/exception messages to UI or logs.
            throw new IOException("pairing-attempt-failed");
        } finally { Arrays.fill(owned, '\0'); lease.cancel(); }
    }
    @Override public ResponseBody execute(OkHttpClient original, Request request) throws IOException {
        check();
        String host = request.url().host();
        if (!host.equals(profile.address) || !request.method().equals("GET")
                || !(request.url().isHttps() ? request.url().port() == profile.httpsPort
                    : request.url().port() == profile.httpPort)) throw new IOException("endpoint-mismatch");
        long remaining = lease.remaining();
        OkHttpClient client = original.newBuilder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).callTimeout(remaining, TimeUnit.MILLISECONDS)
                .connectTimeout(Math.min(5000, remaining), TimeUnit.MILLISECONDS)
                .readTimeout(remaining, TimeUnit.MILLISECONDS)
                .writeTimeout(Math.min(5000, remaining), TimeUnit.MILLISECONDS).build();
        Call call = client.newCall(request);
        AttemptLease.ActiveCall active = call::cancel;
        lease.register(active);
        try (Response response = call.execute()) {
            check();
            if (!response.isSuccessful() || response.body() == null) throw new IOException("http-failed");
            ResponseBody body = response.body();
            if (body.contentLength() > MAX_BODY) throw new IOException("body-limit");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            try (InputStream input = body.byteStream()) {
                for (int size; (size = input.read(buffer)) != -1;) {
                    check();
                    if (size > MAX_BODY - bytes.size()) throw new IOException("body-limit");
                    bytes.write(buffer, 0, size);
                }
            }
            check();
            return ResponseBody.create(bytes.toByteArray(), MediaType.get("application/xml; charset=utf-8"));
        } finally { lease.retire(active); }
    }
}
