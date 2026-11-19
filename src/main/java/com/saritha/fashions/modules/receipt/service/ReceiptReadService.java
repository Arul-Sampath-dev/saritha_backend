package com.saritha.fashions.modules.receipt.service;

import com.saritha.fashions.modules.receipt.payload.ReceiptResponse;
import com.saritha.fashions.payload.PaginatedResponse;

/**
 * Reading receipts.
 */
public interface ReceiptReadService {

    /**
     * Receipts, newest first. Shop owners see their own shop; the platform owner sees all
     * shops, or one shop when shopId is given.
     */
    default PaginatedResponse<ReceiptResponse> getReceipts(String shopId, int pageNumber, int pageSize) {
        return getReceipts(shopId, null, null, null, pageNumber, pageSize);
    }

    /**
     * The same, filtered by receipt date (inclusive IST days, either side optional) and search text
     * (receipt number, invoice number or customer name / phone).
     */
    PaginatedResponse<ReceiptResponse> getReceipts(String shopId, java.time.LocalDate from, java.time.LocalDate to,
                                                   String search, int pageNumber, int pageSize);

    ReceiptResponse getReceipt(String receiptNo);

    ReceiptPdf getReceiptPdf(String receiptNo);

    record ReceiptPdf(String fileName, byte[] content) {
    }
}
