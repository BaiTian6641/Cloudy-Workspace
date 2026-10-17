package dev.cloudy.qualification.x11broker;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import io.github.baitian6641.cloudy.ssh.SshX11Duplex;
import io.github.baitian6641.cloudy.ssh.SshX11ServerEndpoint;

/** Android filesystem-socket endpoint; no unsupported java.nio UNIX-channel dependency. */
public final class AndroidLocalSocketEndpoint implements SshX11ServerEndpoint {
    private final String path;
    public AndroidLocalSocketEndpoint(String path) { this.path = path; }

    @Override public SshX11Duplex open() {
        LocalSocket socket = new LocalSocket();
        boolean connected = false;
        try {
            socket.connect(new LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM));
            connected = true;
            LocalSocket owned = socket;
            return new SshX11Duplex(socket.getInputStream(), socket.getOutputStream(), () -> {
                try { owned.close(); } catch (java.io.IOException ignored) { }
                return null;
            });
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("X11_LOCAL_CONNECT_FAILED", failure);
        } finally {
            if (!connected) {
                try { socket.close(); } catch (java.io.IOException ignored) { }
            }
        }
    }
}
