package dev.cloudy.qualification.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequestsTest {
    @Test fun oldDeviceReplyDoesNotConsumeNewRequest() {
        val requests = PermissionRequests()
        val old = requests.begin(1, 10, "old")
        val current = requests.begin(2, 20, "current")
        assertNull(requests.complete(old.generation, old.deviceId, old.nonce, 2, 20))
        assertTrue(requests.isPending(current))
        assertEquals(current, requests.complete(2, 20, "current", 2, 20))
    }
    @Test fun sameDeviceOldReplyCannotAcceptNewerGeneration() {
        val requests = PermissionRequests()
        requests.begin(1, 10, "old")
        val current = requests.begin(2, 10, "current")
        assertNull(requests.complete(1, 10, "old", 2, 10))
        assertTrue(requests.isPending(current))
    }
    @Test fun missingCreatorIdentityNeverConsumesPending() {
        val requests = PermissionRequests()
        val current = requests.begin(2, 10, "current")
        assertNull(requests.complete(-1, -1, null, 2, 10))
        assertTrue(requests.isPending(current))
    }
    @Test fun matchingCreatorIdentityWorksWithoutSystemDeviceOrGrantExtras() {
        val requests = PermissionRequests()
        val current = requests.begin(2, 10, "current")
        assertEquals(current, requests.complete(2, 10, "current", 2, 10))
        assertFalse(requests.isPending(current))
        assertNull(requests.complete(2, 10, "current", 2, 10))
    }
    @Test fun cancellationRejectsLateReply() {
        val requests = PermissionRequests()
        requests.begin(2, 10, "current"); requests.cancel()
        assertNull(requests.complete(2, 10, "current", 2, 10))
    }
    @Test fun selectedDeviceOrGenerationMustStillMatch() {
        val requests = PermissionRequests()
        val current = requests.begin(2, 10, "current")
        assertNull(requests.complete(2, 10, "current", 3, 10))
        assertNull(requests.complete(2, 10, "current", 2, 20))
        assertTrue(requests.isPending(current))
    }
    @Test fun nonceCannotBeReplacedEvenWithMatchingGenerationAndDevice() {
        val requests = PermissionRequests()
        val current = requests.begin(2, 10, "current")
        assertNull(requests.complete(2, 10, "other", 2, 10))
        assertTrue(requests.isPending(current))
    }
}
