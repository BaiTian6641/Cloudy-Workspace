package dev.cloudy.qualification.stream.playback;

import android.net.InetAddresses;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.jni.MoonBridge;
import java.io.ByteArrayInputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/** One explicitly approved numeric IPv4 peer; choosing/proving its LAN route is parent-owned. */
public final class PlaybackProfile {
    final String id,address;
    final int httpPort,httpsPort,rtspPort,appId;
    final X509Certificate certificate;
    private final byte[] pin;
    public PlaybackProfile(String id,String numericIpv4,int httpPort,int httpsPort,int rtspPort,
            int appId,X509Certificate certificate,byte[] sha256) throws CertificateException {
        if (id==null || !id.matches("[A-Za-z0-9_-]{1,64}") || !PlaybackPolicy.port(httpPort)
                || !PlaybackPolicy.port(httpsPort) || !PlaybackPolicy.port(rtspPort)
                || httpPort==httpsPort || httpPort==rtspPort || httpsPort==rtspPort || appId<=0
                || certificate==null || sha256==null || sha256.length!=32) throw new IllegalArgumentException("profile-bounds");
        InetAddress parsed=InetAddresses.parseNumericAddress(numericIpv4);
        if (!(parsed instanceof Inet4Address) || parsed.isLoopbackAddress() || parsed.isAnyLocalAddress() || parsed.isMulticastAddress()) throw new IllegalArgumentException("numeric-ipv4-unicast-required");
        this.id=id; address=parsed.getHostAddress(); this.httpPort=httpPort; this.httpsPort=httpsPort;
        this.rtspPort=rtspPort; this.appId=appId; pin=sha256.clone();
        byte[] encoded=certificate.getEncoded();
        if (encoded.length>16384) throw new CertificateException("certificate-limit");
        this.certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));
        verifyLeaf(this.certificate);
    }
    void verifyLeaf(X509Certificate leaf) throws CertificateException {
        if (leaf==null) throw new CertificateException("leaf-required");
        leaf.checkValidity();
        try { if (!MessageDigest.isEqual(pin,MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded()))) throw new CertificateException("leaf-mismatch"); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new CertificateException("digest-unavailable"); }
    }
    void verifySessionUrl(String value) throws java.io.IOException {
        if (!PlaybackPolicy.sessionUrl(address,rtspPort,value)) throw new java.io.IOException("session-endpoint-rejected");
    }
    StreamConfiguration configuration() {
        return new StreamConfiguration.Builder().setApp(new NvApp("Cloudy qualification",appId,false))
                .setResolution(1280,720).setRefreshRate(60).setLaunchRefreshRate(60).setClientRefreshRateX100(6000)
                .setBitrate(10000).setSupportedVideoFormats(MoonBridge.VIDEO_FORMAT_H264)
                .setAudioConfiguration(MoonBridge.AUDIO_CONFIGURATION_STEREO).setMaxPacketSize(1392)
                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_LOCAL).setEnableSops(false)
                .enableAdaptiveResolution(false).enableLocalAudioPlayback(false).setAttachedGamepadMask(0)
                .setPersistGamepadsAfterDisconnect(false).build();
    }
}
