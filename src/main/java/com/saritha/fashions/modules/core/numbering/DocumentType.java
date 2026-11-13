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

    INVOICE("invoice_sequence", List.of(
            new UsedNumberSource("invoice", "invoiceId"),
            // Numbers of deleted invoices are never handed out again (the legacy delete moved the
            // counter back, which could re-issue a number already sent to a customer).
            new UsedNumberSource("deleted_invoices", "invoice.invoiceId"))),

    RECEIPT("receipt_sequence", List.of(
            new UsedNumberSource("receipt", "receiptNo")));

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
