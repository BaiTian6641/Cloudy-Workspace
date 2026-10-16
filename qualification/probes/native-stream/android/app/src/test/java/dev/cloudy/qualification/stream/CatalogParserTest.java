package dev.cloudy.qualification.stream;

import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.NvApp;
import java.io.StringReader;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Calls the real composed upstream parser, with kxml2 only in the JVM test closure. */
public final class CatalogParserTest {
    @Test public void realReturnedIdsAreNotPositionsAndWhitespaceIsAccepted() throws Exception {
        List<NvApp> apps=NvHTTP.cloudyGetAppListByReader(new StringReader(
                "<root status_code=\"200\">\n <App><AppTitle>Editor</AppTitle><ID>4321</ID></App>\n"
                +"<App><AppTitle>Desktop</AppTitle><ID>987654</ID></App></root>"));
        assertEquals(2,apps.size());assertEquals(4321,apps.get(0).getAppId());assertEquals(987654,apps.get(1).getAppId());
    }
    @Test public void emptyCatalogDoesNotInventDesktop() throws Exception {
        assertTrue(NvHTTP.cloudyGetAppListByReader(new StringReader("<root status_code=\"200\">\n</root>")).isEmpty());
    }
    @Test public void malformedIdFailsWithFixedErrorBeforeNvAppLogging() {
        IOException failure=assertThrows(IOException.class,() -> NvHTTP.cloudyGetAppListByReader(new StringReader(
                "<root status_code=\"200\"><App><ID>PRIVATE_MALFORMED_VALUE</ID></App></root>")));
        assertEquals("catalog-id-rejected",failure.getMessage());
    }
    @Test public void protocolFailureStillUsesUpstreamStatusValidation() {
        assertThrows(IOException.class,() -> NvHTTP.cloudyGetAppListByReader(new StringReader("<root status_code=\"401\"/>")));
    }
}
