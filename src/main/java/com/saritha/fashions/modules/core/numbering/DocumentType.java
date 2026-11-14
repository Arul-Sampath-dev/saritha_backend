package com.saritha.fashions.modules.core.numbering;

import java.util.List;

/**
 * Documents that get a running number, with where their counter and their existing numbers live.
 *
 * The counter collections and documents are the ones the legacy InvoiceSequenceService /
 * ReceiptSequenceService use ({_id: "{year}-{shopId}", year, shopId, sequence}), so legacy and
 * module code keep sharing one counter per shop and year while both are in use.
 */
public enum DocumentType {

    // Only live invoices count as "used": when the latest invoice is deleted its number is released
    // and re-used by the next invoice, so the bill numbers stay in sequence (client requirement,
    // same behaviour as the legacy delete).
    INVOICE("invoice_sequence", List.of(
            new UsedNumberSource("invoice", "invoiceId"))),

    RECEIPT("receipt_sequence", List.of(
            new UsedNumberSource("receipt", "receiptNo"))),

    /** One per recorded payment ({SHOP}-PR-{dd-MM-yyyy}-{00001}); its own counter per shop and year. */
    PAYMENT_RECEIPT("payment_receipt_sequence", List.of(
            new UsedNumberSource("payment_receipts", "paymentReceiptNo")));

    private final String counterCollection;
    private final List<UsedNumberSource> usedNumberSources;

    DocumentType(String counterCollection, List<UsedNumberSource> usedNumberSources) {
        this.counterCollection = counterCollection;
        this.usedNumberSources = usedNumberSources;
    }

    String counterCollection() {
        return counterCollection;
    }

    List<UsedNumberSource> usedNumberSources() {
        return usedNumberSources;
    }

    /** A collection field that holds numbers already issued. */
    record UsedNumberSource(String collection, String field) {
    }
}
