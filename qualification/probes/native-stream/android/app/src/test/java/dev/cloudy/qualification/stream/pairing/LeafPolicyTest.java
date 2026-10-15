package dev.cloudy.qualification.stream.pairing;

import static org.junit.Assert.assertThrows;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;

/** Real public test certificates, never app keys or TLS/server integration evidence. */
public final class LeafPolicyTest {
    private static X509Certificate certificate(long start, long end) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair key = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=Cloudy test fixture");
        byte[] der = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE, new Date(start),
                new Date(end), subject, key.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(key.getPrivate())).getEncoded();
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }
    private static byte[] pin(X509Certificate certificate) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
    }
    @Test public void exactPinRequiredEvenForAnotherValidCertificate() throws Exception {
        long now = System.currentTimeMillis();
        X509Certificate first = certificate(now - 60_000, now + 60_000);
        byte[] expected = pin(first);
        LeafPolicy policy = new LeafPolicy(first, expected);
        expected[0] ^= 1; // Caller mutation cannot replace the enrolled identity.
        policy.verify(first);
        X509Certificate other = certificate(now - 60_000, now + 60_000);
        assertThrows(CertificateException.class, () -> policy.verify(other));
    }
    @Test public void matchingPinNeverOverridesValidityOrMissingCertificate() throws Exception {
        long now = System.currentTimeMillis();
        X509Certificate expired = certificate(now - 120_000, now - 60_000);
        assertThrows(CertificateException.class, () -> new LeafPolicy(expired, pin(expired)));
        X509Certificate future = certificate(now + 60_000, now + 120_000);
        assertThrows(CertificateException.class, () -> new LeafPolicy(future, pin(future)));
        assertThrows(CertificateException.class, () -> new LeafPolicy(null, new byte[32]));
    }
}
