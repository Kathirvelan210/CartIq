package com.smartcart.service;

import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.*;

@Service
@Profile("!mysql-local")
public class RecommendationFeedback {
    public enum Category { PLAN, BASKET, BULK_BUY, PERSONALIZED, ASSISTANT }
    public enum Outcome { ACCEPTED, REJECTED, MODIFIED, NOT_RELEVANT }
    public record Rating(@jakarta.validation.constraints.NotNull Category category,@jakarta.validation.constraints.NotNull Outcome outcome) {}
    public record Aggregate(Category category,Outcome outcome,long count) {}
    public record Analytics(int days,int minimumDistinctAccounts,List<Aggregate> results) {}

    private final Db db;
    private final CartService carts;

    public RecommendationFeedback(Db db,CartService carts){this.db=db;this.carts=carts;}

    @Transactional
    public Rating record(String owner,UUID reference,Rating rating) {
        carts.lockOwner(owner);
        String id=reference.toString();
        Instant now=Instant.now();
        var prior=db.maybe("SELECT category FROM recommendation_feedback WHERE user_id=? AND reference_id=?",owner,id);
        if(prior.isPresent()) {
            if(!rating.category().name().equals(text(prior.get(),"category")))throw ApiException.bad("Feedback category cannot be changed.");
            db.run("UPDATE recommendation_feedback SET outcome=?,updated_at=? WHERE user_id=? AND reference_id=?",rating.outcome().name(),now,owner,id);
        } else {
            if(db.count("SELECT COUNT(*) FROM recommendation_feedback WHERE user_id=? AND updated_at>?",owner,now.minus(1,ChronoUnit.DAYS))>=30)
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Daily feedback limit reached.");
            db.insert("recommendation_feedback",fields("user_id",owner,"reference_id",id,"category",rating.category().name(),
                "outcome",rating.outcome().name(),"updated_at",now));
        }
        return rating;
    }

    @Transactional
    public void clear(String owner) {
        carts.lockOwner(owner);
        db.run("DELETE FROM recommendation_feedback WHERE user_id=?",owner);
    }

    public Analytics aggregate() {
        var after=Instant.now().minus(90,ChronoUnit.DAYS);
        var aggregates=db.rows("""
            SELECT category,outcome,COUNT(*) AS total FROM recommendation_feedback WHERE updated_at>=?
            GROUP BY category,outcome HAVING COUNT(DISTINCT user_id)>=5 ORDER BY category,outcome
            """,after).stream().map(r->new Aggregate(Category.valueOf(text(r,"category")),Outcome.valueOf(text(r,"outcome")),num(r,"total"))).toList();
        return new Analytics(90,5,aggregates);
    }
}
