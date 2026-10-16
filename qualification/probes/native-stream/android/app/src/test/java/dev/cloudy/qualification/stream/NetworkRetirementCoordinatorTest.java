package dev.cloudy.qualification.stream;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NetworkRetirementCoordinatorTest {
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> work=new ArrayDeque<>();
        @Override public void execute(Runnable action) { work.add(action); }
        void drain() { while (!work.isEmpty()) work.remove().run(); }
    }
    private static final class Fixture {
        final Queue queue=new Queue();
        final List<String> trace=new ArrayList<>();
        boolean writeFails;
        final NetworkRetirementCoordinator owner=new NetworkRetirementCoordinator(queue,() -> {
            trace.add("terminal-write");
            if (writeFails) throw new IOException();
            trace.add("readback");
        },() -> { trace.add("release");trace.add("shutdown"); },() -> trace.add("write-failed"));
    }
    @Test public void normalCompletionCannotRetirePairOrCatalogAfterFailedTerminalWrite() {
        // Both network kinds use this same production guard before calling the
        // assertive retire method. Persistence failure remains owned, not a main-thread throw.
        assertFalse(NetworkRetirementCoordinator.normalRetirementReady(false,false,false));
        assertFalse(NetworkRetirementCoordinator.normalRetirementReady(true,true,false));
        assertFalse(NetworkRetirementCoordinator.normalRetirementReady(true,false,true));
        assertTrue(NetworkRetirementCoordinator.normalRetirementReady(true,false,false));
    }
    @Test public void ownerCloseWaitsForPromptThenActualWorkerReturn() {
        Fixture f=new Fixture();
        f.owner.workerSubmitted(); f.owner.promptStarted(); f.owner.closeOwner();
        f.queue.drain(); assertTrue(f.trace.isEmpty());
        f.owner.promptRetired(); f.queue.drain(); assertTrue(f.trace.isEmpty());
        f.owner.workerReturned(); assertFalse(f.owner.retired());
        f.queue.drain();
        assertEquals(List.of("terminal-write","readback","release","shutdown"),f.trace);
        assertTrue(f.owner.retired());
    }
    @Test public void latePromptAfterWorkerReturnSchedulesOnlyTerminalWork() {
        Fixture f=new Fixture();
        f.owner.workerSubmitted(); f.owner.promptStarted(); f.owner.closeOwner();
        f.owner.workerReturned(); f.queue.drain(); assertTrue(f.trace.isEmpty());
        f.owner.promptRetired();
        // A duplicate terminal callback after the final write is queued cannot
        // schedule a second write or restore an authenticate/network action.
        f.owner.promptRetired(); f.owner.closeOwner(); f.queue.drain();
        assertEquals(List.of("terminal-write","readback","release","shutdown"),f.trace);
    }
    @Test public void callbackBeforeOwnerCloseDoesNotRetireActiveOwner() {
        Fixture f=new Fixture();
        f.owner.promptStarted(); f.owner.promptRetired(); f.queue.drain();
        assertTrue(f.trace.isEmpty()); assertFalse(f.owner.retired());
        f.owner.closeOwner(); f.queue.drain();
        f.owner.promptRetired(); f.queue.drain();
        assertEquals(List.of("terminal-write","readback","release","shutdown"),f.trace);
    }
    @Test public void failedWriteKeepsExecutorAndLeaseUntilExplicitLocalRetry() {
        Fixture f=new Fixture();f.writeFails=true;
        f.owner.closeOwner(); f.queue.drain();
        assertEquals(List.of("terminal-write","write-failed"),f.trace);
        assertTrue(f.owner.failed());assertFalse(f.owner.retired());
        f.owner.promptRetired(); f.queue.drain();assertEquals(2,f.trace.size());
        f.writeFails=false;f.owner.retryTerminal();f.queue.drain();
        assertEquals(List.of("terminal-write","write-failed","terminal-write","readback","release","shutdown"),f.trace);
    }
    @Test public void closeRejectsNewWorkerAndPromptInsteadOfReplayingNetwork() {
        Fixture f=new Fixture();f.owner.closeOwner();
        try { f.owner.workerSubmitted();fail(); } catch (IllegalStateException expected) { assertFalse(f.owner.retired()); }
        try { f.owner.promptStarted();fail(); } catch (IllegalStateException expected) { assertFalse(f.owner.retired()); }
        f.queue.drain();assertTrue(f.owner.retired());
    }
}
