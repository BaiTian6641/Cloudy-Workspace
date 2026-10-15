package dev.cloudy.qualification.stream;

import android.content.Context;
import android.util.Base64;
import dev.cloudy.qualification.stream.pairing.PairingController;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONObject;

/** Frozen public trust/configuration only; initial cleartext route is explicitly loopback. */
final class PairingProfile {
    private PairingProfile() {}
    static PairingController.Profile read(Context context) throws Exception {
        File file=new File(context.getNoBackupFilesDir(),"pairing-config.json");
        if (!file.isFile() || file.length()>32768) throw new IllegalArgumentException();
        byte[] bytes=Files.readAllBytes(file.toPath());
        if (bytes.length>32768) throw new IllegalArgumentException();
        JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        Set<String> fields=Set.of("schema","profileId","numericAddress","httpPort","httpsPort","certificateSha256","certificateDerBase64");
        if (json.length()!=fields.size()) throw new IllegalArgumentException();
        for (Iterator<String> keys=json.keys();keys.hasNext();) if (!fields.contains(keys.next())) throw new IllegalArgumentException();
        if (integer(json,"schema")!=1) throw new IllegalArgumentException();
        String id=json.getString("profileId"),address=json.getString("numericAddress"),hex=json.getString("certificateSha256");
        int http=integer(json,"httpPort"),https=integer(json,"httpsPort");
        if (!PairingPinPolicy.endpoint(id,address,http,https) || !hex.matches("[A-Fa-f0-9]{64}")) throw new IllegalArgumentException();
        byte[] pin=new byte[32];
        for (int i=0;i<32;i++) pin[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        String encoded=json.getString("certificateDerBase64");
        if (encoded.length()>22000 || !encoded.matches("[A-Za-z0-9+/]+={0,2}")) throw new IllegalArgumentException();
        byte[] der=Base64.decode(encoded,Base64.NO_WRAP);
        if (der.length==0 || der.length>16384 || !Base64.encodeToString(der,Base64.NO_WRAP).equals(encoded)) throw new IllegalArgumentException();
        ByteArrayInputStream input=new ByteArrayInputStream(der);
        X509Certificate certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input);
        if (input.available()!=0 || !java.util.Arrays.equals(der,certificate.getEncoded())) throw new IllegalArgumentException();
        return new PairingController.Profile(id,address,http,https,certificate,pin);
    }
    private static int integer(JSONObject json,String field) throws Exception {
        Object value=json.get(field); if (!(value instanceof Integer)) throw new IllegalArgumentException(); return (Integer)value;
    }
}
