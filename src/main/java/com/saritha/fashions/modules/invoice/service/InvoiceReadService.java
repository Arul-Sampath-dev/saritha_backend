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
    default PaginatedResponse<?> listInvoices(String shopId, LocalDate from, LocalDate to, String status,
                                              String paymentStatus, String search, boolean special, boolean details,
                                              String sort, int pageNumber, int pageSize) {
        return listInvoices(shopId, from, to, status, paymentStatus, search, special, details, sort, null, null,
                pageNumber, pageSize);
    }

    /**
     * @param stage      NEW, IN_PRODUCTION, READY or DELIVERED (see InvoiceStage), or null
     * @param itemStatus only invoices with at least one item in this status (e.g. CUTTING), or null
     * @param sort       newest (default), oldest, dueSoonest, dueLatest
     */
    PaginatedResponse<?> listInvoices(String shopId, LocalDate from, LocalDate to, String status,
                                      String paymentStatus, String search, boolean special, boolean details,
                                      String sort, String stage, String itemStatus, int pageNumber, int pageSize);

    /** The full invoice. */
    InvoiceResponse getInvoice(String invoiceId, String shopId);

    /**
     * Finds the customer with this 10-digit WhatsApp number (customers are shared by all branches, as at
     * invoice creation). Not found → found = false.
     */
    com.saritha.fashions.modules.invoice.payload.CustomerLookupResponse lookupCustomer(String phone);

    /** Generates the PDF of an invoice. */
    InvoicePdf getInvoicePdf(String invoiceId, String shopId);

    /**
     * @param fileName suggested download file name, e.g. "SARI-12-11-2026-00042.pdf"
     */
    record InvoicePdf(String fileName, byte[] content) {
    }
}
