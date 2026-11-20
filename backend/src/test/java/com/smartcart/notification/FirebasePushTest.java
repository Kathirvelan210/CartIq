package com.smartcart.notification;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FirebasePushTest {
    @Test void disabledPushDoesNotRequireFirebaseConfiguration() {
        var push = new FirebasePush(false, "");
        assertFalse(push.enabled());
        assertDoesNotThrow(push::close);
    }

    @Test void invalidProjectStopsStartupBeforeCredentialLoading() {
        var problem = assertThrows(IllegalStateException.class, () -> new FirebasePush(true, "invalid/project"));
        assertTrue(problem.getMessage().contains("FIREBASE_PROJECT_ID"));
        assertFalse(problem.getMessage().contains("invalid/project"));
    }

    @Test void acceptedResponsesRequireAPlausibleProviderMessageId() {
        String providerId = "projects/cartiq-test/messages/0:123%fixture";
        var result = FirebasePush.classify(200, "{\"name\":\"" + providerId + "\"}", null);
        assertEquals(FirebasePush.Delivery.SENT, result.outcome());
        assertEquals(providerId, result.providerMessageId());
        assertNull(result.errorCode());
        for (String invalid : new String[]{ "{}", "not-json", "{\"name\":\"private fixture data\"}" }) {
            var unknown = FirebasePush.classify(200, invalid, null);
            assertEquals(FirebasePush.Delivery.UNCERTAIN, unknown.outcome());
            assertNull(unknown.providerMessageId());
        }
    }

    @Test void onlyAuthoritativeTokenRevocationPrunesAnInstallation() {
        var revoked = FirebasePush.classify(404, """
            {"error":{"details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}
            """, null);
        assertEquals(FirebasePush.Delivery.UNREGISTERED, revoked.outcome());
        assertEquals(FirebasePush.Delivery.PERMANENT,
            FirebasePush.classify(404, "{\"error\":{\"status\":\"NOT_FOUND\",\"message\":\"UNREGISTERED fixture\"}}", null).outcome());
        assertEquals(FirebasePush.Delivery.PERMANENT, FirebasePush.classify(403, "{}", null).outcome());
    }

    @Test void documentedRateLimitCanDeferButAmbiguousServerOutcomesNeverRetry() {
        Instant before = Instant.now();
        var retry = FirebasePush.classify(429, "{}", "120");
        assertEquals(FirebasePush.Delivery.DEFERRED, retry.outcome());
        assertNotNull(retry.retryAfter());
        assertTrue(retry.retryAfter().isAfter(before.plusSeconds(100)));
        assertEquals(FirebasePush.Delivery.UNCERTAIN, FirebasePush.classify(500, "{}", null).outcome());
        assertEquals(FirebasePush.Delivery.UNCERTAIN, FirebasePush.classify(503, "{}", "60").outcome());
    }
}
