package dev.cloudy.qualification.stream.pairing;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/** Explicit endpoint-bound leaf identity, including CA-valid presented chains. */
final class LeafPolicy {
    private final byte[] expected;
    LeafPolicy(X509Certificate certificate, byte[] pin) throws CertificateException {
        if (certificate == null || pin == null || pin.length != 32) throw new CertificateException("pin-required");
        expected = pin.clone();
        verify(certificate);
    }
    void verify(X509Certificate certificate) throws CertificateException {
        if (certificate == null) throw new CertificateException("leaf-required");
        certificate.checkValidity();
        try {
            if (!MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()))) {
                throw new CertificateException("leaf-mismatch");
            }
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new CertificateException("digest-unavailable");
        }
    }
}
