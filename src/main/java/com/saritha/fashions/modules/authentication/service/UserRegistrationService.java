package com.saritha.fashions.modules.authentication.service;

import com.saritha.fashions.modules.authentication.payload.RegisterUserRequest;
import com.saritha.fashions.modules.authentication.payload.RegisteredUserResponse;

/**
 * Creating user accounts.
 */
public interface UserRegistrationService {

    /** Creates an account (platform owner only) and links it to its shop. */
    RegisteredUserResponse register(RegisterUserRequest request);

    /**
     * True if an account already uses this email (compared the same way as on registration).
     * Lets callers fail early; register() still rejects a duplicate that appears meanwhile.
     */
    boolean isEmailRegistered(String email);
}
