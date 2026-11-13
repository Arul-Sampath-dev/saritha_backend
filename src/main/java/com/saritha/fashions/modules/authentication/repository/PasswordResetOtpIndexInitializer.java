package com.saritha.fashions.modules.authentication.repository;

import com.saritha.fashions.modules.authentication.model.PasswordResetOtp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Creates the indexes of "password_reset_otps" at startup (index auto-creation is off in
 * this project):
 *  - userId unique   → at most one pending code per user
 *  - expiresAt TTL   → MongoDB deletes expired codes automatically
 * Creating an index that already exists is a no-op. Failures are logged, not fatal
 * (the service also rejects and deletes expired codes itself).
 */
@Component
public class PasswordResetOtpIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetOtpIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public PasswordResetOtpIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        try {
            var indexOps = mongoTemplate.indexOps(PasswordResetOtp.class);
            indexOps.ensureIndex(new Index().on("userId", Sort.Direction.ASC).unique().named("userId_unique"));
            indexOps.ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC)
                    .expire(Duration.ZERO).named("expiresAt_ttl"));
        } catch (Exception exception) {
            log.warn("Could not create password_reset_otps indexes: {}", exception.getMessage());
        }
    }
}
