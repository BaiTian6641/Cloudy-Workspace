import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.sshd.common.Service;
import org.apache.sshd.common.ServiceFactory;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.kex.KexProposalOption;
import org.apache.sshd.common.session.ReservedSessionMessagesHandler;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.server.kex.DHGServer;
import org.apache.sshd.server.session.AbstractServerSession;
import org.apache.sshd.server.session.ServerConnectionService;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerUserAuthService;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;

/** Isolated maintained-library fixture, never a system account or production SSH server. */
public final class CloudySshPasswordFixture {
    private static final int MAX_SECONDS = 180;
    private static final int MAX_SESSIONS = 64;
    private static final int MAX_EVENTS = 512;
    private final Path directory;
    private final SshServer server = SshServer.setUpDefaultServer();
    private final Map<Session, Integer> sessions = new ConcurrentHashMap<>();
    private final Map<Session, String> modes = new ConcurrentHashMap<>();
    private final Map<Session, ScheduledFuture<?>> delayedActions = new ConcurrentHashMap<>();
    private final java.util.Set<Session> actionScheduled = ConcurrentHashMap.newKeySet();
    private final Map<Session, AtomicInteger> serverKex = new ConcurrentHashMap<>();
    private final Map<Session, AtomicInteger> clientNegotiations = new ConcurrentHashMap<>();
    private final AtomicInteger nextSession = new AtomicInteger();
    private final Map<String, Long> counts = new LinkedHashMap<>();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private int events;
    private volatile boolean overflow;
    private volatile boolean closed;
    private volatile boolean ioFailed;
    private final String password;

    private CloudySshPasswordFixture(Path destination) throws Exception {
        // Require a fresh leaf: never chmod, overwrite or read another fixture/user directory.
        directory = Files.createDirectory(destination,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        privateFile("events.jsonl");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        java.util.Arrays.fill(random, (byte) 0);
    }

    public static void main(String[] args) {
        final long began = System.nanoTime();
        CloudySshPasswordFixture fixture = null;
        int exit = 1;
        try {
            if (args.length != 1 || !(LoggerFactory.getILoggerFactory() instanceof NOPLoggerFactory)) {
                throw new IllegalArgumentException("FIXTURE_CONFIGURATION");
            }
            fixture = new CloudySshPasswordFixture(Path.of(args[0]).toAbsolutePath().normalize());
            final CloudySshPasswordFixture owned = fixture;
            Runtime.getRuntime().addShutdownHook(new Thread(owned::stop, "cloudy-password-fixture-shutdown"));
            // Absolute watchdog includes startup and cleanup. Parent must still record exit status;
            // a hard stop is cleanup uncertainty, never a successful retirement receipt.
            Thread watchdog = new Thread(() -> {
                sleepUntil(began + TimeUnit.SECONDS.toNanos(MAX_SECONDS));
                Runtime.getRuntime().halt(124);
            }, "cloudy-password-fixture-hard-deadline");
            watchdog.setDaemon(true);
            watchdog.start();
            fixture.start();
            long remaining = began + TimeUnit.SECONDS.toNanos(MAX_SECONDS - 5) - System.nanoTime();
            if (remaining > 0) fixture.stopped.await(remaining, TimeUnit.NANOSECONDS);
            fixture.stop();
            exit = fixture.closed && !fixture.ioFailed && !fixture.overflow ? 0 : 2;
        } catch (Exception exception) {
            // No paths, account/password data, server prompts or exception transcripts on stderr.
            System.err.println("FIXTURE_FAILED");
        } finally {
            if (fixture != null) fixture.stop();
        }
        System.exit(exit);
    }

    private void start() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair hostKey = generator.generateKeyPair();
        String fingerprint = KeyUtils.getFingerPrint(BuiltinDigests.sha256, hostKey.getPublic());
        if (fingerprint == null || !fingerprint.startsWith("SHA256:")) throw new IOException("FIXTURE_KEY");
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(KeyPairProvider.wrap(hostKey));
        server.setKeyExchangeFactories(List.of(DHGServer.newFactory(BuiltinDHFactories.ecdhp256)));
        server.setSignatureFactories(List.of(BuiltinSignatures.nistp256));
        server.setCipherFactories(List.of(BuiltinCiphers.aes128gcm));
        server.setMacFactories(List.of(BuiltinMacs.hmacsha256));
        server.setCompressionFactories(List.of(BuiltinCompressions.none));
        server.setShellFactory(null);
        server.setCommandFactory(null);
        server.setSubsystemFactories(Collections.emptyList());
        server.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);
        server.setGlobalRequestHandlers(Collections.emptyList());
        // SshServer.checkConfig requires a nonempty factory list. Its sole factory rejects.
        server.setChannelFactories(List.of(new ChannelFactory() {
            @Override public String getName() { return "session"; }
            @Override public Channel createChannel(Session session) throws IOException {
                event("channel_factory_rejected", session, null, null);
                throw new IOException("FIXTURE_CHANNEL_REJECTED");
            }
        }));
        server.setReservedSessionMessagesHandler(new ReservedSessionMessagesHandler() {
            @Override public IoWriteFuture sendKexInitRequest(
                    Session session, Map<KexProposalOption, String> proposal, Buffer packet) throws Exception {
                int sequence = serverKex.computeIfAbsent(session, ignored -> new AtomicInteger()).incrementAndGet();
                String prefix = sequence == 1 ? "server_initial_kexinit_" : "server_rekey_kexinit_";
                byte[] payload = packet.getCompactData();
                if (payload.length == 0 || payload[0] != SshConstants.SSH_MSG_KEXINIT) {
                    throw new IOException("FIXTURE_KEX_BUFFER");
                }
                // The hook receives the complete plaintext payload, starting with the opcode.
                // AbstractSession.sendKexInit computes its exchange-hash data AFTER this hook.
                // writePacket encodes/mutates its buffer: writing the original here would corrupt
                // that hash input. Preserve it and let maintained createBuffer reserve framing,
                // insert the opcode once, and copy only the already-encoded remaining payload.
                Buffer outgoing = session.createBuffer(SshConstants.SSH_MSG_KEXINIT, payload.length);
                outgoing.putRawBytes(payload, 1, payload.length - 1);
                IoWriteFuture written = session.writePacket(outgoing);
                written.addListener(completed -> event(prefix + (completed.isWritten() ? "written" : "write_failed"),
                        session, null, null));
                // A nonnull result transfers delivery responsibility; returning this exact future
                // prevents the upstream fallback from writing the original a second time.
                return written;
            }
        });
        server.setUserAuthFactories(List.of(new UserAuthPasswordFactory()));
        server.setPublickeyAuthenticator(null);
        server.setKeyboardInteractiveAuthenticator(null);
        server.setPasswordAuthenticator(new PasswordAuthenticator() {
            @Override public boolean authenticate(String username, String supplied, ServerSession session) {
                String mode = mode(username);
                if (!password.equals(supplied)) { event("auth_denied", session, null, null); return false; }
                switch (mode) {
                    case "accept": event("auth_accepted", session, null, null); return true;
                    case "change":
                        event("change_required", session, null, null);
                        // Upstream UserAuthPassword emits the genuine encrypted USERAUTH_60.
                        throw new PasswordChangeRequiredException("FIXTURE_CHANGE", "Fixture password change required", "en");
                    case "close":
                    case "rekey":
                        scheduleAction(session, mode);
                        // Neither lane returns an auth success/failure response. Upstream resumes
                        // NIO processing while the server-owned scheduler acts outside auth locks.
                        throw new AsyncAuthException();
                    case "wait":
                        event("auth_wait", session, null, null);
                        // Keep NIO processing free to observe actual client disconnect. No sleeping
                        // auth callback or synthetic cancellation response; never resolve this token.
                        throw new AsyncAuthException();
                    default: event("auth_denied", session, null, null); return false;
                }
            }
            @Override public boolean handleClientPasswordChangeRequest(
                    ServerSession session, String username, String oldPassword, String newPassword) {
                event("change_handler", session, null, null);
                return false;
            }
        });
        server.setServiceFactories(List.of(new ServiceFactory() {
            @Override public String getName() { return "ssh-userauth"; }
            @Override public Service create(Session session) throws IOException {
                return new ServerUserAuthService(session) {
                    @Override public synchronized void process(int cmd, Buffer buffer) throws Exception {
                        if (cmd == SshConstants.SSH_MSG_USERAUTH_REQUEST) {
                            int position = buffer.rpos();
                            try {
                                String user = buffer.getString();
                                buffer.getString(); // Service name stays private and is not recorded.
                                String method = method(buffer.getString());
                                modes.put(session, mode(user));
                                Boolean change = "password".equals(method) ? buffer.getBoolean() : null;
                                event("auth_request", session, method, change);
                            } finally { buffer.rpos(position); }
                        }
                        super.process(cmd, buffer);
                    }
                };
            }
        }, new ServiceFactory() {
            @Override public String getName() { return "ssh-connection"; }
            @Override public Service create(Session session) throws IOException {
                return new ServerConnectionService((AbstractServerSession) session) {
                    @Override public void process(int cmd, Buffer buffer) throws Exception {
                        if (cmd == SshConstants.SSH_MSG_CHANNEL_OPEN) event("channel_open", session, null, null);
                        super.process(cmd, buffer);
                    }
                };
            }
        }));
        server.addSessionListener(new SessionListener() {
            @Override public void sessionCreated(Session session) {
                int id = sessionId(session);
                if (id == 0) { session.close(true); return; }
                modes.putIfAbsent(session, "unknown");
                event("session_created", session, null, null);
            }
            @Override public void sessionNegotiationStart(Session session,
                    Map<KexProposalOption, String> clientProposal, Map<KexProposalOption, String> serverProposal) {
                int sequence = clientNegotiations.computeIfAbsent(session, ignored -> new AtomicInteger()).incrementAndGet();
                // Public negotiation callback proves a parsed peer proposal reached negotiation;
                // it is not a raw packet-ingress counter and observes no proposal contents.
                event(sequence == 1 ? "client_initial_kexinit_negotiation_start" : "client_rekey_kexinit_negotiation_start",
                        session, null, null);
            }
            @Override public void sessionEvent(Session session, Event event) {
                if (event == Event.Authenticated) event("session_authenticated", session, null, null);
                if (event == Event.KeyEstablished) event("key_established", session, null, null);
            }
            @Override public void sessionClosed(Session session) {
                event("session_closed", session, null, null);
                ScheduledFuture<?> delayed = delayedActions.remove(session);
                if (delayed != null) delayed.cancel(false);
                // Retain at most MAX_SESSIONS non-secret correlation records until process exit:
                // an already running scheduled action may report after sessionClosed fires.
            }
        });
        server.start();
        if (server.getBoundAddresses().size() != 1 || server.getBoundAddresses().stream().anyMatch(address ->
                !(address instanceof InetSocketAddress) ||
                !((InetSocketAddress) address).getAddress().isLoopbackAddress())) throw new IOException("FIXTURE_BIND");
        Properties config = new Properties();
        config.setProperty("port", Integer.toString(server.getPort()));
        config.setProperty("fingerprint", fingerprint);
        config.setProperty("password", password);
        for (String mode : List.of("accept", "deny", "change", "wait", "close", "rekey")) config.setProperty("user." + mode, "fixture-" + mode);
        Path configPath = privateFile("fixture.properties");
        try (OutputStream output = Files.newOutputStream(configPath, StandardOpenOption.WRITE)) {
            config.store(output, "Private generated fixture credentials; never publish");
        }
        summary();
        System.out.println("READY port=" + server.getPort() + " fingerprint=" + fingerprint);
    }

    private void scheduleAction(ServerSession session, String mode) {
        if (!actionScheduled.add(session)) {
            event("duplicate_action_request", session, null, null);
            return;
        }
        event("action_scheduled", session, null, null);
        ScheduledFuture<?> delayed = session.getFactoryManager().getScheduledExecutorService().schedule(() -> {
            try {
                if (stopping.get() || session.isClosing() || session.isClosed()) {
                    event("action_cancelled", session, null, null);
                    return;
                }
                if ("close".equals(mode)) {
                    event("close_requested", session, null, null);
                    session.close(true);
                } else {
                    // Public reExchangeKeys only initiates KEX and returns a future. Never await
                    // it on the scheduler/auth/I/O lane: the actual peer must process the exchange.
                    session.reExchangeKeys();
                    event("rekey_started", session, null, null);
                }
            } catch (Exception exception) {
                event("action_failed", session, null, null);
                session.close(true);
            } finally { delayedActions.remove(session); }
        }, 100, TimeUnit.MILLISECONDS);
        delayedActions.put(session, delayed);
        // Handle close/task completion racing registration without retaining a stale future.
        if (stopping.get() || session.isClosing() || session.isClosed() || delayed.isDone()) {
            delayed.cancel(false);
            delayedActions.remove(session, delayed);
        }
    }

    private synchronized int sessionId(Session session) {
        Integer existing = sessions.get(session);
        if (existing != null) return existing;
        int next = nextSession.incrementAndGet();
        if (next > MAX_SESSIONS) { overflow = true; return 0; }
        sessions.put(session, next);
        modes.putIfAbsent(session, "unknown");
        return next;
    }

    private synchronized void event(String type, Session session, String method, Boolean change) {
        if (events >= MAX_EVENTS) { overflow = true; return; }
        int id = sessionId(session);
        String mode = modes.getOrDefault(session, "unknown");
        counts.merge(type, 1L, Long::sum);
        if ("auth_request".equals(type)) {
            counts.merge("request." + mode + "." + method, 1L, Long::sum);
            if (change != null) counts.merge("passwordChange." + change, 1L, Long::sum);
        }
        String line = "{\"event\":\"" + type + "\",\"session\":" + id + ",\"mode\":\"" + mode + "\""
                + (method == null ? "" : ",\"method\":\"" + method + "\"")
                + (change == null ? "" : ",\"passwordChange\":" + change) + "}\n";
        try (FileChannel output = FileChannel.open(directory.resolve("events.jsonl"), StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer data = StandardCharsets.UTF_8.encode(line);
            while (data.hasRemaining()) output.write(data);
            output.force(true);
            events++;
            summary();
        } catch (IOException exception) { ioFailed = true; }
    }

    private synchronized void summary() throws IOException {
        StringBuilder text = new StringBuilder("{\"schema\":1,\"events\":").append(events)
                .append(",\"overflow\":").append(overflow).append(",\"ioFailed\":").append(ioFailed)
                .append(",\"serverClosed\":").append(closed).append(",\"counts\":{");
        boolean first = true;
        for (Map.Entry<String, Long> count : counts.entrySet()) {
            if (!first) text.append(',');
            first = false;
            text.append('"').append(count.getKey()).append("\":").append(count.getValue());
        }
        text.append("}}\n");
        Path temporary = directory.resolve("summary.tmp");
        Files.deleteIfExists(temporary);
        Files.createFile(temporary, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
        Files.move(temporary, directory.resolve("summary.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path privateFile(String name) throws IOException {
        return Files.createFile(directory.resolve(name),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }

    private void stop() {
        if (!stopping.compareAndSet(false, true)) return;
        delayedActions.values().forEach(delayed -> delayed.cancel(false));
        delayedActions.clear();
        try { closed = server.close(true).await(4_000); }
        catch (Exception exception) { ioFailed = true; }
        try { summary(); } catch (IOException exception) { ioFailed = true; }
        stopped.countDown();
    }

    private static String mode(String name) {
        for (String mode : List.of("accept", "deny", "change", "wait", "close", "rekey")) if (("fixture-" + mode).equals(name)) return mode;
        return "unknown";
    }
    private static String method(String name) {
        return List.of("none", "password", "publickey", "keyboard-interactive").contains(name) ? name : "other";
    }
    private static void sleepUntil(long deadline) {
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return;
            try { TimeUnit.NANOSECONDS.sleep(remaining); }
            catch (InterruptedException exception) { /* Absolute watchdog cannot be disabled. */ }
        }
    }
}
