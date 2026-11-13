package com.saritha.fashions.modules.authentication.repository;

import com.saritha.fashions.modules.authentication.model.PasswordResetOtp;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * MongoTemplate-based implementation of PasswordResetOtpRepositoryCustom.
 */
public class PasswordResetOtpRepositoryCustomImpl implements PasswordResetOtpRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public PasswordResetOtpRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public int registerFailedAttempt(String otpId) {
        PasswordResetOtp updated = mongoTemplate.findAndModify(
                new Query(Criteria.where("id").is(otpId)),
                new Update().inc("failedAttempts", 1),
                FindAndModifyOptions.options().returnNew(true),
                PasswordResetOtp.class);
        return updated == null ? -1 : updated.getFailedAttempts();
    }

    @Override
    public boolean consume(String otpId) {
        return mongoTemplate.findAndRemove(new Query(Criteria.where("id").is(otpId)), PasswordResetOtp.class) != null;
    }
}
