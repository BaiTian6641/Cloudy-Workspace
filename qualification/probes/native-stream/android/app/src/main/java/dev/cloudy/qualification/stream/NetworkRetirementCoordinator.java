package dev.cloudy.qualification.stream;

import java.util.concurrent.Executor;

/** Actual cleared-owner barrier. Only local terminal persistence may run after close. */
final class NetworkRetirementCoordinator {
    interface Write { void terminalReadBack() throws Exception; }
    private final Executor executor;
    private final Write write;
    private final Runnable released,failed;
    private int workers;
    private boolean prompt,closing,queued,retired,writeFailed;
    NetworkRetirementCoordinator(Executor executor,Write write,Runnable released,Runnable failed) {
        this.executor=executor;this.write=write;this.released=released;this.failed=failed;
    }
    static boolean normalRetirementReady(boolean terminalEvidence,boolean workerRunning,boolean promptOutstanding) {
        return terminalEvidence && !workerRunning && !promptOutstanding;
    }
    synchronized void workerSubmitted() {
        if (closing || retired) throw new IllegalStateException("owner-closed");
        workers++;
    }
    synchronized void workerReturned() {
        if (workers<=0) throw new IllegalStateException("worker-not-owned");
        workers--; schedule();
    }
    synchronized void promptStarted() {
        if (closing || retired || prompt) throw new IllegalStateException("prompt-not-admitted");
        prompt=true;
    }
    synchronized void promptRetired() { prompt=false; schedule(); }
    synchronized void closeOwner() { closing=true; schedule(); }
    synchronized boolean failed() { return writeFailed; }
    synchronized boolean retired() { return retired; }
    synchronized void retryTerminal() { if (writeFailed && !retired) { writeFailed=false; schedule(); } }
    private void schedule() {
        if (!closing || workers!=0 || prompt || queued || retired || writeFailed) return;
        queued=true;
        try { executor.execute(this::finish); }
        catch (RuntimeException rejected) { queued=false; writeFailed=true; failed.run(); }
    }
    private void finish() {
        synchronized(this) {
            if (!closing || workers!=0 || prompt || retired) { queued=false; schedule(); return; }
        }
        try { write.terminalReadBack(); }
        catch (Exception failure) {
            synchronized(this) { queued=false;writeFailed=true; }
            failed.run(); return;
        }
        synchronized(this) { queued=false;retired=true; }
        // The executor remains usable until this actual readback barrier succeeds.
        released.run();
    }
}
