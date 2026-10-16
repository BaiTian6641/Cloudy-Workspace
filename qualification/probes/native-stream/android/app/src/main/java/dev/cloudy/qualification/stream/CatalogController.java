package dev.cloudy.qualification.stream;

import android.os.Looper;
import android.os.SystemClock;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvHTTP;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Explicit paired catalog read through the exact upstream HTTPS/parser APIs. */
public final class CatalogController implements NvHTTP.CloudyControl {
    public interface Guard { void check() throws IOException; }
    public enum Stage { CREATED, CLIENT_TLS, COMPLETED, FAILED }
    public static final class Result {
        public final java.util.List<CatalogEntry> entries;
        public final int applicationListBytes,httpCalls;
        private Result(java.util.List<CatalogEntry> entries,int bytes,int calls) {
            this.entries=entries;applicationListBytes=bytes;httpCalls=calls;
        }
    }
    private volatile int calls,responseBytes;
    private static final int MAX_BODY = 1024 * 1024;
    private final CatalogPeerProfile profile;
    private final LimelightCryptoProvider provider;
    private final Guard owner;
    private final CatalogLease lease;
    private final AtomicBoolean used = new AtomicBoolean();
    private volatile Stage stage = Stage.CREATED;
    private volatile Stage failureAt = Stage.CREATED;


    public CatalogController(CatalogPeerProfile profile, LimelightCryptoProvider qualifiedProvider, Guard owner,
            long authenticatedAtElapsedMillis, long maximumDurationMillis) {
        if (profile == null || qualifiedProvider == null || owner == null) throw new IllegalArgumentException("owner-required");
        this.profile = profile; provider = qualifiedProvider; this.owner = owner;
        lease = new CatalogLease(SystemClock::elapsedRealtime, authenticatedAtElapsedMillis, maximumDurationMillis);
    }
    public int calls() { return calls; }
    public int responseBytes() { return responseBytes; }
    public Stage stage() { return stage; }
    public Stage failureAt() { return failureAt; }
    /** Nonblocking OkHttp cancellation. Worker retains ownership until run() returns. */
    public void cancel() { lease.cancel(); }
    @Override public void check() throws IOException {
        lease.remaining(); owner.check();
        try { provider.getClientCertificate(); }
        catch (RuntimeException unavailable) { throw new IOException("identity-unavailable"); }
    }
    @Override public void verifyLeaf(X509Certificate certificate) throws CertificateException {
        profile.verifyLeaf(certificate);
    }
    /** Worker-only; reads exactly the paired HTTPS application catalog, never launches. */
    public Result run() throws IOException {
        if (Looper.myLooper()==Looper.getMainLooper()) throw new IllegalStateException("worker-required");
        if (!used.compareAndSet(false,true)) throw new IllegalStateException("single-use");
        try {
            check(); stage=Stage.CLIENT_TLS;
            NvHTTP http=new NvHTTP(new ComputerDetails.AddressTuple(profile.address,profile.httpPort),
                    profile.httpsPort,profile.id,profile.certificate,provider,this);
            String xml=http.getAppListRaw();
            java.util.List<com.limelight.nvstream.http.NvApp> parsed=NvHTTP.cloudyGetAppListByReader(new java.io.StringReader(xml));
            java.util.List<CatalogEntry> entries=CatalogPolicy.entries(parsed);
            check();stage=Stage.COMPLETED;
            return new Result(java.util.List.copyOf(entries),responseBytes,calls);
        } catch (Exception failure) {
            failureAt=stage;stage=Stage.FAILED;throw new IOException("catalog-attempt-failed");
        } finally { lease.cancel(); }
    }
    @Override public ResponseBody execute(OkHttpClient original, Request request) throws IOException {
        check();
        if (!request.url().isHttps() || !request.url().host().equals(profile.address)
                || request.url().port()!=profile.httpsPort || !request.method().equals("GET")
                || !request.url().encodedPath().equals("/applist")) throw new IOException("catalog-request-rejected");
        long remaining = lease.remaining();
        OkHttpClient client = original.newBuilder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).callTimeout(remaining, TimeUnit.MILLISECONDS)
                .connectTimeout(Math.min(5000, remaining), TimeUnit.MILLISECONDS)
                .readTimeout(remaining, TimeUnit.MILLISECONDS)
                .writeTimeout(Math.min(5000, remaining), TimeUnit.MILLISECONDS).build();
        Call call = client.newCall(request);
        CatalogLease.ActiveCall active = call::cancel;
        lease.register(active);
        calls++;
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
                    responseBytes+=size;
                }
            }
            check();
            return ResponseBody.create(bytes.toByteArray(), MediaType.get("application/xml; charset=utf-8"));
        } finally { lease.retire(active); }
    }
}
