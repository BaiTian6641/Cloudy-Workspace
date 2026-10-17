package dev.cloudy.qualification.x11broker;

import android.content.Context;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;

import java.io.File;
import java.util.Properties;

import io.github.baitian6641.cloudy.ssh.SshX11ClientFactory;
import io.github.baitian6641.cloudy.ssh.SshX11AuthorityProvider;
import io.github.baitian6641.cloudy.ssh.SshX11Duplex;
import io.github.baitian6641.cloudy.ssh.SshX11Forwarding;
import io.github.baitian6641.cloudy.ssh.SshX11ServerEndpoint;

/** One explicit ordinary-SSH session; no agent, replay, saved credential or implicit reconnect. */
public final class SshX11Controller {
    private static final Object LOCK = new Object();
    private static SshX11Controller active;

    private final SSHClient client;
    private final SshX11Forwarding forwarding;
    private final Session session;
    private final Session.Command command;

    private SshX11Controller(SSHClient client, SshX11Forwarding forwarding, Session session,
                             Session.Command command) {
        this.client = client;
        this.forwarding = forwarding;
        this.session = session;
        this.command = command;
    }

    public static String start(Context context) throws Exception {
        return startInternal(context,
            "xterm -geometry 80x24+40+40 & xterm -geometry 80x24+700+40 & wait",
            "Two remote xterm windows launch admitted; waiting for X11 windows");
    }

    public static String startVsCode(Context context) throws Exception {
        return startInternal(context,
            "export CODE_USER_DATA_DIR=/tmp/cloudy-vscode-official-evidence; " +
            "export XDG_RUNTIME_DIR=/tmp/cloudy-vscode-official-runtime; " +
            "export GTK_USE_PORTAL=0; " +
            "mkdir -p $CODE_USER_DATA_DIR $XDG_RUNTIME_DIR; " +
            "/tmp/cloudy-vscode-official/code --no-sandbox --disable-gpu " +
            "--ozone-platform=x11 --disable-extensions --disable-crash-reporter " +
            "--disable-dev-shm-usage --user-data-dir $CODE_USER_DATA_DIR & " +
            "xterm -geometry 80x24+700+40 & wait",
            "Remote VS Code and xterm launch admitted; waiting for X11 windows");
    }

    private static String startInternal(Context context, String commandText, String resultText) throws Exception {
        synchronized (LOCK) {
            if (active != null) return "SSH X11 session already running";
            Properties properties = new Properties();
            try (java.io.InputStream input = context.getAssets().open("ssh-fixture/fixture.properties")) {
                properties.load(input);
            }
            String host = required(properties, "host");
            int port = Integer.parseInt(required(properties, "port"));
            String user = required(properties, "user");
            String keyText = readAsset(context, "ssh-fixture/user_ecdsa");
            String fingerprint = required(properties, "hostKeyFingerprint");
            String remoteAuthority = required(properties, "xauthorityRemote");
            SSHClient client = SshX11ClientFactory.create();
            Session session = null;
            SshX11Forwarding forwarding = null;
            try {
                client.addHostKeyVerifier(fingerprint);
                client.connect(host, port);
                client.authPublickey(user, client.loadKeys(keyText, null, null));
                byte[] cookie = X11Environment.cookieForSsh();
                SshX11ServerEndpoint endpoint = new AndroidLocalSocketEndpoint(X11Environment.socketPath());
                SshX11Duplex endpointCheck = endpoint.open();
                endpointCheck.close();
                android.util.Log.i("CloudyX11Broker", "Android UNIX X11 endpoint opened");
                SshX11AuthorityProvider authority = () -> cookie.clone();
                forwarding = SshX11Forwarding.Companion.attach(client, endpoint, authority, 16, 10_000L,
                    (code, detail) -> { android.util.Log.w("CloudyX11Broker", "X11 channel failure: " + code + " / " + detail); return null; });
                session = client.startSession();
                session.setEnvVar("XAUTHORITY", remoteAuthority);
                forwarding.enableOn(session, 0);
                // Real remote applications; the command channel remains owned until explicit stop.
                Session.Command command = session.exec(commandText);
                active = new SshX11Controller(client, forwarding, session, command);
                Thread diagnostics = new Thread(() -> reportExit(command), "cloudy-x11-ssh-exit");
                diagnostics.setDaemon(true);
                diagnostics.start();
                Thread stdout = new Thread(() -> drainStream(command.getInputStream(), "stdout"), "cloudy-x11-ssh-out");
                stdout.setDaemon(true);
                stdout.start();
                Thread stderr = new Thread(() -> drainStream(command.getErrorStream(), "stderr"), "cloudy-x11-ssh-err");
                stderr.setDaemon(true);
                stderr.start();
                return resultText;
            } catch (Exception failure) {
                if (forwarding != null) forwarding.stop();
                if (session != null) session.close();
                client.close();
                throw failure;
            }
        }
    }

    public static void stop() {
        SshX11Controller owner;
        synchronized (LOCK) {
            owner = active;
            active = null;
        }
        if (owner == null) return;
        owner.forwarding.stop();
        try { owner.command.close(); } catch (Exception ignored) { }
        try { owner.session.close(); } catch (Exception ignored) { }
        try { owner.client.close(); } catch (Exception ignored) { }
    }

    private static String readAsset(Context context, String name) throws Exception {
        try (java.io.InputStream input = context.getAssets().open(name)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void drainStream(java.io.InputStream stream, String label) {
        try {
            byte[] buffer = new byte[2048];
            for (int count = 0; count < 32; ++count) {
                int read = stream.read(buffer);
                if (read <= 0) break;
                String value = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8);
                android.util.Log.i("CloudyX11Broker", "Remote " + label + ": " + value);
            }
        } catch (Exception failure) {
            android.util.Log.i("CloudyX11Broker", "Remote " + label + " ended: " +
                failure.getClass().getSimpleName());
        }
    }

    private static void reportExit(Session.Command command) {
        try {
            command.join();
            android.util.Log.i("CloudyX11Broker", "Remote command ended exit=" + command.getExitStatus());
        } catch (Exception failure) {
            android.util.Log.i("CloudyX11Broker", "Remote command diagnostics ended: " +
                failure.getClass().getSimpleName());
        }
    }

    public static int activeChannels() {
        synchronized (LOCK) { return active == null ? 0 : active.forwarding.activeChannels(); }
    }

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("SSH_FIXTURE_MISSING_" + name);
        return value;
    }
}
