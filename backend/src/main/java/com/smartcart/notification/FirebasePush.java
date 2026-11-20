package com.smartcart.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class FirebasePush {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RESPONSE = 65536;
    private static final String FCM_ERROR = "type.googleapis.com/google.firebase.fcm.v1.FcmError";
    private final GoogleCredentials credentials;
    private final String endpoint;
    private final CloseableHttpClient transport;

    FirebasePush(@Value("${smartcart.firebase-enabled}") boolean enabled,
                 @Value("${smartcart.firebase-project-id}") String project) {
        credentials = enabled ? configure(project) : null;
        endpoint = enabled ? "https://fcm.googleapis.com/v1/projects/" + project + "/messages:send" : null;
        // The queue owns all message retries. Disable transport retries, redirects and POST authentication replay.
        transport = enabled ? HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(5)).build())
                .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(Timeout.ofSeconds(15)).build()).build())
            .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(15)).setAuthenticationEnabled(false).build())
            .disableAutomaticRetries().disableRedirectHandling().build() : null;
    }

    private static GoogleCredentials configure(String project) {
        if (!project.matches("[a-z][a-z0-9-]{4,28}[a-z0-9]")) {
            throw new IllegalStateException("Set FIREBASE_PROJECT_ID to the Firebase project used by the clients.");
        }
        try {
            return GoogleCredentials.getApplicationDefault().createScoped("https://www.googleapis.com/auth/firebase.messaging");
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Firebase is enabled but application-default credentials could not be loaded.");
        }
    }

    @jakarta.annotation.PreDestroy
    void close() {
        if (transport != null) {
            try { transport.close(); }
            catch (IOException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not close the Firebase HTTP transport.");
            }
        }
    }

    boolean enabled() { return credentials != null; }

    enum Delivery { SENT, UNREGISTERED, DEFERRED, PERMANENT, UNCERTAIN }

    record Result(Delivery outcome, String providerMessageId, String errorCode, Instant retryAfter) {
        Result(Delivery outcome, String providerMessageId, String errorCode) {
            this(outcome, providerMessageId, errorCode, null);
        }
    }

    Result send(String token, String noticeId) {
        HttpPost request = new HttpPost(endpoint);
        try {
            credentials.refreshIfExpired();
            request.setHeader("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue());
            var message = Map.of(
                "token", token,
                "notification", Map.of("title", "CartIQ", "body", "You have a shopping update."),
                "data", Map.of("notificationId", noticeId),
                "android", Map.of("notification", Map.of("channel_id", "shopping-updates", "tag", noticeId)),
                "apns", Map.of("headers", Map.of("apns-collapse-id", noticeId)),
                "webpush", Map.of("notification", Map.of("tag", noticeId))
            );
            request.setEntity(new StringEntity(JSON.writeValueAsString(Map.of("message", message)), ContentType.APPLICATION_JSON));
        } catch (IOException | RuntimeException failure) {
            return new Result(Delivery.PERMANENT, null, "PREPARATION_FAILED");
        }

        try (var response = transport.executeOpen(null, request, null)) {
            int status = response.getCode();
            var retry = response.getFirstHeader("Retry-After");
            String body = null;
            if ((status == 200 || status == 404) && response.getEntity() != null) {
                var bytes = response.getEntity().getContent().readNBytes(MAX_RESPONSE + 1);
                if (bytes.length <= MAX_RESPONSE) body = new String(bytes, StandardCharsets.UTF_8);
            }
            return classify(status, body, retry == null ? null : retry.getValue());
        } catch (IOException | RuntimeException failure) {
            return new Result(Delivery.UNCERTAIN, null, "TRANSPORT_UNKNOWN");
        }
    }

    static Result classify(int status, String body, String retryAfter) {
        if (status == 429) return new Result(Delivery.DEFERRED, null, "RATE_LIMITED", retryAt(retryAfter));
        if (status == 200) {
            String id = object(body).path("name").asText();
            if (id.matches("projects/[A-Za-z0-9_-]{1,128}/messages/[A-Za-z0-9_:%+.~-]{1,128}")) {
                return new Result(Delivery.SENT, id, null);
            }
            return new Result(Delivery.UNCERTAIN, null, "INVALID_RECEIPT");
        }
        if (status == 404) {
            for (var detail : object(body).path("error").path("details")) {
                if (FCM_ERROR.equals(detail.path("@type").asText()) && "UNREGISTERED".equals(detail.path("errorCode").asText())) {
                    return new Result(Delivery.UNREGISTERED, null, "UNREGISTERED");
                }
            }
        }
        if (status >= 400 && status < 500) return new Result(Delivery.PERMANENT, null, "HTTP_" + status);
        return new Result(Delivery.UNCERTAIN, null, "HTTP_" + status);
    }

    private static JsonNode object(String body) {
        if (body != null) {
            try {
                var node = JSON.readTree(body);
                if (node != null && node.isObject()) return node;
            } catch (IOException invalid) { /* Do not log provider bodies. */ }
        }
        return JSON.createObjectNode();
    }

    private static Instant retryAt(String value) {
        if (value == null) return null;
        try {
            if (value.matches("[0-9]{1,18}")) return Instant.now().plusSeconds(Long.parseLong(value));
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeException | ArithmeticException invalid) {
            return null;
        }
    }
}
