package com.saritha.fashions.modules.receipt.service;

import com.saritha.fashions.modules.receipt.payload.ReceiptResponse;

/**
 * Generating receipts (which also marks the order delivered).
 */
public interface ReceiptWriteService {

    /**
     * Generates the receipt of a fully paid, completed invoice and marks it delivered.
     * Idempotent: if the invoice already has a receipt, that receipt is returned (created = false).
     */
    ReceiptResponse generateReceipt(String invoiceId);
}
