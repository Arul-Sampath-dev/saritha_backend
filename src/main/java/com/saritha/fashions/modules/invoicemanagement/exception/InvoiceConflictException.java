package com.saritha.fashions.modules.invoicemanagement.exception;

/**
 * The invoice changed between reading and writing it (e.g. a payment, a receipt or a
 * production update happened during an edit or delete). Returned as 409 Conflict; the
 * client should reload the invoice and try again.
 */
public class InvoiceConflictException extends RuntimeException {

    public InvoiceConflictException(String message) {
        super(message);
    }
}
