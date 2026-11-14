package com.saritha.fashions.modules.invoice.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validation errors for the create-invoice request, as field → message.
 *
 * The invoice JSON arrives as a multipart text part, so Spring's @Valid cannot be
 * used on it; the service validates it manually and throws this instead.
 */
public class InvoiceRequestValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public InvoiceRequestValidationException(Map<String, String> errors) {
        super("Invalid invoice request: " + errors);
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public static InvoiceRequestValidationException of(String field, String message) {
        return new InvoiceRequestValidationException(Map.of(field, message));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
