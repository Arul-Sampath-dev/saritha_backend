package com.saritha.fashions.modules.authentication.service;

import com.saritha.fashions.modules.authentication.payload.ResetPasswordRequest;

/**
 * Password reset with an emailed one-time code.
 */
public interface PasswordResetService {

    /**
     * Emails a reset code if the account exists. Always behaves the same whether or not the
     * email has an account, so it cannot be used to discover accounts.
     */
    void requestResetCode(String email);

    /** Sets the new password if the code is valid, and signs the user out everywhere. */
    void resetPassword(ResetPasswordRequest request);
}
