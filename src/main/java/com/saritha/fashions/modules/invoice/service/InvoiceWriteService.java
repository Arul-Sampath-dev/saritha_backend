package com.saritha.fashions.modules.invoice.service;

import com.saritha.fashions.modules.invoice.payload.DeleteInvoiceResponse;
import com.saritha.fashions.modules.invoice.payload.InvoiceResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Write side of invoice management (SHOP_OWNER only).
 */
public interface InvoiceWriteService {

    /**
     * Creates an invoice for the logged-in shop owner's shop.
     *
     * @param invoiceJson the CreateInvoiceRequest as JSON (multipart text part)
     * @param images      optional item images named item_{index}_{name}.{ext}
     */
    InvoiceResponse createInvoice(String invoiceJson, List<MultipartFile> images);

    /**
     * Edits an invoice of the owner's shop that has no receipt yet.
     *
     * @param editJson the EditInvoiceRequest as JSON
     * @param images   optional item images named item_{index}_{name}.{ext}, index = position in items
     * @throws com.saritha.fashions.modules.invoice.exception.InvoiceConflictException
     *         if the invoice changed while it was being edited
     */
    InvoiceResponse editInvoice(String invoiceId, String editJson, List<MultipartFile> images);

    /**
     * Deletes (archives) an invoice of the owner's shop that has no receipt yet.
     *
     * @param reason optional reason stored with the archived invoice
     */
    DeleteInvoiceResponse deleteInvoice(String invoiceId, String reason);
}
