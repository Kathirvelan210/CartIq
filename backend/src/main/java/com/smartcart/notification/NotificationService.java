package com.smartcart.notification;

import com.smartcart.dto.Models.*;
import com.smartcart.repository.Db;
import com.smartcart.service.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import com.smartcart.exception.ApiException;
import org.springframework.http.HttpStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.smartcart.repository.Db.*;

@Service
public class NotificationService {
    private final Db db;
    private final FirebasePush push;
    private final NotificationDeliveryQueue deliveryQueue;
    public NotificationService(Db db, FirebasePush push, NotificationDeliveryQueue deliveryQueue){this.db=db;this.push=push;this.deliveryQueue=deliveryQueue;}
    public List<Notice> list(String user){return db.rows("SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 100",user).stream().map(r->new Notice(text(r,"id"),text(r,"title"),text(r,"body"),text(r,"kind"),bool(r,"sample"),r.get("read_at")!=null,time(r,"created_at"),deliveryStatus(text(r,"delivery_status")))).toList();}
    private static String deliveryStatus(String status) { return "PUSH_QUEUED".equals(status) ? "PUSH_PENDING" : status; }
    public void read(String user,String id){db.one("SELECT id FROM notifications WHERE id=? AND user_id=?",id,user);db.run("UPDATE notifications SET read_at=?,updated_at=? WHERE id=? AND user_id=?",Instant.now(),Instant.now(),id,user);}
    public NotificationSettings settings(String user){var r=db.one("SELECT * FROM user_preferences WHERE user_id=?",user);return new NotificationSettings(bool(r,"notifications_enabled"),bool(r,"price_increase_alerts"),bool(r,"replenishment_alerts"),text(r,"preferred_brands"));}
    @Transactional public void settings(String user,NotificationSettings p){db.run("UPDATE user_preferences SET notifications_enabled=?,price_increase_alerts=?,replenishment_alerts=?,preferred_brands=?,updated_at=? WHERE user_id=?",p.notificationsEnabled(),p.priceIncreaseAlerts(),p.replenishmentAlerts(),p.preferredBrands()==null?"":p.preferredBrands(),Instant.now(),user);
        if (!p.notificationsEnabled()) deliveryQueue.cancelForUser(user, Instant.now());
    }
    public boolean emit(String user,String title,String body,String kind,String key,boolean sample){if(!settings(user).notificationsEnabled())return false;try{db.insert("notifications",fields("id",id(),"user_id",user,"title",title,"body",body,"kind",kind,"dedupe_key",key,"sample",sample,"delivery_status",push.enabled()&&!sample?"PUSH_PENDING":"IN_APP"));return true;}catch(DuplicateKeyException e){return false;}}

    @Transactional public void register(String user, String token, String previousToken) {
        if (!push.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Push registration is unavailable.");
        if (!settings(user).notificationsEnabled()) throw ApiException.bad("Enable notifications in your account preferences first.");
        String hash = AuthService.hash(token);
        try {
            db.insert("device_tokens", fields("id", id(), "user_id", user, "token", token, "token_hash", hash));
        } catch (DuplicateKeyException existing) {
            db.run("UPDATE device_tokens SET user_id=?,updated_at=? WHERE token_hash=?", user, Instant.now(), hash);
        }
        if (previousToken != null && !previousToken.equals(token)) unregister(user, previousToken);
    }

    public void unregister(String user, String token) {
        db.run("DELETE FROM device_tokens WHERE user_id=? AND token_hash=?", user, AuthService.hash(token));
    }
    public void unregister(String user){db.run("DELETE FROM device_tokens WHERE user_id=?",user);}

    @Scheduled(fixedDelay=86400000) void expireTokens() {
        if (push.enabled()) db.run("DELETE FROM device_tokens WHERE updated_at<?", Instant.now().minus(Duration.ofDays(60)));
    }

    @Scheduled(fixedDelay=60000) public synchronized void deliver(){
        if (push.enabled()) deliveryQueue.deliver(push);
    }
}
