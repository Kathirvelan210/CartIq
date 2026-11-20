package com.smartcart.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.repository.Db;
import com.smartcart.service.AuthService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cartiq_push_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
class NotificationLifecycleTest {
    private static final String DEVICES = "/api/me/device-tokens";
    private static final String OLD_DEVICE = "fixture:old-device";
    private static final String CURRENT_DEVICE = "fixture:current-device";
    private static final String OTHER_DEVICE = "fixture:another-device";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Db db;
    @Autowired NotificationService notifications;
    @MockitoBean FirebasePush push;

    private record Account(String id, String accessToken) {}

    @BeforeEach void enableMockDelivery() {
        when(push.enabled()).thenReturn(true);
    }

    @Test void rotationAndLogoutAffectOnlyTheSignedInDevices() throws Exception {
        var first = registerAccount();
        var second = registerAccount();
        registerDevice(first, OLD_DEVICE);
        registerDevice(first, OTHER_DEVICE);

        mvc.perform(body(delete(DEVICES), second, Map.of("token", OLD_DEVICE))).andExpect(status().isNoContent());
        assertEquals(1, registrations(first, OLD_DEVICE));
        mvc.perform(body(post(DEVICES), second, Map.of("token", CURRENT_DEVICE, "previousToken", OLD_DEVICE)))
            .andExpect(status().isNoContent());
        assertEquals(1, registrations(first, OLD_DEVICE));

        mvc.perform(body(post(DEVICES), first, Map.of("token", CURRENT_DEVICE, "previousToken", OLD_DEVICE)))
            .andExpect(status().isNoContent());
        registerDevice(first, CURRENT_DEVICE);
        assertEquals(0, registrations(first, OLD_DEVICE));
        assertEquals(0, registrations(second, CURRENT_DEVICE));
        assertEquals(1, registrations(first, CURRENT_DEVICE));

        mvc.perform(body(post("/api/auth/logout"), first, Map.of("token", CURRENT_DEVICE))).andExpect(status().isNoContent());
        assertEquals(0, registrations(first, CURRENT_DEVICE));
        assertEquals(1, registrations(first, OTHER_DEVICE));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + first.accessToken())).andExpect(status().isUnauthorized());
    }

    @Test void registrationRequiresAuthenticationValidInputAndAnEnabledWorker() throws Exception {
        var account = registerAccount();
        mvc.perform(post(DEVICES).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fixture:device\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(body(post(DEVICES), account, Map.of("token", "unexpected token markup"))).andExpect(status().isBadRequest());
        when(push.enabled()).thenReturn(false);
        mvc.perform(body(post(DEVICES), account, Map.of("token", CURRENT_DEVICE))).andExpect(status().isServiceUnavailable());
        assertEquals(0, registrations(account, CURRENT_DEVICE));
    }

    @Test void accountOptOutCancelsPendingPushAndDisallowsRegistration() throws Exception {
        var account = registerAccount();
        registerDevice(account, CURRENT_DEVICE);
        assertTrue(notifications.emit(account.id(), "Fixture title", "Fixture body", "TARGET", "pending-test", false));
        assertEquals("PUSH_PENDING", state("pending-test"));

        setPreferences(account, false);
        assertEquals("IN_APP", state("pending-test"));
        assertFalse(notifications.emit(account.id(), "Fixture title", "Fixture body", "TARGET", "opt-out-test", false));
        mvc.perform(body(post(DEVICES), account, Map.of("token", OTHER_DEVICE))).andExpect(status().isBadRequest());
        setPreferences(account, true);
        notifications.deliver();
        verify(push, never()).send(anyString(), anyString());
    }

    @Test void deliveryPrunesUnregisteredDevicesAndKeepsSampleNoticesInApp() throws Exception {
        var account = registerAccount();
        registerDevice(account, CURRENT_DEVICE);
        registerDevice(account, OLD_DEVICE);
        when(push.send(eq(CURRENT_DEVICE), anyString())).thenReturn(accepted("fixture-message-1"));
        when(push.send(eq(OLD_DEVICE), anyString())).thenReturn(failed(FirebasePush.Delivery.UNREGISTERED, "UNREGISTERED"));
        assertTrue(notifications.emit(account.id(), "Fixture title", "Fixture body", "TARGET", "actual-test", false));
        assertTrue(notifications.emit(account.id(), "Sample title", "Sample body", "TARGET", "sample-test", true));

        notifications.deliver();
        notifications.deliver();

        assertEquals(1, registrations(account, CURRENT_DEVICE));
        assertEquals(0, registrations(account, OLD_DEVICE));
        assertEquals("SENT", state("actual-test"));
        assertEquals("IN_APP", state("sample-test"));
        assertEquals(2, receipts("actual-test").size());
        assertEquals(0, receipts("sample-test").size());
        verify(push).send(eq(CURRENT_DEVICE), anyString());
        verify(push).send(eq(OLD_DEVICE), anyString());
    }

    @Test void staleTokensAndOldPushWorkExpireWhileTemporaryFailuresStayPending() throws Exception {
        var account = registerAccount();
        registerDevice(account, OLD_DEVICE);
        registerDevice(account, CURRENT_DEVICE);
        db.run("UPDATE device_tokens SET updated_at=? WHERE user_id=? AND token_hash=?",
            Instant.now().minus(61, ChronoUnit.DAYS), account.id(), AuthService.hash(OLD_DEVICE));
        assertTrue(notifications.emit(account.id(), "Fixture title", "Fixture body", "TARGET", "stale-test", false));
        assertTrue(notifications.emit(account.id(), "Fixture title", "Fixture body", "TARGET", "deferred-test", false));
        db.run("UPDATE notifications SET created_at=? WHERE dedupe_key=?", Instant.now().minus(3, ChronoUnit.DAYS), "stale-test");
        when(push.send(eq(CURRENT_DEVICE), anyString())).thenReturn(failed(FirebasePush.Delivery.DEFERRED, "QUOTA_EXCEEDED"));

        notifications.deliver();
        notifications.expireTokens();

        assertEquals(0, registrations(account, OLD_DEVICE));
        assertEquals(1, registrations(account, CURRENT_DEVICE));
        assertEquals("IN_APP", state("stale-test"));
        assertEquals("PUSH_QUEUED", state("deferred-test"));
        verify(push).send(eq(CURRENT_DEVICE), anyString());
        verify(push, never()).send(eq(OLD_DEVICE), anyString());
    }

    @Test void partialDeliveryRetriesOnlyTheRejectedDeviceAndHonorsBackoff() throws Exception {
        var account = registerAccount();
        registerDevice(account, CURRENT_DEVICE);
        registerDevice(account, OTHER_DEVICE);
        // The worker dispatches by device ID and stops for this tick on a rate limit.
        db.run("UPDATE device_tokens SET id='fixture-first-device' WHERE token_hash=?", AuthService.hash(CURRENT_DEVICE));
        db.run("UPDATE device_tokens SET id='fixture-second-device' WHERE token_hash=?", AuthService.hash(OTHER_DEVICE));
        String notice = emit(account, "partial-delivery");
        when(push.send(CURRENT_DEVICE, notice)).thenReturn(accepted("fixture-first"));
        when(push.send(OTHER_DEVICE, notice)).thenReturn(failed(FirebasePush.Delivery.DEFERRED, "QUOTA_EXCEEDED"),
            accepted("fixture-second"));

        notifications.deliver();
        assertEquals("PUSH_QUEUED", state("partial-delivery"));
        var first = receipt(notice, CURRENT_DEVICE);
        var second = receipt(notice, OTHER_DEVICE);
        assertEquals("SENT", Db.text(first, "status"));
        assertEquals(1, Db.num(first, "attempt_count"));
        assertEquals("fixture-first", Db.text(first, "provider_message_id"));
        assertEquals("RETRY", Db.text(second, "status"));
        assertEquals(1, Db.num(second, "attempt_count"));
        assertEquals("QUOTA_EXCEEDED", Db.text(second, "last_error"));
        assertTrue(Db.time(second, "next_attempt_at").isAfter(Instant.now()));
        mvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + account.accessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].deliveryStatus").value("PUSH_PENDING"))
            .andExpect(jsonPath("$[0].providerMessageId").doesNotExist()).andExpect(jsonPath("$[0].lastError").doesNotExist());

        notifications.deliver();
        verify(push).send(OTHER_DEVICE, notice);
        makeRetryDue(notice);
        notifications.deliver();
        notifications.deliver();
        assertEquals("SENT", state("partial-delivery"));
        assertEquals("SENT", Db.text(receipt(notice, OTHER_DEVICE), "status"));
        assertEquals(2, Db.num(receipt(notice, OTHER_DEVICE), "attempt_count"));
        assertEquals("fixture-second", Db.text(receipt(notice, OTHER_DEVICE), "provider_message_id"));
        verify(push).send(CURRENT_DEVICE, notice);
        verify(push, times(2)).send(OTHER_DEVICE, notice);
    }

    @Test void quotaRejectionsStopAfterThreeAttemptsAndLateDevicesAreExcluded() throws Exception {
        var account = registerAccount();
        registerDevice(account, CURRENT_DEVICE);
        String notice = emit(account, "quota-limit");
        when(push.send(CURRENT_DEVICE, notice)).thenReturn(failed(FirebasePush.Delivery.DEFERRED, "QUOTA_EXCEEDED"));
        notifications.deliver();
        registerDevice(account, OTHER_DEVICE);
        for (int attempt = 2; attempt <= 3; attempt++) {
            makeRetryDue(notice);
            notifications.deliver();
        }
        makeRetryDue(notice);
        notifications.deliver();
        var failed = receipt(notice, CURRENT_DEVICE);
        assertEquals("FAILED", Db.text(failed, "status"));
        assertEquals(3, Db.num(failed, "attempt_count"));
        assertEquals(1, receipts("quota-limit").size());
        verify(push, times(3)).send(CURRENT_DEVICE, notice);
        verify(push, never()).send(OTHER_DEVICE, notice);
    }

    @Test void providerCooldownPausesOtherReadyRecipientsAcrossWorkerTicks() throws Exception {
        var account = registerAccount();
        registerDevice(account, CURRENT_DEVICE);
        registerDevice(account, OTHER_DEVICE);
        db.run("UPDATE device_tokens SET id='fixture-first-device' WHERE token_hash=?", AuthService.hash(CURRENT_DEVICE));
        db.run("UPDATE device_tokens SET id='fixture-second-device' WHERE token_hash=?", AuthService.hash(OTHER_DEVICE));
        String notice = emit(account, "provider-cooldown");
        Instant retryAfter = Instant.now().plusSeconds(600);
        when(push.send(CURRENT_DEVICE, notice)).thenReturn(
            new FirebasePush.Result(FirebasePush.Delivery.DEFERRED, null, "RATE_LIMITED", retryAfter),
            accepted("fixture-first"));
        when(push.send(OTHER_DEVICE, notice)).thenReturn(accepted("fixture-second"));

        notifications.deliver();
        assertTrue(Db.time(receipt(notice, CURRENT_DEVICE), "provider_retry_at").isAfter(Instant.now()));
        notifications.deliver();
        verify(push, never()).send(OTHER_DEVICE, notice);

        makeRetryDue(notice);
        notifications.deliver();
        assertEquals("SENT", state("provider-cooldown"));
        verify(push, times(2)).send(CURRENT_DEVICE, notice);
        verify(push).send(OTHER_DEVICE, notice);
    }

    @Test void permanentAndUncertainDeliveryAreNeverRetried() throws Exception {
        var account = registerAccount();
        registerDevice(account, CURRENT_DEVICE);
        registerDevice(account, OTHER_DEVICE);
        String notice = emit(account, "terminal-outcomes");
        when(push.send(CURRENT_DEVICE, notice)).thenReturn(failed(FirebasePush.Delivery.PERMANENT, "INVALID_ARGUMENT"));
        when(push.send(OTHER_DEVICE, notice)).thenReturn(failed(FirebasePush.Delivery.UNCERTAIN, "NETWORK"));
        notifications.deliver();
        notifications.deliver();
        assertEquals("FAILED", Db.text(receipt(notice, CURRENT_DEVICE), "status"));
        assertEquals("UNCERTAIN", Db.text(receipt(notice, OTHER_DEVICE), "status"));
        assertEquals(1, registrations(account, CURRENT_DEVICE));
        assertEquals(1, registrations(account, OTHER_DEVICE));
        verify(push).send(CURRENT_DEVICE, notice);
        verify(push).send(OTHER_DEVICE, notice);
    }

    @Test void optingOutCancelsARetryWithoutDeliveringItOnReenable() throws Exception {
        var account = registerAccount();
        registerDevice(account, CURRENT_DEVICE);
        String notice = emit(account, "opt-out-retry");
        when(push.send(CURRENT_DEVICE, notice)).thenReturn(failed(FirebasePush.Delivery.DEFERRED, "QUOTA_EXCEEDED"));
        notifications.deliver();
        assertEquals("RETRY", Db.text(receipt(notice, CURRENT_DEVICE), "status"));
        setPreferences(account, false);
        setPreferences(account, true);
        makeRetryDue(notice);
        notifications.deliver();
        assertEquals("CANCELLED", Db.text(receipt(notice, CURRENT_DEVICE), "status"));
        assertEquals("IN_APP", state("opt-out-retry"));
        verify(push).send(CURRENT_DEVICE, notice);
    }

    @Test void revokingDeviceKeepsItsReceiptAndPreventsFurtherDelivery() throws Exception {
        var account = registerAccount();
        registerDevice(account, CURRENT_DEVICE);
        String notice = emit(account, "revoked-retry");
        String device = registrationId(CURRENT_DEVICE);
        when(push.send(CURRENT_DEVICE, notice)).thenReturn(failed(FirebasePush.Delivery.DEFERRED, "QUOTA_EXCEEDED"));
        notifications.deliver();
        assertEquals("RETRY", Db.text(receipt(notice, CURRENT_DEVICE), "status"));
        mvc.perform(body(delete(DEVICES), account, Map.of("token", CURRENT_DEVICE))).andExpect(status().isNoContent());
        makeRetryDue(notice);
        notifications.deliver();
        assertEquals(0, registrations(account, CURRENT_DEVICE));
        assertEquals("CANCELLED", Db.text(db.one("SELECT status FROM notification_deliveries WHERE notification_id=? AND device_id=?",
            notice, device), "status"));
        verify(push).send(CURRENT_DEVICE, notice);
    }

    @Test void abandonedClaimBecomesUncertainAndIsNeverReplayed() throws Exception {
        var account = registerAccount();
        registerDevice(account, CURRENT_DEVICE);
        String notice = emit(account, "abandoned-claim");
        db.run("UPDATE notifications SET delivery_status='PUSH_QUEUED' WHERE id=?", notice);
        db.insert("notification_deliveries", Db.fields("notification_id", notice, "device_id", registrationId(CURRENT_DEVICE),
            "status", "SENDING", "attempt_count", 1, "last_attempt_at", Instant.now().minus(11, ChronoUnit.MINUTES)));

        notifications.deliver();
        notifications.deliver();

        var receipt = receipt(notice, CURRENT_DEVICE);
        assertEquals("UNCERTAIN", Db.text(receipt, "status"));
        assertEquals("ABANDONED_CLAIM", Db.text(receipt, "last_error"));
        assertEquals(1, Db.num(receipt, "attempt_count"));
        verify(push, never()).send(anyString(), anyString());
    }

    @Test void eachWorkerTickCapsDispatchesAndNeverReplaysSuccessfulReceipts() throws Exception {
        var account = registerAccount();
        for (int device = 0; device < 23; device++) notifications.register(account.id(), "fixture:budget-" + device, null);
        String notice = emit(account, "dispatch-budget");
        when(push.send(anyString(), eq(notice))).thenReturn(accepted("fixture-accepted"));

        notifications.deliver();
        verify(push, times(20)).send(anyString(), eq(notice));
        assertEquals(23, receipts("dispatch-budget").size());
        assertEquals("PUSH_QUEUED", state("dispatch-budget"));

        notifications.deliver();
        notifications.deliver();
        verify(push, times(23)).send(anyString(), eq(notice));
        assertTrue(receipts("dispatch-budget").stream().allMatch(row -> "SENT".equals(Db.text(row, "status"))
            && Db.num(row, "attempt_count") == 1));
        assertEquals("SENT", state("dispatch-budget"));
    }

    private Account registerAccount() throws Exception {
        var response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
            Map.of("name", "Push fixture", "email", UUID.randomUUID() + "@example.test", "password", "A-long-test-password!"))))
            .andExpect(status().isCreated()).andReturn();
        var session = json.readTree(response.getResponse().getContentAsString());
        return new Account(session.path("user").path("id").asText(), session.path("token").asText());
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Account account, Map<String, ?> fields) throws Exception {
        return request.header("Authorization", "Bearer " + account.accessToken()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(fields));
    }

    private void registerDevice(Account account, String token) throws Exception {
        mvc.perform(body(post(DEVICES), account, Map.of("token", token))).andExpect(status().isNoContent());
    }

    private void setPreferences(Account account, boolean enabled) throws Exception {
        mvc.perform(body(put("/api/me/preferences"), account, Map.of("notificationsEnabled", enabled,
            "priceIncreaseAlerts", false, "replenishmentAlerts", true))).andExpect(status().isOk());
    }

    private long registrations(Account account, String token) {
        return db.count("SELECT COUNT(*) FROM device_tokens WHERE user_id=? AND token_hash=?", account.id(), AuthService.hash(token));
    }

    private String state(String key) {
        return Db.text(db.one("SELECT delivery_status FROM notifications WHERE dedupe_key=?", key), "delivery_status");
    }

    private String emit(Account account, String key) {
        assertTrue(notifications.emit(account.id(), "Test notice", "Private in-app text", "TARGET", key, false));
        return Db.text(db.one("SELECT id FROM notifications WHERE dedupe_key=?", key), "id");
    }

    private java.util.List<Map<String, Object>> receipts(String key) {
        return db.rows("SELECT d.* FROM notification_deliveries d JOIN notifications n ON n.id=d.notification_id WHERE n.dedupe_key=?", key);
    }

    private String registrationId(String token) {
        return Db.text(db.one("SELECT id FROM device_tokens WHERE token_hash=?", AuthService.hash(token)), "id");
    }

    private Map<String, Object> receipt(String notification, String token) {
        return db.one("SELECT * FROM notification_deliveries WHERE notification_id=? AND device_id=?", notification, registrationId(token));
    }

    private void makeRetryDue(String notice) {
        var earlier = Instant.now().minusSeconds(1);
        db.run("UPDATE notification_deliveries SET next_attempt_at=?,provider_retry_at=? WHERE notification_id=? AND status='RETRY'",
            earlier, earlier, notice);
    }

    private static FirebasePush.Result accepted(String message) {
        return new FirebasePush.Result(FirebasePush.Delivery.SENT, message, null);
    }

    private static FirebasePush.Result failed(FirebasePush.Delivery delivery, String code) {
        return new FirebasePush.Result(delivery, null, code);
    }
}
