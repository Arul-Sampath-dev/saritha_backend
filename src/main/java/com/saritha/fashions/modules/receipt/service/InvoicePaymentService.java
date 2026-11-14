package com.saritha.fashions.modules.receipt.service;

import com.saritha.fashions.modules.receipt.payload.ApplyDiscountRequest;
import com.saritha.fashions.modules.receipt.payload.InvoiceAccountResponse;
import com.saritha.fashions.modules.receipt.payload.RecordPaymentRequest;

/**
 * Payments and discounts on an invoice, before the receipt is generated.
 */
public interface InvoicePaymentService {

    /** Totals, payments and balance of an invoice of the shop owner's shop. */
    InvoiceAccountResponse getAccount(String invoiceId);

    /** Records a payment; it may not exceed the balance due. */
    InvoiceAccountResponse recordPayment(String invoiceId, RecordPaymentRequest request);

    /** Gives a one-time discount, recalculates GST and issues a credit note. */
    InvoiceAccountResponse applyDiscount(String invoiceId, ApplyDiscountRequest request);
}
