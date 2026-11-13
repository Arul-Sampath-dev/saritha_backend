package com.saritha.fashions.modules.authentication.exception;

/**
 * Too many wrong password-reset codes; the code has been destroyed. Returned as 429.
 */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
