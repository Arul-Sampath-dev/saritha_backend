package com.saritha.fashions.modules.receipt.service;

import com.saritha.fashions.util.AmountUtils;
import org.bson.Document;

import java.util.Date;
import java.util.List;

/**
 * Typed, tolerant view of a raw invoice document (old invoices are not always consistently
 * typed: numbers may be int/long/double, lists may be null).
 */
record InvoiceView(Document raw) {

    static final double EPSILON = 0.005;

    Object dbId() { return raw.get("_id"); }
    String dbIdString() { return raw.get("_id").toString(); }
    String invoiceId() { return string("invoiceId"); }
    String shopId() { return string("shopId"); }
    String receiptId() { return string("receiptId"); }
    String orderStatus() { return string("orderStatus"); }

    double totalAmount() { return number("totalAmount"); }
    double discount() { return number("discount"); }
    double taxableAmount() { return number("taxableAmount"); }
    double cgstRate() { return number("cgstRate"); }
    double sgstRate() { return number("sgstRate"); }
    double igstRate() { return number("igstRate"); }
    double cgstAmount() { return number("cgstAmount"); }
    double sgstAmount() { return number("sgstAmount"); }
    double igstAmount() { return number("igstAmount"); }
    double totalAmountWithGST() { return number("totalAmountWithGST"); }
    double advancePaid() { return number("advancePaid"); }

    /** Recomputed (not the stored balanceDue, which the old update-payment could get wrong). */
    double balanceDue() {
        return Math.max(0.0, AmountUtils.round(totalAmountWithGST() - advancePaid()));
    }

    /** Raw stored values, used as compare-and-set guards (exact same value and type). */
    Object rawAdvancePaid() { return raw.get("advancePaid"); }
    Object rawTotalAmountWithGST() { return raw.get("totalAmountWithGST"); }
    Object rawDiscount() { return raw.get("discount"); }

    boolean isListField(String field) { return raw.get(field) instanceof List<?>; }
    boolean hasField(String field) { return raw.containsKey(field); }

    @SuppressWarnings("unchecked")
    List<Document> items() {
        return raw.get("items") instanceof List<?> list ? (List<Document>) list : List.of();
    }

    List<?> list(String field) {
        return raw.get(field) instanceof List<?> list ? list : List.of();
    }

    Date date(String field) {
        return raw.get(field) instanceof Date date ? date : null;
    }

    String string(String field) {
        Object value = raw.get(field);
        return value == null ? null : value.toString();
    }

    double number(String field) {
        return raw.get(field) instanceof Number number ? number.doubleValue() : 0.0;
    }

    static String paymentStatus(double advancePaid, double balanceDue) {
        if (advancePaid <= 0) {
            return "Dues";
        }
        return balanceDue > EPSILON ? "Partial" : "Paid";
    }
}
