package dev.cloudy.qualification.stream;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public final class CatalogPolicyTest {
    @Test public void staleCatalogAndMissingIdCannotRestoreSelection() {
        assertTrue(CatalogPolicy.selected(8,8,123,true));
        assertFalse(CatalogPolicy.selected(9,8,123,true));
        assertFalse(CatalogPolicy.selected(8,8,123,false));
        assertFalse(CatalogPolicy.selected(8,8,0,true));
        assertFalse(CatalogPolicy.selected(0,0,123,true));
    }
    @Test public void nativeTitlesAreBoundedAndRemoveInvisibleControls() {
        assertEquals("Editor",CatalogPolicy.title("\u202eEd\u0000itor\n"));
        assertEquals(96,CatalogPolicy.title("x".repeat(1000)).length());
        assertEquals(96,CatalogPolicy.title("\ud83d\ude00".repeat(100)).codePointCount(0,192));
        assertThrows(IllegalArgumentException.class,() -> CatalogPolicy.title("x".repeat(1025)));
        assertThrows(IllegalArgumentException.class,() -> new CatalogEntry(-3,"Editor"));
    }
    @Test public void duplicateNonpositiveAndExcessCatalogEntriesFailClosed() {
        com.limelight.nvstream.http.NvApp app=new com.limelight.nvstream.http.NvApp("Editor",4321,false);
        assertEquals(4321,CatalogPolicy.entries(java.util.List.of(app)).get(0).id);
        assertThrows(IllegalArgumentException.class,() -> CatalogPolicy.entries(java.util.List.of(app,app)));
        assertThrows(IllegalArgumentException.class,() -> CatalogPolicy.entries(java.util.List.of(new com.limelight.nvstream.http.NvApp("Bad",0,false))));
        java.util.ArrayList<com.limelight.nvstream.http.NvApp> tooMany=new java.util.ArrayList<>();
        for (int i=1;i<=129;i++) tooMany.add(new com.limelight.nvstream.http.NvApp("App",i,false));
        assertThrows(IllegalArgumentException.class,() -> CatalogPolicy.entries(tooMany));
        assertTrue(CatalogPolicy.entries(java.util.List.of()).isEmpty());
    }
    @Test public void originalDeadlineDoesNotRestartAfterQualification() throws Exception {
        AtomicLong now=new AtomicLong(1100);
        CatalogLease lease=new CatalogLease(now::get,1000,30_000);
        assertEquals(29900,lease.remaining());
        now.set(30_999);assertEquals(1,lease.remaining());
        now.set(31_000);assertThrows(java.io.IOException.class,lease::remaining);
    }
    @Test public void cancellationReachesActualRegisteredCallAndPreventsLatePublication() throws Exception {
        CatalogLease lease=new CatalogLease(() -> 1000L,1000,30_000);
        java.util.concurrent.atomic.AtomicInteger cancelled=new java.util.concurrent.atomic.AtomicInteger();
        CatalogLease.ActiveCall call=cancelled::incrementAndGet;
        lease.register(call);lease.cancel();assertEquals(1,cancelled.get());
        lease.retire(call);lease.cancel();assertEquals(1,cancelled.get());
        assertThrows(java.io.IOException.class,() -> lease.register(call));
    }
}
