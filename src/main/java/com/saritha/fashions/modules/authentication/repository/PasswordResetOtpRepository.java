package com.saritha.fashions.modules.authentication.repository;

import com.saritha.fashions.modules.authentication.model.PasswordResetOtp;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Pending password-reset codes ("password_reset_otps" collection), at most one per user.
 */
@Repository
public interface PasswordResetOtpRepository
        extends MongoRepository<PasswordResetOtp, String>, PasswordResetOtpRepositoryCustom {

    Optional<PasswordResetOtp> findByUserId(String userId);

    void deleteByUserId(String userId);
}
