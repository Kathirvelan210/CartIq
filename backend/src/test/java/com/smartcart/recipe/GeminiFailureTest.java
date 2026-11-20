package com.smartcart.recipe;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import static org.junit.jupiter.api.Assertions.*;

class GeminiFailureTest {
    @Test void privateConnectionAndTimeoutHaveNoGoogleStatus() {
        var refused = GeminiFailure.transport(new ResourceAccessException("fixture",new IOException()));
        var timeout = GeminiFailure.transport(new ResourceAccessException("fixture",new HttpTimeoutException("fixture")));
        assertEquals("AI_SERVICE_CONNECTION",refused.code);
        assertEquals("AI_SERVICE_TIMEOUT",timeout.code);
        assertTrue(refused.evidence.isEmpty());
        assertTrue(timeout.evidence.isEmpty());
    }

    @Test void dropsUnsafeMetadataAndRawPrivateResponse() {
        var headers=new HttpHeaders();
        headers.set("X-CartIQ-AI-Error","GEMINI_SERVER");
        headers.set("X-Gemini-Request-Id","Bearer private-text");
        headers.set("X-Gemini-HTTP-Status","unknown");
        var error=new RestClientResponseException("private-text",HttpStatus.BAD_GATEWAY,"fixture",headers,null,null);
        var failure=(GeminiFailure)GeminiFailure.from(error,"Unavailable.");
        assertTrue(failure.evidence.isEmpty());
        assertFalse(failure.getMessage().contains("private-text"));
    }
}
