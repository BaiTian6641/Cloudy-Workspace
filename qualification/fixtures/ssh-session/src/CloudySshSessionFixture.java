import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.io.ByteArrayOutputStream;
import org.apache.sshd.common.util.threads.SshThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.channel.ChannelSessionFactory;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.SftpModuleProperties;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.sshd.common.Service;
import org.apache.sshd.common.ServiceFactory;
import org.apache.sshd.common.SshConstants;
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
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.server.kex.DHGServer;
import org.apache.sshd.server.session.AbstractServerSession;
import org.apache.sshd.server.session.ServerConnectionService;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerUserAuthService;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;

/** Real maintained SSH/SFTP fixture; generated credentials and synthetic filesystem only. */
public final class CloudySshSessionFixture {
    private static final int MAX_SECONDS = 180;
    private static final int MAX_SESSIONS = 16;
    private static final int MAX_EVENTS = 2048;
    private final Path directory;
    private final SshServer server = SshServer.setUpDefaultServer();
    private final Map<Session, Integer> sessions = new ConcurrentHashMap<>();
    private final Map<Session, String> modes = new ConcurrentHashMap<>();
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
    private final Path root;
    private final String fixtureCase;
    private final Map<Session, AtomicInteger> channelCounts = new ConcurrentHashMap<>();
    private final Set<Session> authenticationAttempts = ConcurrentHashMap.newKeySet();
    private final Set<SshThreadPoolExecutor> sftpWorkers = ConcurrentHashMap.newKeySet();
    private final ByteArrayOutputStream control = new ByteArrayOutputStream();
    private volatile boolean workersRetired;
    private final AtomicBoolean authenticationClaimed = new AtomicBoolean();
    private final AtomicBoolean rekeyRequested = new AtomicBoolean();
    private final Map<Session, CountDownLatch> heldReads = new ConcurrentHashMap<>();
    private final Map<Session, AtomicLong> requestedBytes = new ConcurrentHashMap<>();
    private final AtomicReference<ServerSession> authenticated = new AtomicReference<>();
    private KeyPair originalKey;
    private KeyPair replacementKey;
    private int commandCount;

    private CloudySshSessionFixture(Path destination, Path suppliedRoot, String selectedCase) throws Exception {
        if (!List.of("basic", "same-key", "changed-key", "hold-read").contains(selectedCase)) {
            throw new IOException("FIXTURE_CASE");
        }
        fixtureCase = selectedCase;
        root = suppliedRoot.toAbsolutePath().normalize();
        if (!root.equals(root.toRealPath()) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(root).equals(PosixFilePermissions.fromString("rwx------"))
                || root.startsWith(destination) || destination.startsWith(root)) throw new IOException("FIXTURE_ROOT");
        // Only a small pre-provisioned synthetic tree; never expose links or the output/credential directory.
        try (var paths = Files.walk(root)) {
            List<Path> tree = paths.limit(129).toList();
            if (tree.size() > 128) throw new IOException("FIXTURE_ROOT_BOUND");
            long total = 0;
            for (Path path : tree) {
                if (Files.isSymbolicLink(path) || root.relativize(path).getNameCount() > 4) throw new IOException("FIXTURE_ROOT_LINK");
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 1_048_576L
                            || ((Number) Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() != 1) {
                        throw new IOException("FIXTURE_ROOT_FILE");
                    }
                    total += Files.size(path);
                }
            }
            if (total > 4_194_304L) throw new IOException("FIXTURE_ROOT_BOUND");
        }
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
        CloudySshSessionFixture fixture = null;
        int exit = 1;
        try {
            if (args.length != 3 || !(LoggerFactory.getILoggerFactory() instanceof NOPLoggerFactory)) {
                throw new IllegalArgumentException("FIXTURE_CONFIGURATION");
            }
            fixture = new CloudySshSessionFixture(Path.of(args[0]).toAbsolutePath().normalize(), Path.of(args[1]), args[2]);
            final CloudySshSessionFixture owned = fixture;
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
            long deadline = System.nanoTime() + Math.max(0, remaining);
            while (!fixture.stopping.get() && System.nanoTime() < deadline) {
                fixture.pollControl();
                Thread.sleep(50);
            }
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
        originalKey = hostKey;
        replacementKey = generator.generateKeyPair();
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
        server.setFileSystemFactory(new VirtualFileSystemFactory(root));
        SftpModuleProperties.MAX_OPEN_HANDLES_PER_SESSION.set(server, 16);
        SftpModuleProperties.MAX_READDATA_PACKET_LENGTH.set(server, 65_536);
        SftpModuleProperties.MAX_READDIR_DATA_SIZE.set(server, 16_384);
        SftpSubsystemFactory sftp = new SftpSubsystemFactory.Builder()
                .withExecutorServiceProvider(() -> {
                    SshThreadPoolExecutor worker = new SshThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                            new ArrayBlockingQueue<>(8), action -> {
                                Thread thread = new Thread(action, "cloudy-sftp-fixture-worker");
                                thread.setDaemon(true);
                                return thread;
                            });
                    sftpWorkers.add(worker);
                    return worker;
                })
                .withFileSystemAccessor(new SftpFileSystemAccessor() {
                    @Override public SeekableByteChannel openFile(SftpSubsystemProxy subsystem, FileHandle fileHandle,
                            Path file, String handle, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
                            throws IOException {
                        if (options.stream().anyMatch(option -> option != StandardOpenOption.READ
                                && option != LinkOption.NOFOLLOW_LINKS)
                                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                            event("sftp_mutation_denied", subsystem.getServerSession(), null, null);
                            throw new IOException("FIXTURE_READ_ONLY");
                        }
                        // The virtual filesystem supplies containment; deny every symlink component as well.
                        for (Path p = file.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
                            if (Files.isSymbolicLink(p)) throw new IOException("FIXTURE_LINK_DENIED");
                        }
                        return SftpFileSystemAccessor.DEFAULT.openFile(subsystem, fileHandle, file, handle,
                                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS), attrs);
                    }
                }).build();
        sftp.addSftpEventListener(new SftpEventListener() {
            @Override public void received(ServerSession session, int type, int id) throws IOException {
                String operation = sftpOperation(type);
                event("sftp_" + operation + "_request", session, null, null);
                if (operation.equals("denied")) throw new IOException("FIXTURE_READ_ONLY");
            }
            @Override public void receivedExtension(ServerSession session, String extension, int id) throws IOException {
                event("sftp_extension_denied", session, null, null);
                throw new IOException("FIXTURE_READ_ONLY");
            }
            @Override public void initialized(ServerSession session, int version) {
                event("sftp_initialized", session, null, null);
            }
            @Override public void destroying(ServerSession session) {
                event("sftp_destroying", session, null, null);
            }
            @Override public void reading(ServerSession session, String handle, FileHandle file,
                    long offset, byte[] data, int dataOffset, int dataLen) throws IOException {
                if (offset < 0 || offset > 1_048_576L || dataLen < 0 || dataLen > 65_536
                        || requestedBytes.computeIfAbsent(session, ignored -> new AtomicLong()).addAndGet(dataLen) > 8_388_608L) throw new IOException("FIXTURE_READ_BOUND");
                CountDownLatch releaseRead = new CountDownLatch(1);
                if (fixtureCase.equals("hold-read") && heldReads.putIfAbsent(session, releaseRead) == null) {
                    event("read_hold_entered", session, null, null);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    // This is the maintained subsystem worker, never NIO/auth/KEX processing.
                    // Poll actual closure so a cancelled client retires a genuinely pending read.
                    try {
                        while (releaseRead.getCount() != 0 && !stopping.get() && session.isOpen()
                                && System.nanoTime() < deadline) releaseRead.await(50, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("FIXTURE_READ_INTERRUPTED");
                    }
                    if (releaseRead.getCount() != 0 || stopping.get() || !session.isOpen()) {
                        event("read_hold_retired", session, null, null);
                        throw new IOException("FIXTURE_READ_RETIRED");
                    }
                    event("read_hold_released", session, null, null);
                }
            }
            @Override public void read(ServerSession session, String handle, FileHandle file,
                    long offset, byte[] data, int dataOffset, int dataLen, int readLen, Throwable thrown) {
                event(thrown == null ? "read_completed" : "read_failed", session, null, null);
            }
        });
        server.setSubsystemFactories(List.of(sftp));
        server.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);
        server.setGlobalRequestHandlers(Collections.emptyList());
        server.setChannelFactories(List.of(ChannelSessionFactory.INSTANCE));
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
                if (!authenticationAttempts.add(session)
                        || (!fixtureCase.equals("basic") && !fixtureCase.equals("hold-read")
                            && !authenticationClaimed.compareAndSet(false, true)) || !username.equals("fixture-session-" + fixtureCase)
                        || !password.equals(supplied)) {
                    event("auth_denied", session, null, null);
                    return false;
                }
                event("auth_accepted", session, null, null);
                return true;
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
                        if (cmd == SshConstants.SSH_MSG_CHANNEL_OPEN) {
                            event("channel_open", session, null, null);
                            if (channelCounts.computeIfAbsent(session, ignored -> new AtomicInteger()).incrementAndGet() > 4) { session.close(true); return; }
                        }
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
                if (event == Event.Authenticated) {
                    authenticated.set((ServerSession) session);
                    event("session_authenticated", session, null, null);
                }
                if (event == Event.KeyEstablished) event("key_established", session, null, null);
            }
            @Override public void sessionClosed(Session session) {
                event("session_closed", session, null, null);
                // Keep bounded correlation records until exit so late I/O completions retain their ID.
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
        config.setProperty("user", "fixture-session-" + fixtureCase);
        config.setProperty("case", fixtureCase);
        config.setProperty("replacementFingerprint", KeyUtils.getFingerPrint(BuiltinDigests.sha256, replacementKey.getPublic()));
        Path configPath = privateFile("fixture.properties");
        try (OutputStream output = Files.newOutputStream(configPath, StandardOpenOption.WRITE)) {
            config.store(output, "Private generated fixture credentials; never publish");
        }
        summary();
        System.out.println("READY port=" + server.getPort() + " fingerprint=" + fingerprint);
    }

    /** Poll only available stdin bytes on the finite main lane: no blocked/unowned control thread. */
    private void pollControl() throws Exception {
        int available = Math.min(System.in.available(), 64);
        for (int i = 0; i < available; i++) {
            int next = System.in.read();
            if (next == '\n') {
                if (++commandCount > 16) throw new IOException("FIXTURE_CONTROL_BOUND");
                String value = control.toString(StandardCharsets.US_ASCII);
                control.reset();
                command(value);
                if (stopping.get()) return;
            } else {
                if (next < 32 || next > 126 || control.size() >= 32) throw new IOException("FIXTURE_CONTROL");
                control.write(next);
            }
        }
    }

    private void command(String value) throws Exception {
        if (value.equals("shutdown")) { event("shutdown_requested", authenticated.get(), null, null); stop(); return; }
        if (value.equals("release-read") && fixtureCase.equals("hold-read")) {
            ServerSession target = authenticated.get();
            CountDownLatch held = target == null ? null : heldReads.get(target);
            if (held == null || !target.isOpen()) throw new IOException("FIXTURE_CONTROL");
            held.countDown(); event("release_requested", target, null, null); return;
        }
        boolean changed = value.equals("rekey-changed") && fixtureCase.equals("changed-key");
        boolean same = value.equals("rekey-same") && fixtureCase.equals("same-key");
        ServerSession session = authenticated.get();
        if ((!same && !changed) || session == null || !session.isAuthenticated() || !session.isOpen()
                || !rekeyRequested.compareAndSet(false, true)) throw new IOException("FIXTURE_CONTROL");
        // Public session-local provider is loaded again by DHGServer for this exchange; no global
        // server change, reflection, stale key cache mutation or manufactured verifier callback.
        ((AbstractServerSession) session).setKeyPairProvider(KeyPairProvider.wrap(changed ? replacementKey : originalKey));
        event(changed ? "changed_key_rekey_requested" : "same_key_rekey_requested", session, null, null);
        session.reExchangeKeys().addListener(future -> event(
                future.getException() == null ? "rekey_future_completed" : "rekey_future_failed", session, null, null));
    }

    private static String sftpOperation(int type) {
        switch (type) {
            case SftpConstants.SSH_FXP_INIT: return "init";
            case SftpConstants.SSH_FXP_OPEN: return "open";
            case SftpConstants.SSH_FXP_CLOSE: return "close";
            case SftpConstants.SSH_FXP_READ: return "read";
            case SftpConstants.SSH_FXP_OPENDIR: return "opendir";
            case SftpConstants.SSH_FXP_READDIR: return "readdir";
            case SftpConstants.SSH_FXP_STAT: return "stat";
            case SftpConstants.SSH_FXP_LSTAT: return "lstat";
            case SftpConstants.SSH_FXP_FSTAT: return "fstat";
            case SftpConstants.SSH_FXP_REALPATH: return "realpath";
            default: return "denied";
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
        int id = session == null ? 0 : sessionId(session);
        String mode = session == null ? fixtureCase : modes.getOrDefault(session, "unknown");
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
                .append(",\"serverClosed\":").append(closed).append(",\"workersRetired\":").append(workersRetired).append(",\"counts\":{");
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
        heldReads.values().forEach(CountDownLatch::countDown);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        try { closed = server.close(true).await(3_000); }
        catch (Exception exception) { ioFailed = true; }
        // Keep explicit handles even after library destruction to verify all supplied workers retire.
        sftpWorkers.forEach(SshThreadPoolExecutor::shutdownNow);
        for (SshThreadPoolExecutor worker : sftpWorkers) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try { worker.awaitTermination(remaining, TimeUnit.NANOSECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); ioFailed = true; break; }
        }
        workersRetired = sftpWorkers.stream().allMatch(SshThreadPoolExecutor::isTerminated);
        if (!workersRetired) ioFailed = true;
        try { summary(); } catch (IOException exception) { ioFailed = true; }
        stopped.countDown();
    }

    private String mode(String name) {
        return ("fixture-session-" + fixtureCase).equals(name) ? fixtureCase : "unknown";
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
