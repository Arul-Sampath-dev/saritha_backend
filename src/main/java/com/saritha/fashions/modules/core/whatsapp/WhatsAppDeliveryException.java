package com.saritha.fashions.modules.core.whatsapp;

/**
 * Thrown when a WhatsApp message could not be handed to Twilio.
 */
public class WhatsAppDeliveryException extends RuntimeException {

    public WhatsAppDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public WhatsAppDeliveryException(String message) {
        super(message);
    }
}
