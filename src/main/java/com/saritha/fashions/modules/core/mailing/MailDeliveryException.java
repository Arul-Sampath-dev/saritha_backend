package com.saritha.fashions.modules.core.mailing;

/**
 * Thrown when an email could not be handed to the mail provider.
 */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
