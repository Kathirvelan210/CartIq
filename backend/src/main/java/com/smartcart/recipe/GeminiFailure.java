package com.smartcart.recipe;

import com.smartcart.exception.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

/** Only controlled codes and bounded metadata may cross the private service boundary. */
public final class GeminiFailure extends ApiException {
    private enum Reason {
        GEMINI_AUTH("Gemini rejected the service credential or its access permissions."),
        GEMINI_MODEL_OR_ENDPOINT("The configured Gemini model or endpoint was not found."),
        GEMINI_INVALID_REQUEST("Gemini rejected the generation request."),
        GEMINI_QUOTA("Gemini reported a quota or rate limit."),
        GEMINI_SERVER("Gemini reported a server error."),
        GEMINI_HTTP("Gemini returned an unexpected HTTP status."),
        GEMINI_TIMEOUT("The AI service timed out waiting for a response from Google. CartIQ did not retry. No recipe was received."),
        GEMINI_CONNECTION("The connection to Gemini failed."),
        GEMINI_DNS("The Gemini host could not be resolved."),
        GEMINI_TLS("The secure connection to Gemini failed."),
        GEMINI_INVALID_OUTPUT("Gemini returned incomplete or invalid model output."),
        AI_SERVICE_AUTH("The private AI service rejected CartIQ's service credential."),
        AI_SERVICE_TIMEOUT("The private AI service did not respond in time."),
        AI_SERVICE_CONNECTION("CartIQ could not communicate with the private AI service.");

        final String message;
        Reason(String message) { this.message = message; }
    }

    public final String code;
    public final HttpHeaders evidence;

    private GeminiFailure(Reason reason, HttpStatus status, HttpHeaders headers) {
        super(status, reason.message);
        code = reason.name();
        evidence = safeEvidence(headers);
    }

    static HttpHeaders safeEvidence(HttpHeaders headers) {
        var evidence = new HttpHeaders();
        copy(headers,evidence,"X-Gemini-HTTP-Status","[1-5][0-9]{2}");
        copy(headers,evidence,"X-Gemini-Request-Id","(?!AIza)[A-Za-z0-9_-]{1,128}");
        return evidence;
    }

    private static void copy(HttpHeaders headers,HttpHeaders evidence,String name,String pattern) {
        String value = headers == null ? null : headers.getFirst(name);
        if (value != null && value.matches(pattern)) evidence.set(name,value);
    }

    static GeminiFailure transport(ResourceAccessException failure) {
        for(Throwable cause=failure; cause!=null; cause=cause.getCause()) {
            if(cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException)
                return new GeminiFailure(Reason.AI_SERVICE_TIMEOUT,HttpStatus.GATEWAY_TIMEOUT,null);
        }
        return new GeminiFailure(Reason.AI_SERVICE_CONNECTION,HttpStatus.BAD_GATEWAY,null);
    }

    static ApiException from(RestClientResponseException ex,String fallback) {
        int internal = ex.getStatusCode().value();
        var headers = ex.getResponseHeaders();
        String code = headers == null ? null : headers.getFirst("X-CartIQ-AI-Error");
        var status = switch (internal) {
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            case 504 -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        if (internal == 401 || internal == 403) return new GeminiFailure(Reason.AI_SERVICE_AUTH,status,null);
        if (code != null) {
            try { return new GeminiFailure(Reason.valueOf(code),status,headers); }
            catch (IllegalArgumentException ignored) { /* Unrecognized private-service response. */ }
        }
        return new ApiException(status,fallback);
    }
}
