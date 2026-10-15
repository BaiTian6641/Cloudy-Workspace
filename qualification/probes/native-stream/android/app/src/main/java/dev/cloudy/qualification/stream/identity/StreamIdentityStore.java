package dev.cloudy.qualification.stream.identity;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.AtomicFile;
import android.util.Base64;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.json.JSONObject;

/** App-local diagnostic identity, not a general vault or an authenticated TLS result.
 * Blocking methods belong to one worker. Main-thread cancellation never takes IO_LOCK.
 */
public final class StreamIdentityStore implements AutoCloseable {
    public enum State { ABSENT, ENROLLMENT_PENDING, READY }
    public enum Code {
        UNSUPPORTED_PLATFORM, MAIN_THREAD, CLOSED, CANCELLED, STALE_ATTEMPT,
        DEVICE_LOCK_REQUIRED, DEVICE_LOCKED, AUTH_REQUIRED, NOT_QUALIFIED,
        IDENTITY_ABSENT, IDENTITY_EXISTS, ENROLLMENT_PENDING, INVALID_RECORD,
        KEY_MISSING, KEY_INVALIDATED, KEY_POLICY, ORPHANED_IDENTITY,
        CERTIFICATE_INVALID, STORAGE, CRYPTO
    }
    public static final class IdentityException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public final Code code;
        public final String stage;
        public final String causeClass;
        public final Integer keyStoreErrorCode;
        private IdentityException(Code code) {
            this(code, FailureStage.GUARD, "IdentityException", null);
        }
        private IdentityException(Code code, FailureStage stage, String causeClass, Integer keyStoreErrorCode) {
            super(code.name());
            this.code = code;
            this.stage = stage.label;
            this.causeClass = causeClass;
            this.keyStoreErrorCode = keyStoreErrorCode;
        }
    }
    private enum FailureStage {
        GUARD("guard"), AUTHORIZATION("authorization"), KEY_LOAD("key-load"), KEY_POLICY("key-policy"),
        KEY_GENERATE("key-generate"), STORAGE("storage"), CERTIFICATE_PREPARE("certificate-prepare"),
        CERTIFICATE_SIGN_INIT("certificate-sign-init"), CERTIFICATE_SIGN("certificate-sign"),
        CERTIFICATE_VALIDATE("certificate-validate"),
        PRECERTIFICATE_SIGN_INIT("precertificate-sign-init"), PRECERTIFICATE_SIGN("precertificate-sign"),
        PRECERTIFICATE_VERIFY("precertificate-verify"), CHALLENGE_GUARD("challenge-guard"),
        PRIVATE_ENCODING("private-encoding"), CHALLENGE_SIGN_INIT("challenge-sign-init"),
        CHALLENGE_SIGN("challenge-sign"), CHALLENGE_VERIFY("challenge-verify"),
        QUALIFICATION_DETAILS("qualification-details"), PROVIDER_PREPARE("provider-prepare"),
        DELETE("delete");
        final String label;
        FailureStage(String label) { this.label = label; }
    }
    public static final class IdentityInfo {
        public final State state;
        public final String securityLevel;
        public final int keyBits;
        public final int authWindowSeconds;
        private IdentityInfo(State state, String securityLevel) {
            this.state = state;
            this.securityLevel = securityLevel;
            keyBits = state == State.ABSENT ? 0 : 2048;
            authWindowSeconds = 30;
        }
    }
    public static final class Qualification {
        public final String securityLevel;
        public final boolean authenticationHardwareEnforced;
        public final boolean privateEncodingUnavailable;
        public final boolean signVerified;
        public final int keyBits;
        public final int certificateBytes;
        public final int signatureBytes;
        public final String certificateSha256;
        private Qualification(KeyInfo info, X509Certificate cert, int signatureBytes) throws Exception {
            securityLevel = level(info);
            authenticationHardwareEnforced = info.isUserAuthenticationRequirementEnforcedBySecureHardware();
            privateEncodingUnavailable = true;
            signVerified = true;
            keyBits = info.getKeySize();
            certificateBytes = cert.getEncoded().length;
            this.signatureBytes = signatureBytes;
            certificateSha256 = hex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        }
    }
    public static final class Attempt {
        private final StreamIdentityStore owner;
        private final IdentityAuthorization authorization = new IdentityAuthorization();
        private volatile Prepared prepared;
        private Attempt(StreamIdentityStore owner) { this.owner = owner; }
    }
    private static final class Prepared {
        final PrivateKey key;
        final X509Certificate certificate;
        final byte[] pem;
        Prepared(PrivateKey key, X509Certificate certificate) throws Exception {
            this.key = key;
            this.certificate = certificate;
            String encoded = Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP);
            StringBuilder text = new StringBuilder("-----BEGIN CERTIFICATE-----\n");
            for (int start = 0; start < encoded.length(); start += 64) {
                text.append(encoded, start, Math.min(start + 64, encoded.length())).append('\n');
            }
            pem = text.append("-----END CERTIFICATE-----\n").toString().getBytes(StandardCharsets.US_ASCII);
        }
    }

    private static final Object IO_LOCK = new Object();
    private static final AtomicReference<Attempt> ACTIVE = new AtomicReference<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_RECORD_BYTES = 32_768;
    private static final int MAX_CERTIFICATE_BYTES = 8_192;
    private static final int AUTH_TYPES = KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL;
    private final Context app;
    private final String prefix;
    private final AtomicBoolean closed = new AtomicBoolean();

    public StreamIdentityStore(Context context) {
        app = context.getApplicationContext();
        prefix = app.getPackageName() + ".stream-identity.v1.";
    }

    /** Nonblocking: a new owner immediately makes all older getters/continuations stale. */
    public Attempt newAttempt() {
        ensureOpen();
        Attempt next = new Attempt(this);
        Attempt previous = ACTIVE.getAndSet(next);
        if (previous != null) previous.owner.cancel(previous);
        if (closed.get()) { next.authorization.cancel(); throw error(Code.CLOSED); }
        return next;
    }

    public void cancel(Attempt attempt) {
        if (attempt != null && attempt.owner == this) {
            attempt.authorization.cancel();
            attempt.prepared = null;
        }
    }

    @Override public void close() {
        closed.set(true);
        Attempt current = ACTIVE.get();
        if (current != null && current.owner == this) cancel(current);
    }

    public IdentityInfo prepare() {
        worker();
        synchronized (IO_LOCK) {
            FailureStage stage = FailureStage.GUARD;
            try {
                ensureOpen();
                stage = FailureStage.KEY_LOAD;
                KeyStore store = keyStore();
                stage = FailureStage.STORAGE;
                JSONObject record = readRecord();
                if (record == null) {
                    if (hasOwnedAlias(store)) throw error(Code.ORPHANED_IDENTITY);
                    return new IdentityInfo(State.ABSENT, "unavailable");
                }
                String alias = record.getString("alias");
                stage = FailureStage.KEY_LOAD;
                PrivateKey key = privateKey(store, alias);
                stage = FailureStage.KEY_POLICY;
                KeyInfo info = keyInfo(key);
                if (record.getString("state").equals("pending")) {
                    return new IdentityInfo(State.ENROLLMENT_PENDING, level(info));
                }
                stage = FailureStage.CERTIFICATE_VALIDATE;
                certificate(store, record);
                return new IdentityInfo(State.READY, level(info));
            } catch (Exception failure) { throw failure(failure, stage); }
        }
    }

    /** Explicit creation only. Persist pending intent before generation to expose interruptions. */
    public IdentityInfo beginEnrollment() {
        worker();
        synchronized (IO_LOCK) {
            FailureStage stage = FailureStage.GUARD;
            try {
                ensureOpen();
                requireUnlocked();
                stage = FailureStage.KEY_LOAD;
                KeyStore store = keyStore();
                if (readRecord() != null) throw error(Code.IDENTITY_EXISTS);
                if (hasOwnedAlias(store)) throw error(Code.ORPHANED_IDENTITY);
                String alias = prefix + UUID.randomUUID();
                stage = FailureStage.STORAGE;
                writeRecord(new JSONObject().put("schemaVersion", 1).put("state", "pending").put("alias", alias));
                ensureOpen();
                stage = FailureStage.KEY_GENERATE;
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
                generator.initialize(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                        .setKeySize(2048)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                        .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                                KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                        // TLS opaque RSA may perform a raw SIGN through Cipher private-key ENCRYPT_MODE.
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(true).setUserAuthenticationParameters(30, AUTH_TYPES)
                        .setUnlockedDeviceRequired(true)
                        .setCertificateSubject(new X500Principal("CN=Cloudy stream pending identity"))
                        .build());
                generator.generateKeyPair();
                ensureOpen();
                stage = FailureStage.KEY_LOAD;
                PrivateKey key = privateKey(store, alias);
                stage = FailureStage.KEY_POLICY;
                return new IdentityInfo(State.ENROLLMENT_PENDING, level(keyInfo(key)));
            } catch (Exception failure) { throw failure(failure, stage); }
        }
    }

    /** Call only with the timestamp captured at an actual successful local authentication callback. */
    public Qualification completeEnrollment(Attempt attempt, long authenticatedAtElapsedMillis) {
        worker();
        synchronized (IO_LOCK) {
            FailureStage stage = FailureStage.GUARD;
            try {
                ensureOpen();
                stage = FailureStage.AUTHORIZATION;
                authorize(attempt, authenticatedAtElapsedMillis);
                stage = FailureStage.KEY_LOAD;
                KeyStore store = keyStore();
                stage = FailureStage.STORAGE;
                JSONObject record = requiredRecord();
                if (!record.getString("state").equals("pending")) throw error(Code.IDENTITY_EXISTS);
                stage = FailureStage.KEY_LOAD;
                PrivateKey key = privateKey(store, record.getString("alias"));
                stage = FailureStage.KEY_POLICY;
                KeyInfo info = keyInfo(key);
                // Direct JCA control precedes BC certificate construction, using
                // only the initial certificate's public key, not its placeholder signature.
                preCertificateControl(attempt, key, store.getCertificate(record.getString("alias")).getPublicKey());
                stage = FailureStage.CERTIFICATE_PREPARE;
                Date now = new Date();
                X500Name name = new X500Name("CN=Cloudy stream client");
                byte[] publicKey = store.getCertificate(record.getString("alias")).getPublicKey().getEncoded();
                X509v3CertificateBuilder builder = new X509v3CertificateBuilder(name,
                        new BigInteger(128, RANDOM).setBit(127), new Date(now.getTime() - 60_000),
                        new Date(now.getTime() + 365L * 24 * 60 * 60 * 1000), name,
                        SubjectPublicKeyInfo.getInstance(publicKey));
                // Never force BC private signing: opaque keys require JCA's accepting provider.
                stage = FailureStage.CERTIFICATE_SIGN_INIT;
                ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(key);
                stage = FailureStage.CERTIFICATE_SIGN;
                byte[] der = builder.build(signer).getEncoded();
                stage = FailureStage.CERTIFICATE_VALIDATE;
                X509Certificate certificate = parseCertificate(der);
                validateCertificate(store, record.getString("alias"), certificate);
                Qualification result = signQualification(attempt, key, info, certificate);
                record.put("state", "ready").put("certificate", Base64.encodeToString(der, Base64.NO_WRAP));
                guard(attempt, false);
                // A cancellation concurrent with this already-started public atomic commit may
                // leave enrollment ready, but never grants the cancelled attempt a provider.
                stage = FailureStage.STORAGE;
                writeRecord(record);
                guard(attempt, false);
                stage = FailureStage.PROVIDER_PREPARE;
                attempt.prepared = new Prepared(key, certificate);
                guard(attempt, true);
                return result;
            } catch (Exception failure) { cancelPrepared(attempt); throw failure(failure, stage); }
        }
    }

    public Qualification qualify(Attempt attempt, long authenticatedAtElapsedMillis) {
        worker();
        synchronized (IO_LOCK) {
            FailureStage stage = FailureStage.GUARD;
            try {
                ensureOpen();
                stage = FailureStage.AUTHORIZATION;
                authorize(attempt, authenticatedAtElapsedMillis);
                stage = FailureStage.KEY_LOAD;
                KeyStore store = keyStore();
                stage = FailureStage.STORAGE;
                JSONObject record = requiredRecord();
                if (!record.getString("state").equals("ready")) throw error(Code.ENROLLMENT_PENDING);
                stage = FailureStage.KEY_LOAD;
                PrivateKey key = privateKey(store, record.getString("alias"));
                stage = FailureStage.CERTIFICATE_VALIDATE;
                X509Certificate certificate = certificate(store, record);
                stage = FailureStage.KEY_POLICY;
                KeyInfo info = keyInfo(key);
                Qualification result = signQualification(attempt, key, info, certificate);
                guard(attempt, false);
                stage = FailureStage.PROVIDER_PREPARE;
                attempt.prepared = new Prepared(key, certificate);
                guard(attempt, true);
                return result;
            } catch (Exception failure) { cancelPrepared(attempt); throw failure(failure, stage); }
        }
    }

    /** No prompts, regeneration or disk IO. Cached opaque handles are not cryptographically revoked. */
    public LimelightCryptoProvider provider(Attempt attempt) {
        guard(attempt, true);
        return new LimelightCryptoProvider() {
            private Prepared current() {
                guard(attempt, true);
                Prepared value = attempt.prepared;
                if (value == null) throw error(Code.NOT_QUALIFIED);
                try { value.certificate.checkValidity(); }
                catch (java.security.cert.CertificateException invalid) { throw error(Code.CERTIFICATE_INVALID); }
                return value;
            }
            @Override public X509Certificate getClientCertificate() { return current().certificate; }
            @Override public PrivateKey getClientPrivateKey() { return current().key; }
            @Override public byte[] getPemEncodedClientCertificate() { return current().pem.clone(); }
            @Override public String encodeBase64String(byte[] data) {
                current();
                return Base64.encodeToString(data, Base64.NO_WRAP);
            }
        };
    }

    /** Deliberate local deletion only; it does not send a host-side unpair request. */
    public void deleteIdentity() {
        worker();
        Attempt current = ACTIVE.get();
        if (current != null) current.owner.cancel(current);
        synchronized (IO_LOCK) {
            FailureStage stage = FailureStage.GUARD;
            try {
                ensureOpen();
                Attempt active = ACTIVE.get();
                if (active != null) active.owner.cancel(active);
                stage = FailureStage.KEY_LOAD;
                KeyStore store = keyStore();
                stage = FailureStage.DELETE;
                Enumeration<String> aliases = store.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    if (ownedAlias(alias)) store.deleteEntry(alias);
                }
                if (hasOwnedAlias(store)) throw error(Code.STORAGE);
                AtomicFile file = recordFile();
                file.delete();
                if (file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists()
                        || new File(file.getBaseFile() + ".new").exists()) throw error(Code.STORAGE);
            } catch (Exception failure) { throw failure(failure, stage); }
        }
    }

    /** Nonblocking owner-generation check; does not authorize a cryptographic operation. */
    public boolean checkCurrent(Attempt attempt) {
        return !closed.get() && attempt != null && attempt.owner == this
                && ACTIVE.get() == attempt && !attempt.authorization.isCancelled();
    }

    private void authorize(Attempt attempt, long authenticatedAt) {
        guardOwner(attempt);
        requireUnlocked();
        attempt.prepared = null;
        if (!attempt.authorization.authorize(authenticatedAt, SystemClock.elapsedRealtime())) throw error(Code.AUTH_REQUIRED);
    }
    private void guardOwner(Attempt attempt) {
        ensureOpen();
        if (attempt == null || attempt.owner != this || ACTIVE.get() != attempt) throw error(Code.STALE_ATTEMPT);
        if (attempt.authorization.isCancelled()) throw error(Code.CANCELLED);
    }
    private void guard(Attempt attempt, boolean prepared) {
        guardOwner(attempt);
        requireUnlocked();
        if (!attempt.authorization.permits(SystemClock.elapsedRealtime())) throw error(Code.AUTH_REQUIRED);
        if (prepared && attempt.prepared == null) throw error(Code.NOT_QUALIFIED);
    }
    private void cancelPrepared(Attempt attempt) {
        if (attempt != null && attempt.owner == this) attempt.prepared = null;
    }
    private void ensureOpen() {
        if (closed.get()) throw error(Code.CLOSED);
        // This identity increment qualifies the connected API37 Googlebook.
        // KeyInfo's unlocked-device policy inspector was only added in 36.1;
        // older APK-compatible devices must not create a key we cannot inspect.
        if (Build.VERSION.SDK_INT < 37) throw error(Code.UNSUPPORTED_PLATFORM);
    }
    private void worker() {
        ensureOpen();
        if (Looper.myLooper() == Looper.getMainLooper()) throw error(Code.MAIN_THREAD);
    }
    private void requireUnlocked() {
        KeyguardManager keyguard = app.getSystemService(KeyguardManager.class);
        UserManager users = app.getSystemService(UserManager.class);
        if (keyguard == null || !keyguard.isDeviceSecure()) throw error(Code.DEVICE_LOCK_REQUIRED);
        if (users == null || !users.isUserUnlocked() || keyguard.isDeviceLocked()) throw error(Code.DEVICE_LOCKED);
    }
    private KeyStore keyStore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }
    private PrivateKey privateKey(KeyStore store, String alias) throws Exception {
        if (!store.containsAlias(alias)) throw error(Code.KEY_MISSING);
        java.security.Key entry = store.getKey(alias, null);
        if (!(entry instanceof PrivateKey)) throw error(Code.KEY_MISSING);
        PrivateKey key = (PrivateKey) entry;
        if (!"RSA".equals(key.getAlgorithm())) throw error(Code.KEY_POLICY);
        return key;
    }
    private static KeyInfo keyInfo(PrivateKey key) throws Exception {
        if (Build.VERSION.SDK_INT < 37) throw error(Code.UNSUPPORTED_PLATFORM);
        KeyInfo info = KeyFactory.getInstance("RSA", "AndroidKeyStore").getKeySpec(key, KeyInfo.class);
        if (info.getKeySize() != 2048 || info.getPurposes() != KeyProperties.PURPOSE_SIGN
                || !info.isUserAuthenticationRequired() || !info.isUnlockedDeviceRequired()
                || info.getUserAuthenticationValidityDurationSeconds() != 30
                || info.getUserAuthenticationType() != AUTH_TYPES
                || !same(info.getSignaturePaddings(), KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                || !same(info.getDigests(), KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                || !same(info.getEncryptionPaddings(), KeyProperties.ENCRYPTION_PADDING_NONE)) throw error(Code.KEY_POLICY);
        return info;
    }
    private static boolean same(String[] actual, String... expected) {
        return new HashSet<>(Arrays.asList(actual)).equals(new HashSet<>(Arrays.asList(expected)));
    }
    private static String level(KeyInfo info) {
        switch (info.getSecurityLevel()) {
            case KeyProperties.SECURITY_LEVEL_SOFTWARE: return "software";
            case KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT: return "trusted-environment";
            case KeyProperties.SECURITY_LEVEL_STRONGBOX: return "strongbox";
            case KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE: return "unknown-secure";
            default: return "unknown";
        }
    }
    private void preCertificateControl(Attempt attempt, PrivateKey key, java.security.PublicKey publicKey) throws Exception {
        FailureStage stage = FailureStage.GUARD;
        try {
            guard(attempt, false);
            byte[] challenge = new byte[32];
            RANDOM.nextBytes(challenge);
            stage = FailureStage.PRECERTIFICATE_SIGN_INIT;
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            stage = FailureStage.PRECERTIFICATE_SIGN;
            signer.update(challenge);
            byte[] signature = signer.sign();
            stage = FailureStage.PRECERTIFICATE_VERIFY;
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(challenge);
            if (!verifier.verify(signature)) throw error(Code.KEY_POLICY);
            stage = FailureStage.GUARD;
            guard(attempt, false);
        } catch (Exception failure) { throw failure(failure, stage); }
    }
    private Qualification signQualification(Attempt attempt, PrivateKey key, KeyInfo info, X509Certificate cert) throws Exception {
        FailureStage stage = FailureStage.CHALLENGE_GUARD;
        try {
            stage = FailureStage.CHALLENGE_GUARD;
            guard(attempt, false);
            // Only test non-exportability: an unexpected encoding is wiped, never saved or returned.
            stage = FailureStage.PRIVATE_ENCODING;
            byte[] encoding = key.getEncoded();
            if (encoding != null) { Arrays.fill(encoding, (byte) 0); throw error(Code.KEY_POLICY); }
            byte[] challenge = new byte[32];
            RANDOM.nextBytes(challenge);
            stage = FailureStage.CHALLENGE_SIGN_INIT;
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            stage = FailureStage.CHALLENGE_SIGN;
            signer.update(challenge);
            byte[] signature = signer.sign();
            stage = FailureStage.CHALLENGE_VERIFY;
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(cert.getPublicKey());
            verifier.update(challenge);
            if (!verifier.verify(signature)) throw error(Code.KEY_POLICY);
            stage = FailureStage.CHALLENGE_GUARD;
            guard(attempt, false);
            stage = FailureStage.QUALIFICATION_DETAILS;
            return new Qualification(info, cert, signature.length);
        } catch (Exception failure) { throw failure(failure, stage); }
    }
    private X509Certificate certificate(KeyStore store, JSONObject record) throws Exception {
        String value = record.getString("certificate");
        if (value.length() > MAX_CERTIFICATE_BYTES * 2 || !value.matches("[A-Za-z0-9+/]+={0,2}")) throw error(Code.INVALID_RECORD);
        X509Certificate cert = parseCertificate(Base64.decode(value, Base64.NO_WRAP));
        validateCertificate(store, record.getString("alias"), cert);
        return cert;
    }
    private static X509Certificate parseCertificate(byte[] raw) throws Exception {
        if (raw.length == 0 || raw.length > MAX_CERTIFICATE_BYTES) throw error(Code.CERTIFICATE_INVALID);
        ByteArrayInputStream input = new ByteArrayInputStream(raw);
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        if (input.available() != 0 || !Arrays.equals(raw, cert.getEncoded())) throw error(Code.CERTIFICATE_INVALID);
        return cert;
    }
    private static void validateCertificate(KeyStore store, String alias, X509Certificate cert) throws Exception {
        cert.checkValidity();
        if (!"1.2.840.113549.1.1.11".equals(cert.getSigAlgOID())
                || !cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())
                || !MessageDigest.isEqual(cert.getPublicKey().getEncoded(), store.getCertificate(alias).getPublicKey().getEncoded())) {
            throw error(Code.CERTIFICATE_INVALID);
        }
        cert.verify(cert.getPublicKey());
    }
    private boolean ownedAlias(String alias) {
        if (!alias.startsWith(prefix)) return false;
        String suffix = alias.substring(prefix.length());
        try { return UUID.fromString(suffix).toString().equals(suffix); }
        catch (IllegalArgumentException failure) { return false; }
    }
    private boolean hasOwnedAlias(KeyStore store) throws Exception {
        Enumeration<String> aliases = store.aliases();
        while (aliases.hasMoreElements()) if (ownedAlias(aliases.nextElement())) return true;
        return false;
    }
    private AtomicFile recordFile() throws IOException {
        File directory = new File(app.getNoBackupFilesDir(), "stream-identity-v1");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("identity-directory");
        return new AtomicFile(new File(directory, "identity.json"));
    }
    private JSONObject requiredRecord() throws Exception {
        JSONObject value = readRecord();
        if (value == null) throw error(Code.IDENTITY_ABSENT);
        return value;
    }
    private JSONObject readRecord() throws Exception {
        AtomicFile file = recordFile();
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) {
            if (new File(file.getBaseFile() + ".new").exists()) throw error(Code.INVALID_RECORD);
            return null;
        }
        byte[] raw;
        try (FileInputStream input = file.openRead()) { raw = input.readNBytes(MAX_RECORD_BYTES + 1); }
        if (raw.length == 0 || raw.length > MAX_RECORD_BYTES) throw error(Code.INVALID_RECORD);
        JSONObject value = new JSONObject(new String(raw, StandardCharsets.UTF_8));
        if (!(value.get("schemaVersion") instanceof Integer) || value.getInt("schemaVersion") != 1
                || !(value.get("state") instanceof String) || !(value.get("alias") instanceof String)
                || !ownedAlias(value.getString("alias"))) throw error(Code.INVALID_RECORD);
        String state = value.getString("state");
        Set<String> expected = new HashSet<>(Arrays.asList("schemaVersion", "state", "alias"));
        if (state.equals("ready")) {
            expected.add("certificate");
            if (!(value.get("certificate") instanceof String)) throw error(Code.INVALID_RECORD);
        } else if (!state.equals("pending")) throw error(Code.INVALID_RECORD);
        Set<String> actual = new HashSet<>();
        value.keys().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw error(Code.INVALID_RECORD);
        return value;
    }
    private void writeRecord(JSONObject value) throws Exception {
        byte[] raw = (value.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_RECORD_BYTES) throw error(Code.INVALID_RECORD);
        AtomicFile file = recordFile();
        FileOutputStream output = null;
        boolean finished = false;
        try {
            output = file.startWrite();
            output.write(raw);
            file.finishWrite(output);
            finished = true;
            byte[] actual;
            try (FileInputStream input = file.openRead()) { actual = input.readNBytes(MAX_RECORD_BYTES + 1); }
            if (!Arrays.equals(raw, actual)) throw error(Code.STORAGE);
        } catch (Exception failure) {
            if (!finished && output != null) file.failWrite(output);
            throw failure;
        }
    }
    private static IdentityException error(Code code) { return new IdentityException(code); }
    private static IdentityException failure(Exception failure, FailureStage stage) {
        if (failure instanceof IdentityException) {
            IdentityException existing = (IdentityException) failure;
            return !existing.stage.equals(FailureStage.GUARD.label) ? existing
                    : new IdentityException(existing.code, stage, existing.causeClass, existing.keyStoreErrorCode);
        }
        Code code = failure instanceof IOException ? Code.STORAGE : Code.CRYPTO;
        String causeClass = "OtherException";
        Integer numeric = null;
        Throwable cause = failure;
        // Capture only public diagnostics from a bounded chain. Never attach the
        // original Throwable, whose message/stack may contain identity material.
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (cause instanceof UserNotAuthenticatedException) code = Code.AUTH_REQUIRED;
            else if (cause instanceof KeyPermanentlyInvalidatedException && code != Code.AUTH_REQUIRED) code = Code.KEY_INVALIDATED;
            String allowed = causeClass(cause);
            if (!allowed.equals("OtherException")) causeClass = allowed;
            // Public since API33, below this APK's minimum35 and identity's37 gate.
            if (cause instanceof android.security.KeyStoreException) {
                numeric = ((android.security.KeyStoreException) cause).getNumericErrorCode();
            }
        }
        return new IdentityException(code, stage, causeClass, numeric);
    }
    private static String causeClass(Throwable cause) {
        if (cause instanceof UserNotAuthenticatedException) return "UserNotAuthenticatedException";
        if (cause instanceof KeyPermanentlyInvalidatedException) return "KeyPermanentlyInvalidatedException";
        if (cause instanceof android.security.KeyStoreException) return "android.security.KeyStoreException";
        if (cause instanceof java.security.InvalidKeyException) return "InvalidKeyException";
        if (cause instanceof java.security.SignatureException) return "SignatureException";
        if (cause instanceof java.security.ProviderException) return "ProviderException";
        if (cause instanceof java.security.NoSuchAlgorithmException) return "NoSuchAlgorithmException";
        if (cause instanceof java.security.InvalidAlgorithmParameterException) return "InvalidAlgorithmParameterException";
        if (cause instanceof java.security.UnrecoverableKeyException) return "UnrecoverableKeyException";
        if (cause instanceof java.security.KeyStoreException) return "java.security.KeyStoreException";
        if (cause instanceof java.security.cert.CertificateExpiredException) return "CertificateExpiredException";
        if (cause instanceof java.security.cert.CertificateNotYetValidException) return "CertificateNotYetValidException";
        if (cause instanceof java.security.cert.CertificateException) return "CertificateException";
        if (cause instanceof org.bouncycastle.operator.OperatorCreationException) return "OperatorCreationException";
        if (cause instanceof org.bouncycastle.operator.RuntimeOperatorException) return "RuntimeOperatorException";
        if (cause instanceof IOException) return "IOException";
        if (cause instanceof SecurityException) return "SecurityException";
        if (cause instanceof IllegalArgumentException) return "IllegalArgumentException";
        if (cause instanceof org.json.JSONException) return "JSONException";
        if (cause instanceof IllegalStateException) return "IllegalStateException";
        return "OtherException";
    }
    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) value.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return value.toString();
    }
}
