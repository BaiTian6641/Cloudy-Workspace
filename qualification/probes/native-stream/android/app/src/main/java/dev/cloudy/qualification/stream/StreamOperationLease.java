package dev.cloudy.qualification.stream;

import java.util.concurrent.atomic.AtomicReference;

/** Process-wide admission remains held until the worker and any prompt have retired. */
final class StreamOperationLease {
    private final AtomicReference<Object> current=new AtomicReference<>();
    boolean claim(Object owner) { return owner!=null && current.compareAndSet(null,owner); }
    void release(Object owner) { current.compareAndSet(owner,null); }
    static boolean closed(boolean cancelled,boolean needsPrompt,boolean promptOutstanding) {
        return (cancelled || !needsPrompt) && !promptOutstanding;
    }
}
