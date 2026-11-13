package com.saritha.fashions.modules.receiptmanagement.service;

import com.saritha.fashions.modules.receiptmanagement.payload.ReceiptResponse;
import com.saritha.fashions.payload.PaginatedResponse;

/**
 * Reading receipts.
 */
public interface ReceiptManagementReadService {

    /**
     * Receipts, newest first. Shop owners see their own shop; the platform owner sees all
     * shops, or one shop when shopId is given.
     */
    PaginatedResponse<ReceiptResponse> getReceipts(String shopId, int pageNumber, int pageSize);

    ReceiptResponse getReceipt(String receiptNo);

    ReceiptPdf getReceiptPdf(String receiptNo);

    record ReceiptPdf(String fileName, byte[] content) {
    }
}
