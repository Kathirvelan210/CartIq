package com.smartcart.notification;

import com.smartcart.repository.Db;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.smartcart.repository.Db.*;

@Component
class NotificationDeliveryQueue {
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration NOTICE_TTL = Duration.ofDays(2);
    private static final Duration TOKEN_TTL = Duration.ofDays(60);
    private static final Duration CLAIM_TTL = Duration.ofMinutes(10);
    private final Db db;
    private final TransactionTemplate transactions;

    NotificationDeliveryQueue(Db db, PlatformTransactionManager transactionManager) {
        this.db = db;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    void cancelForUser(String user, Instant now) {
        db.run("UPDATE notifications SET delivery_status='IN_APP',updated_at=? WHERE user_id=? AND delivery_status='PUSH_PENDING'", now, user);
        db.run("""
            UPDATE notification_deliveries SET status='CANCELLED',last_error='OPTED_OUT',next_attempt_at=NULL,updated_at=?
            WHERE status IN ('PENDING','RETRY') AND notification_id IN (
                SELECT id FROM notifications WHERE user_id=?
            )
            """, now, user);
    }

    void deliver(FirebasePush push) {
        // A timestamp stored with second precision must be due at the same query boundary.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant cutoff = now.minus(NOTICE_TTL);
        db.run("UPDATE notifications SET delivery_status='IN_APP',updated_at=? WHERE delivery_status='PUSH_PENDING' AND created_at<?", now, cutoff);
        db.run("""
            UPDATE notification_deliveries SET status='UNCERTAIN',last_error='ABANDONED_CLAIM',updated_at=?
            WHERE status='SENDING' AND last_attempt_at<?
            """, now, now.minus(CLAIM_TTL));
        db.run("""
            UPDATE notification_deliveries SET status='CANCELLED',last_error='EXPIRED_NOTIFICATION',next_attempt_at=NULL,updated_at=?
            WHERE status IN ('PENDING','RETRY') AND notification_id IN (
                SELECT id FROM notifications WHERE created_at<?
            )
            """, now, cutoff);

        for (var notice : db.rows("SELECT id FROM notifications WHERE delivery_status='PUSH_PENDING' AND sample=FALSE ORDER BY created_at,id LIMIT 20")) {
            stage(text(notice,"id"), now);
        }
        if (db.count("SELECT COUNT(*) FROM notification_deliveries WHERE provider_retry_at>?", now) == 0) {
            for (var receipt : db.rows("""
                SELECT d.notification_id,d.device_id,d.attempt_count,n.created_at AS notice_created_at
                FROM notification_deliveries d JOIN notifications n ON n.id=d.notification_id
                WHERE d.status IN ('PENDING','RETRY') AND d.next_attempt_at<=?
                ORDER BY d.next_attempt_at,d.notification_id,d.device_id LIMIT 20
                """, now)) {
                if (dispatch(push, text(receipt,"notification_id"), text(receipt,"device_id"),
                    (int)num(receipt,"attempt_count"), time(receipt,"notice_created_at"))) break;
            }
        }
        db.run("""
            UPDATE notifications SET delivery_status=CASE WHEN EXISTS (
                SELECT 1 FROM notification_deliveries d WHERE d.notification_id=notifications.id AND d.status='SENT'
            ) THEN 'SENT' ELSE 'IN_APP' END,updated_at=?
            WHERE delivery_status='PUSH_QUEUED' AND NOT EXISTS (
                SELECT 1 FROM notification_deliveries d WHERE d.notification_id=notifications.id
                AND d.status IN ('PENDING','RETRY','SENDING')
            )
            """, Instant.now());
    }

    private void stage(String noticeId, Instant now) {
        transactions.executeWithoutResult(transaction -> {
            if (db.run("""
                UPDATE notifications SET delivery_status='PUSH_QUEUED',updated_at=?
                WHERE id=? AND delivery_status='PUSH_PENDING' AND sample=FALSE
                """, now, noticeId) == 0) return;
            db.run("""
                INSERT INTO notification_deliveries (notification_id,device_id,status,next_attempt_at)
                SELECT n.id,t.id,'PENDING',? FROM notifications n
                JOIN user_preferences p ON p.user_id=n.user_id
                JOIN device_tokens t ON t.user_id=n.user_id
                WHERE n.id=? AND p.notifications_enabled=TRUE AND t.updated_at>=?
                """, now, noticeId, now.minus(TOKEN_TTL));
        });
    }

    private boolean dispatch(FirebasePush push, String noticeId, String deviceId, int attempts, Instant noticeCreatedAt) {
        Instant now = Instant.now();
        if (db.run("""
            UPDATE notification_deliveries SET status='SENDING',attempt_count=attempt_count+1,
                next_attempt_at=NULL,last_attempt_at=?,updated_at=?
            WHERE notification_id=? AND device_id=? AND status IN ('PENDING','RETRY')
                AND next_attempt_at<=? AND attempt_count=? AND attempt_count<?
            """, now, now, noticeId, deviceId, now, attempts, MAX_ATTEMPTS) == 0) return false;

        // The single-worker schedule has no surrounding transaction. Claim first, then recheck consent and
        // token ownership. A lost response or abandoned claim can never return to the dispatch queue.
        var recipient = db.maybe("""
            SELECT t.user_id,t.token FROM device_tokens t
            JOIN notifications n ON n.user_id=t.user_id AND n.id=?
            JOIN user_preferences p ON p.user_id=t.user_id
            WHERE t.id=? AND t.updated_at>=? AND p.notifications_enabled=TRUE AND n.delivery_status='PUSH_QUEUED'
            """, noticeId, deviceId, now.minus(TOKEN_TTL));
        if (recipient.isEmpty() || !noticeCreatedAt.plus(NOTICE_TTL).isAfter(now)) {
            finish(noticeId, deviceId, "CANCELLED", null, null, "INELIGIBLE_RECIPIENT", null);
            return false;
        }

        var result = push.send(text(recipient.get(),"token"), noticeId);
        switch (result.outcome()) {
            case SENT -> finish(noticeId, deviceId, "SENT", null, null, null, result.providerMessageId());
            case UNREGISTERED -> {
                db.run("DELETE FROM device_tokens WHERE id=? AND user_id=?", deviceId, text(recipient.get(),"user_id"));
                finish(noticeId, deviceId, "FAILED", null, null, result.errorCode(), null);
            }
            case PERMANENT -> finish(noticeId, deviceId, "FAILED", null, null, result.errorCode(), null);
            case UNCERTAIN -> finish(noticeId, deviceId, "UNCERTAIN", null, null, result.errorCode(), null);
            case DEFERRED -> {
                Instant received = Instant.now();
                Instant providerRetry = later(received.plusSeconds(60), result.retryAfter());
                Instant retry = later(received.plusSeconds(attempts == 0 ? 60 : 300), providerRetry);
                boolean eligible = attempts + 1 < MAX_ATTEMPTS && retry.isBefore(noticeCreatedAt.plus(NOTICE_TTL));
                Instant throttle = providerRetry.isAfter(received.plus(NOTICE_TTL)) ? received.plus(NOTICE_TTL) : providerRetry;
                finish(noticeId, deviceId, eligible ? "RETRY" : "FAILED", eligible ? retry : null, throttle, result.errorCode(), null);
                return true;
            }
        }
        return false;
    }

    private static Instant later(Instant fallback, Instant candidate) {
        return candidate != null && candidate.isAfter(fallback) ? candidate : fallback;
    }

    private void finish(String noticeId, String deviceId, String status, Instant retry, Instant throttle, String error, String providerId) {
        db.run("""
            UPDATE notification_deliveries SET status=?,next_attempt_at=?,provider_retry_at=?,
                last_error=COALESCE(?,last_error),provider_message_id=?,updated_at=?
            WHERE notification_id=? AND device_id=? AND status='SENDING'
            """, status, retry, throttle, error, providerId, Instant.now(), noticeId, deviceId);
    }
}
