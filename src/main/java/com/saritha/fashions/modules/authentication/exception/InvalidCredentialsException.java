package com.saritha.fashions.modules.authentication.exception;

/**
 * Wrong email or password, or a missing / revoked token. Returned as 401.
 * The message never says WHICH part was wrong, so emails cannot be probed.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
