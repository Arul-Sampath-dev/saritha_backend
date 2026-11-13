package com.saritha.fashions.modules.authentication.repository;

/**
 * Atomic operations on password-reset codes.
 */
public interface PasswordResetOtpRepositoryCustom {

    /**
     * Atomically counts one wrong guess.
     *
     * @return the new number of failed attempts, or -1 if the code no longer exists
     */
    int registerFailedAttempt(String otpId);

    /**
     * Atomically deletes the code. Only one caller can succeed, so a code can be used once.
     *
     * @return true if this call removed it
     */
    boolean consume(String otpId);
}
