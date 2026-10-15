package dev.cloudy.qualification.stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class PromptModeTest {
    @Test public void credentialControlRejectsBiometricAndUnknownSuccessTypes() {
        StreamProbeViewModel.PromptMode mode = StreamProbeViewModel.PromptMode.DEVICE_CREDENTIAL;
        assertTrue(mode.acceptsAuthenticationType(1));
        assertFalse(mode.acceptsAuthenticationType(2));
        assertFalse(mode.acceptsAuthenticationType(-1));
        assertFalse(mode.acceptsAuthenticationType(0));
    }
    @Test public void combinedModeRetainsBothKnownTypesAndNoPromptCannotAuthorize() {
        assertTrue(StreamProbeViewModel.PromptMode.STRONG_OR_CREDENTIAL.acceptsAuthenticationType(1));
        assertTrue(StreamProbeViewModel.PromptMode.STRONG_OR_CREDENTIAL.acceptsAuthenticationType(2));
        assertFalse(StreamProbeViewModel.PromptMode.STRONG_OR_CREDENTIAL.acceptsAuthenticationType(-1));
        assertFalse(StreamProbeViewModel.PromptMode.NONE.acceptsAuthenticationType(1));
        assertFalse(StreamProbeViewModel.PromptMode.NONE.acceptsAuthenticationType(2));
    }
}
