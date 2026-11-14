package com.saritha.fashions.modules.receipt.exception;

/**
 * The invoice changed between reading and updating it (e.g. two payments recorded at the
 * same moment). Returned as 409 Conflict; the client should reload and try again.
 */
public class ReceiptConflictException extends RuntimeException {

    public ReceiptConflictException(String message) {
        super(message);
    }
}
