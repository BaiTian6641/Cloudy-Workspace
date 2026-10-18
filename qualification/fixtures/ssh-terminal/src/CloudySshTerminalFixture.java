import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.keyboard.InteractiveChallenge;
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.forward.ForwardingFilter;
import org.apache.sshd.server.forward.TcpForwardingFilter;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.InteractiveProcessShellFactory;

/**
 * Bounded loopback SSH terminal fixture: real maintained password authentication and a real local
 * {@code /bin/sh -i} child under util-linux {@code script} through Apache MINA SSHD. This is test
 * infrastructure, not a production server, privilege boundary, OpenSSH interoperability claim
 * or product acceptance result. The child has a real kernel PTY for line discipline; SSH
 * window-change requests are not propagated to that nested PTY.
 *
 * <p>One fresh random password and one fresh in-memory EC P-256 host key are generated per run.
 * The output directory must already exist as an empty real 0700 directory. The server binds
 * loopback port 0, serves one password account, one accepted client key, one keyboard-interactive
 * prompt and one loopback direct-tcpip jump channel, and closes the real server after at most
 * {@value #MAX_SECONDS} seconds, when the owned {@code stop} file appears, on normal failure, or
 * from a JVM shutdown hook. Credentials are never printed.</p>
 */
public final class CloudySshTerminalFixture {
    private static final int MAX_SECONDS = 600;
    private static final long POLL_MILLIS = 200L;
    private static final String PRIVATE_DIRECTORY = "rwx------";
    private static final String PRIVATE_FILE = "rw-------";
    // One server per JVM. Hold the monitor through close so a second caller cannot claim success early.
    private static boolean stopAttempted;
    private static boolean stopSucceeded;

    private CloudySshTerminalFixture() {
    }

    public static void main(String[] args) {
        int status = 1;
        SshServer server = null;
        Thread shutdownHook = null;
        try {
            if (args.length != 1) {
                throw new IllegalArgumentException("FIXTURE_USAGE");
            }
            Path output = Path.of(args[0]).toAbsolutePath().normalize();
            requirePrivateEmptyDirectory(output);
            Path stopFile = output.resolve("stop");

            String user = System.getProperty("user.name");
            if (user == null || user.isBlank()) {
                throw new IllegalStateException("FIXTURE_USER");
            }
            byte[] secret = new byte[24];
            new SecureRandom().nextBytes(secret);
            String password = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            Arrays.fill(secret, (byte) 0);
            byte[] expectedPassword = password.getBytes(StandardCharsets.UTF_8);

            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair hostKey = generator.generateKeyPair();
            String fingerprint = KeyUtils.getFingerPrint(BuiltinDigests.sha256, hostKey.getPublic());
            if (fingerprint == null || !fingerprint.startsWith("SHA256:")) {
                throw new IllegalStateException("FIXTURE_KEY");
            }
            generateClientKey(output.resolve("client_key"));
            // A second, deliberately unregistered key lets a consumer prove explicit refusal.
            generateClientKey(output.resolve("wrong_key"));
            List<AuthorizedKeyEntry> entries =
                AuthorizedKeyEntry.readAuthorizedKeys(output.resolve("client_key.pub"));
            if (entries.size() != 1) {
                throw new IllegalStateException("FIXTURE_KEY");
            }
            PublicKey acceptedClientKey = entries.get(0).resolvePublicKey(null, PublicKeyEntryResolver.FAILING);

            server = SshServer.setUpDefaultServer();
            server.setHost("127.0.0.1");
            server.setPort(0);
            server.setKeyPairProvider(KeyPairProvider.wrap(hostKey));
            server.setPasswordAuthenticator((username, presented, session) ->
                user.equals(username) && constantTimeEquals(expectedPassword, presented));
            server.setPublickeyAuthenticator((username, key, session) ->
                user.equals(username) && key.equals(acceptedClientKey));
            server.setKeyboardInteractiveAuthenticator(new KeyboardInteractiveAuthenticator() {
                @Override
                public InteractiveChallenge generateChallenge(ServerSession session, String language,
                                                              String subMethods, String username) {
                    InteractiveChallenge challenge = new InteractiveChallenge();
                    challenge.setInteractionName("Cloudy keyboard-interactive fixture");
                    challenge.setInteractionInstruction("Answer the single fixture prompt");
                    challenge.addPrompt("Response: ", false);
                    return challenge;
                }

                @Override
                public boolean authenticate(ServerSession session, String username,
                                            List<String> responses) {
                    return user.equals(username) && responses != null && responses.size() == 1 &&
                        constantTimeEquals(expectedPassword, responses.get(0));
                }
            });
            // script owns a real kernel PTY. MINA's process pipes alone have no CR/Enter line discipline.
            InteractiveProcessShellFactory shell = new InteractiveProcessShellFactory();
            shell.setCommand("script --return --quiet --flush --command '/bin/sh -i' /dev/null",
                "/usr/bin/script", "--return", "--quiet", "--flush", "--command", "/bin/sh -i", "/dev/null");
            server.setShellFactory(shell);
            server.setCommandFactory(null);
            // The two-hop qualification needs one direct-tcpip channel back to loopback. Agent,
            // X11, remote-listen and non-loopback targets remain refused; the listener is loopback.
            server.setForwardingFilter(new ForwardingFilter() {
                @Override public boolean canForwardAgent(Session session, String requestType) { return false; }
                @Override public boolean canForwardX11(Session session, String requestType) { return false; }
                @Override public boolean canListen(SshdSocketAddress address, Session session) { return false; }
                @Override public boolean canConnect(TcpForwardingFilter.Type type, SshdSocketAddress address,
                                                    Session session) {
                    return type == TcpForwardingFilter.Type.Direct && "127.0.0.1".equals(address.getHostName());
                }
            });
            server.setSubsystemFactories(Collections.emptyList());
            server.start();
            requireLoopbackBinding(server);

            SshServer owned = server;
            shutdownHook = new Thread(() -> stop(owned, "shutdown"),
                "cloudy-terminal-fixture-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            Path properties = output.resolve("fixture.properties");
            writeProperties(properties, server.getPort(), user, password, fingerprint);
            System.out.println("fixture.properties=" + properties);
            System.out.println("READY port=" + server.getPort() + " fingerprint=" + fingerprint);
            System.out.flush();

            String reason = awaitStop(stopFile, System.nanoTime() + TimeUnit.SECONDS.toNanos(MAX_SECONDS));
            status = stop(server, reason) ? 0 : 2;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (server != null) {
                stop(server, "interrupted");
            }
            status = 130;
        } catch (Exception failure) {
            // Fixed failure text only: no paths, credentials, key material or exception transcript.
            System.err.println("FIXTURE_FAILED");
            if (server != null) {
                stop(server, "failed");
            }
        } finally {
            if (server != null) {
                stop(server, "finally");
            }
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException | SecurityException ignored) {
                    // JVM shutdown is already in progress; the hook or this close owns retirement.
                }
            }
        }
        System.exit(status);
    }

    private static void requireLoopbackBinding(SshServer server) throws IOException {
        var boundAddresses = server.getBoundAddresses();
        if (boundAddresses.isEmpty()) {
            throw new IOException("FIXTURE_BIND");
        }
        for (SocketAddress bound : boundAddresses) {
            if (!(bound instanceof InetSocketAddress address)
                    || address.getAddress() == null
                    || !address.getAddress().isLoopbackAddress()) {
                throw new IOException("FIXTURE_BIND");
            }
        }
        if (server.getPort() <= 0) {
            throw new IOException("FIXTURE_PORT");
        }
    }

    private static void requirePrivateEmptyDirectory(Path output) throws IOException {
        if (!Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(output)
                || !output.equals(output.toRealPath())
                || !Files.getPosixFilePermissions(output, LinkOption.NOFOLLOW_LINKS)
                    .equals(PosixFilePermissions.fromString(PRIVATE_DIRECTORY))) {
            throw new IOException("output directory must be a real 0700 directory");
        }
        try (var entries = Files.list(output)) {
            if (entries.findAny().isPresent()) {
                throw new IOException("output directory must be empty");
            }
        }
    }

    /** One fresh OpenSSH EC key in the already-validated private run directory. */
    private static void generateClientKey(Path privateKey) throws IOException, InterruptedException {
        Process keygen = new ProcessBuilder("ssh-keygen", "-q", "-t", "ecdsa", "-b", "256",
            "-N", "", "-f", privateKey.toString(), "-C", "cloudy-key-fixture")
            .redirectErrorStream(true).start();
        boolean generated;
        try {
            generated = keygen.waitFor(10, TimeUnit.SECONDS) && keygen.exitValue() == 0;
        } finally {
            keygen.destroyForcibly();
        }
        if (!generated) {
            throw new IllegalStateException("FIXTURE_KEY");
        }
        Files.setPosixFilePermissions(privateKey, PosixFilePermissions.fromString(PRIVATE_FILE));
        Files.setPosixFilePermissions(privateKey.resolveSibling(privateKey.getFileName() + ".pub"),
            PosixFilePermissions.fromString(PRIVATE_FILE));
    }

    private static void writeProperties(Path path, int port, String user, String password, String fingerprint)
            throws IOException {
        Properties values = new Properties();
        values.setProperty("schema", "1");
        values.setProperty("host", "127.0.0.1");
        values.setProperty("port", Integer.toString(port));
        values.setProperty("user", user);
        values.setProperty("password", password);
        values.setProperty("fingerprint", fingerprint);
        // Create with 0600 before the secret is written; the directory is already private and empty.
        Files.createFile(path, PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString(PRIVATE_FILE)));
        try (OutputStream stream = Files.newOutputStream(path, StandardOpenOption.WRITE)) {
            values.store(stream, "Cloudy local SSH terminal fixture; private evidence");
        }
    }

    private static String awaitStop(Path stopFile, long deadline) throws InterruptedException {
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return "deadline";
            }
            if (Files.exists(stopFile, LinkOption.NOFOLLOW_LINKS)) {
                return "stop-file";
            }
            long pause = Math.min(POLL_MILLIS,
                Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)));
            Thread.sleep(pause);
        }
    }

    private static synchronized boolean stop(SshServer server, String reason) {
        if (stopAttempted) {
            return stopSucceeded;
        }
        stopAttempted = true;
        String close;
        boolean clean = false;
        try {
            if (server.isStarted()) {
                server.stop(true);
                close = "closed";
                clean = true;
            } else {
                close = "not-started";
            }
        } catch (IOException failure) {
            close = "close-failed";
        }
        System.out.println("STOP reason=" + reason + " server=" + close);
        System.out.flush();
        stopSucceeded = clean;
        return clean;
    }

    private static boolean constantTimeEquals(byte[] expected, String presented) {
        if (presented == null) {
            return false;
        }
        byte[] actual = presented.getBytes(StandardCharsets.UTF_8);
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }
}
