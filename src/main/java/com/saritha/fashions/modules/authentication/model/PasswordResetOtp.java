package com.saritha.fashions.modules.authentication.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * A pending password-reset code. At most one per user (a new request replaces the old one).
 *
 * Differences from the old ForgetPassword document:
 * - the code itself is never stored, only a BCrypt hash of it;
 * - wrong guesses are counted, and the code is destroyed after MAX_ATTEMPTS;
 * - createdAt allows a cooldown between requests.
 *
 * Indexes (created at startup by PasswordResetOtpIndexInitializer, since Spring Data index
 * auto-creation is off in this project): userId unique, and a TTL index on expiresAt so
 * MongoDB deletes expired codes. Expired codes are also rejected by the service either way.
 */
@Document(collection = "password_reset_otps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "otpHash")
public class PasswordResetOtp {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String otpHash;

    private int failedAttempts;

    private Date createdAt;

    @Indexed(expireAfter = "0s")
    private Date expiresAt;
}
