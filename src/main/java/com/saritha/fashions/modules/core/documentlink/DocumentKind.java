package com.saritha.fashions.modules.core.documentlink;

/**
 * Documents a customer can download through a public link.
 *
 * The label is encrypted into the token; it must stay "invoice" / "receipt" so links already
 * sent to customers (and tokens checked by the legacy download endpoints) keep working.
 */
public enum DocumentKind {
    INVOICE("invoice"),
    RECEIPT("receipt"),
    PAYMENT_RECEIPT("payment-receipt");

    private final String label;

    DocumentKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
