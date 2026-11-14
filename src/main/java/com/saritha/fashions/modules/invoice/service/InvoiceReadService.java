package com.saritha.fashions.modules.invoice.service;

import com.saritha.fashions.modules.invoice.payload.InvoiceResponse;
import com.saritha.fashions.payload.PaginatedResponse;

import java.time.LocalDate;

/**
 * Read side of invoice management.
 *
 * Shop scope: a SHOP_OWNER always reads their own shop (shopId is ignored); the platform
 * OWNER must pass shopId.
 */
public interface InvoiceReadService {

    /**
     * One page of the shop's invoices, newest first by default.
     *
     * @param status        pending (not delivered), delivered, or null/all
     * @param paymentStatus Dues, Partial or Paid (null = any)
     * @param search        text in the invoice number, customer name or phone
     * @param special       only invoices with a special-category item
     * @param details       false → InvoiceSummaryResponse rows, true → full InvoiceResponse rows
     * @param sort          newest (default) or oldest
     */
    PaginatedResponse<?> listInvoices(String shopId, LocalDate from, LocalDate to, String status,
                                      String paymentStatus, String search, boolean special, boolean details,
                                      String sort, int pageNumber, int pageSize);

    /** The full invoice. */
    InvoiceResponse getInvoice(String invoiceId, String shopId);

    /** Generates the PDF of an invoice. */
    InvoicePdf getInvoicePdf(String invoiceId, String shopId);

    /**
     * @param fileName suggested download file name, e.g. "SARI-12-11-2026-00042.pdf"
     */
    record InvoicePdf(String fileName, byte[] content) {
    }
}
