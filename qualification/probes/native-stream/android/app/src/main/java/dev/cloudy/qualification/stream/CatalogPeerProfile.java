package dev.cloudy.qualification.stream;

import android.content.Context;
import android.util.Base64;
import dev.cloudy.qualification.stream.playback.PlaybackProfile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONObject;

/** Public approved trust/profile only. Reading it does not discover or approve a LAN route. */
final class CatalogPeerProfile {
    final String id,address;
    final int httpPort,httpsPort,rtspPort;
    final X509Certificate certificate;
    private final byte[] pin;
    private CatalogPeerProfile(String id,String address,int http,int https,int rtsp,X509Certificate certificate,byte[] pin) throws Exception {
        if (!id.matches("[A-Za-z0-9_-]{1,64}") || !port(http) || !port(https) || !port(rtsp)
                || http==https || http==rtsp || https==rtsp) throw new IllegalArgumentException();
        java.net.InetAddress parsed=android.net.InetAddresses.parseNumericAddress(address);
        if (!(parsed instanceof java.net.Inet4Address) || parsed.isLoopbackAddress() || parsed.isAnyLocalAddress() || parsed.isMulticastAddress()) throw new IllegalArgumentException();
        this.id=id;this.address=parsed.getHostAddress();httpPort=http;httpsPort=https;rtspPort=rtsp;
        this.certificate=certificate;this.pin=pin.clone();verifyLeaf(certificate);
    }
    private static boolean port(int value) { return value>0 && value<=65535; }
    void verifyLeaf(X509Certificate leaf) throws java.security.cert.CertificateException {
        leaf.checkValidity();
        try { if (!java.security.MessageDigest.isEqual(pin,java.security.MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded()))) throw new java.security.cert.CertificateException("leaf-rejected"); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new java.security.cert.CertificateException("digest-unavailable"); }
    }
    PlaybackProfile select(int actualId) throws Exception {
        return new PlaybackProfile(id,address,httpPort,httpsPort,rtspPort,actualId,certificate,pin);
    }
    static CatalogPeerProfile read(Context context) throws Exception {
        File file=new File(context.getNoBackupFilesDir(),"catalog-config.json");
        if (!file.isFile() || file.length()>32768) throw new IllegalArgumentException();
        byte[] bytes=Files.readAllBytes(file.toPath());
        if (bytes.length>32768) throw new IllegalArgumentException();
        JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        Set<String> fields=Set.of("schema","profileId","numericAddress","httpPort","httpsPort","rtspPort","certificateSha256","certificateDerBase64");
        if (json.length()!=fields.size()) throw new IllegalArgumentException();
        for (Iterator<String> keys=json.keys();keys.hasNext();) if (!fields.contains(keys.next())) throw new IllegalArgumentException();
        if (integer(json,"schema")!=1) throw new IllegalArgumentException();
        String hex=json.getString("certificateSha256"),encoded=json.getString("certificateDerBase64");
        if (!hex.matches("[A-Fa-f0-9]{64}") || encoded.length()>22000 || !encoded.matches("[A-Za-z0-9+/]+={0,2}")) throw new IllegalArgumentException();
        byte[] pin=new byte[32]; for (int i=0;i<32;i++) pin[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        byte[] der=Base64.decode(encoded,Base64.NO_WRAP);
        if (der.length==0 || der.length>16384 || !Base64.encodeToString(der,Base64.NO_WRAP).equals(encoded)) throw new IllegalArgumentException();
        ByteArrayInputStream input=new ByteArrayInputStream(der);
        X509Certificate certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input);
        if (input.available()!=0 || !Arrays.equals(der,certificate.getEncoded())) throw new IllegalArgumentException();
        return new CatalogPeerProfile(json.getString("profileId"),json.getString("numericAddress"),integer(json,"httpPort"),integer(json,"httpsPort"),integer(json,"rtspPort"),certificate,pin);
    }
    private static int integer(JSONObject json,String name) throws Exception {
        Object value=json.get(name); if (!(value instanceof Integer)) throw new IllegalArgumentException(); return (Integer)value;
    }
}
