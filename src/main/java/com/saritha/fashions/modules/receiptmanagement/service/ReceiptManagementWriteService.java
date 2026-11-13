package com.saritha.fashions.modules.receiptmanagement.service;

import com.saritha.fashions.modules.receiptmanagement.payload.ReceiptResponse;

/**
 * Generating receipts (which also marks the order delivered).
 */
public interface ReceiptManagementWriteService {

    /**
     * Generates the receipt of a fully paid, completed invoice and marks it delivered.
     * Idempotent: if the invoice already has a receipt, that receipt is returned (created = false).
     */
    ReceiptResponse generateReceipt(String invoiceId);
}
