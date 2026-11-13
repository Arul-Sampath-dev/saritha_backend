package com.saritha.fashions.modules.itemmanagement.exception;

/**
 * The item changed between reading and updating it (another user moved or re-assigned
 * it). Returned as 409 Conflict; the client should reload the item and try again.
 */
public class ItemConflictException extends RuntimeException {

    public ItemConflictException(String message) {
        super(message);
    }
}
