package dev.cloudy.qualification.stream;

import dev.cloudy.qualification.stream.playback.PlaybackPolicy;
import org.junit.Test;
import static org.junit.Assert.*;

/** Executes the actual staged fourth-overlay policy, not a replacement version parser. */
public final class ServerVersionCompatibilityTest {
    @Test public void exactPinnedSunshineApplicationSentinelIsAcceptedOnlyForApplicationVersion() {
        // Sunshine 63d35f702ee9e362e43263742981836ec0710384 src/nvhttp.h VERSION.
        assertTrue(PlaybackPolicy.serverVersion("7.1.431.-1",false));
        assertFalse(PlaybackPolicy.serverVersion("7.1.431.-1",true));
        assertTrue(PlaybackPolicy.serverVersion("3.23.0.74",true));
        assertTrue(PlaybackPolicy.serverVersion("7.1.431.0",false));
    }
    @Test public void noOtherNegativeComponentOrSentinelIsAdmitted() {
        for (String value:new String[]{"-7.1.431.0","7.-1.431.0","7.1.-431.0","7.1.431.-2",
                "7.1.431.-0","7.1.431.-01","7.1.431.+1","7.1.431.+0"}) {
            assertFalse(value,PlaybackPolicy.serverVersion(value,false));
            assertFalse(value,PlaybackPolicy.serverVersion(value,true));
        }
    }
    @Test public void existingComponentCountAndSizeBoundsRemainStrict() {
        assertTrue(PlaybackPolicy.serverVersion("99999.99999.99999.-1",false));
        for (String value:new String[]{"7.1.431","7.1.431.-1.0","7.1.431.-1\n"," 7.1.431.-1",
                "7.1.431.-1 ","7.1.431.−1","7.1.431.peer","7.1.999999.-1",
                "999999.1.431.-1","7.1.431.2147483648",""}) {
            assertFalse(value,PlaybackPolicy.serverVersion(value,false));
            assertFalse(value,PlaybackPolicy.serverVersion(value,true));
        }
    }
    @Test public void onlyAbsentOptionalGfeVersionRemainsAllowed() {
        assertTrue(PlaybackPolicy.serverVersion(null,true));
        assertFalse(PlaybackPolicy.serverVersion(null,false));
    }
}
