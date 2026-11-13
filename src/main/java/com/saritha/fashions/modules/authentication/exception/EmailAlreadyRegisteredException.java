package com.saritha.fashions.modules.authentication.exception;

/**
 * An account with this email already exists. Returned as 409 Conflict.
 * (Only the platform owner can register users, so this does not leak emails publicly.)
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("An account with email " + email + " already exists");
    }
}
