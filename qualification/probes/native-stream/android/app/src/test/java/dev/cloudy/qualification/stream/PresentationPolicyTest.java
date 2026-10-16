package dev.cloudy.qualification.stream;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PresentationPolicyTest {
    private static PresentationTracker.Geometry visible() { return new PresentationTracker.Geometry(1280,720,1280,720,true,true,true); }
    @Test public void containmentPreservesVideoAspectInsideNarrowAndWideDesktopPanes() {
        for (int[] bounds:new int[][]{{2007,390},{320,700},{4096,200},{Integer.MAX_VALUE,Integer.MAX_VALUE}}) {
            PresentationLayout.Size size=PresentationLayout.contain(bounds[0],bounds[1]);
            assertTrue(size.width>0 && size.height>0 && size.width<=bounds[0] && size.height<=bounds[1]);
            assertTrue(Math.abs((long)size.width*9-(long)size.height*16)<16);
        }
        assertEquals(0,PresentationLayout.contain(0,600).width);
        assertEquals(0,PresentationLayout.contain(100,-1).height);
    }
    @Test public void shortWindowReservesFixedStopAndScrollableControlsBeforeVideo() {
        assertEquals(0,PresentationLayout.paneHeight(200,180,72));
        assertEquals(450,PresentationLayout.paneHeight(1000,120,72));
        assertTrue(PresentationLayout.paneHeight(Integer.MAX_VALUE,100,100)>0);
        assertEquals(0,PresentationLayout.paneHeight(-1,0,0));
    }
    @Test public void countdownNeverExtendsTheOriginalDeadlineOrGoesNegative() {
        assertEquals(30,PresentationLayout.remainingSeconds(1000,31000));
        assertEquals(1,PresentationLayout.remainingSeconds(30999,31000));
        assertEquals(0,PresentationLayout.remainingSeconds(31000,31000));
        assertEquals(0,PresentationLayout.remainingSeconds(32000,31000));
        assertEquals(0,PresentationLayout.remainingSeconds(1000,-1));
    }
    @Test public void admissionRequiresRealVisibleAttachedHardwareGeometryButNoVideoFrame() {
        PresentationTracker tracker=new PresentationTracker();assertFalse(tracker.begin(10));
        tracker.observe(PresentationTracker.Edge.AVAILABLE,11,new PresentationTracker.Geometry(100,100,0,0,true,true,true));
        assertFalse(tracker.begin(12));
        tracker.observe(PresentationTracker.Edge.VISIBILITY,13,visible());assertTrue(tracker.begin(14));
        assertEquals(0,tracker.snapshot().updates);assertNotNull(tracker.snapshot().admission);
        assertFalse(tracker.begin(15));
    }
    @Test public void frameBurstsAggregateWithoutGrowingSamplesAndOldCallbacksCannotReviveOwner() {
        PresentationTracker tracker=new PresentationTracker();tracker.observe(PresentationTracker.Edge.AVAILABLE,1,visible());
        assertFalse(tracker.updated(3));assertTrue(tracker.begin(2));assertTrue(tracker.updated(3));
        tracker.observe(PresentationTracker.Edge.FIRST_UPDATE,3,visible());
        for (int i=0;i<10000;i++) assertFalse(tracker.updated(4+i));
        assertEquals(10001,tracker.snapshot().updates);assertEquals(2,tracker.snapshot().samples.size());
        tracker.observe(PresentationTracker.Edge.DESTRUCTION,10004,new PresentationTracker.Geometry(1280,720,0,0,false,false,true));
        assertFalse(tracker.updated(10005));assertFalse(tracker.begin(10005));
        tracker.observe(PresentationTracker.Edge.RETIREMENT,10006,tracker.latest());
        tracker.observe(PresentationTracker.Edge.VISIBILITY,10007,visible());
        assertFalse(tracker.snapshot().latest.admissible());assertTrue(tracker.snapshot().retired);
        assertNotNull(tracker.snapshot().retirement);assertEquals(10001,tracker.snapshot().updates);
    }
    @Test public void actualUpdateTimesRetainFirstAndLastWithoutLateOrBackwardsMutation() {
        PresentationTracker tracker=new PresentationTracker();tracker.observe(PresentationTracker.Edge.AVAILABLE,100,visible());
        tracker.begin(110);assertEquals(-1,tracker.snapshot().lastUpdateElapsedMillis);
        assertTrue(tracker.updated(120));assertFalse(tracker.updated(125));
        assertFalse(tracker.updated(124)); // Invalid backwards clock input cannot corrupt timing/counts.
        assertEquals(2,tracker.snapshot().updates);
        assertEquals(120,tracker.snapshot().firstUpdateElapsedMillis);assertEquals(125,tracker.snapshot().lastUpdateElapsedMillis);
        tracker.observe(PresentationTracker.Edge.DESTRUCTION,130,new PresentationTracker.Geometry(1280,720,0,0,false,false,true));
        assertFalse(tracker.updated(140));tracker.observe(PresentationTracker.Edge.RETIREMENT,150,tracker.latest());
        assertFalse(tracker.updated(160));assertEquals(125,tracker.snapshot().lastUpdateElapsedMillis);
        assertEquals(120,tracker.snapshot().firstUpdateElapsedMillis);assertEquals(2,tracker.snapshot().updates);
    }
    @Test public void visibilitySamplesAreBoundedButRetirementAndFirstUpdateSurviveOverflow() {
        PresentationTracker tracker=new PresentationTracker();tracker.observe(PresentationTracker.Edge.AVAILABLE,1,visible());tracker.begin(2);
        PresentationTracker.Snapshot original=tracker.snapshot();
        for (int i=0;i<100;i++) tracker.observe(PresentationTracker.Edge.VISIBILITY,3+i,
                new PresentationTracker.Geometry(1280,720,i%2==0 ? 100 : 1280,720,true,true,true));
        tracker.updated(104);tracker.observe(PresentationTracker.Edge.FIRST_UPDATE,104,visible());
        tracker.observe(PresentationTracker.Edge.DESTRUCTION,105,new PresentationTracker.Geometry(1280,720,0,0,false,false,true));
        tracker.observe(PresentationTracker.Edge.RETIREMENT,106,tracker.latest());
        PresentationTracker.Snapshot last=tracker.snapshot();
        assertEquals(PresentationTracker.MAX_SAMPLES,last.samples.size());assertTrue(last.omittedSamples>0);
        assertNotNull(last.firstUpdate);assertNotNull(last.retirement);
        assertEquals(1,original.samples.size());assertEquals(0,original.updates);
        assertThrows(UnsupportedOperationException.class,() -> original.samples.clear());
    }
}
