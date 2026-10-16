package dev.cloudy.qualification.vnc;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VncDiagnosticsTest {
    @Test public void firstObservationWinsAndAttemptsAreIndependent() {
        VncDiagnostics old=new VncDiagnostics(), next=new VncDiagnostics();
        old.mark(VncDiagnostics.Time.FRAME,150);
        old.mark(VncDiagnostics.Time.FRAME,200);
        assertEquals(150,old.time(VncDiagnostics.Time.FRAME));
        assertEquals(-1,next.time(VncDiagnostics.Time.FRAME));
        next.mark(VncDiagnostics.Time.ACCEPTED,500);
        assertEquals("worker-queued",next.phase());
        next.mark(VncDiagnostics.Time.CONNECTED,550);
        assertEquals("waiting-first-frame",next.phase());
        next.mark(VncDiagnostics.Time.FRAME,560);
        assertEquals("waiting-frame-conversion",next.phase());
        next.mark(VncDiagnostics.Time.CONVERTED,565);
        assertEquals("waiting-first-draw",next.phase());
        next.mark(VncDiagnostics.Time.DRAWN,570);
        assertEquals("frame-drawn",next.phase());
        next.mark(VncDiagnostics.Time.STOP,600);
        assertEquals("stopping",next.phase());
    }
    @Test public void observationsDoNotInventAdmissionAndCountersSaturate() {
        VncDiagnostics value=new VncDiagnostics();
        for (int i=0;i<5000;i++) value.count(VncDiagnostics.Marker.DOWN_SEEN);
        assertEquals(4000,value.countOf(VncDiagnostics.Marker.DOWN_SEEN));
        assertEquals(0,value.countOf(VncDiagnostics.Marker.DOWN_QUEUED));
        assertEquals(-1,value.time(VncDiagnostics.Time.CONNECTED));
    }
    @Test public void concurrentMilestoneIsRecordedOnlyOnce() throws InterruptedException {
        VncDiagnostics value=new VncDiagnostics();
        Thread first=new Thread(() -> value.mark(VncDiagnostics.Time.FRAME,100));
        Thread second=new Thread(() -> value.mark(VncDiagnostics.Time.FRAME,101));
        first.start(); second.start(); first.join(); second.join();
        long observed=value.time(VncDiagnostics.Time.FRAME);
        assertTrue(observed==100 || observed==101);
        value.mark(VncDiagnostics.Time.FRAME,200);
        assertEquals(observed,value.time(VncDiagnostics.Time.FRAME));
    }
}
