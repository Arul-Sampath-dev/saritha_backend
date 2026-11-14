package com.saritha.fashions.modules.invoice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Invoice entity used by the Invoice module.
 *
 * Maps to the same "invoice" collection as com.saritha.fashions.model.Invoice and uses
 * the same field names, so invoices created here are read by the existing screens,
 * PDF generation, receipts and tailor assignment without any change. Keep the fields
 * in sync with the original Invoice model.
 */
@Document(collection = "invoice")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
public class InvoiceRecord {

    @Id
    private String id;

    /** Human-readable, unique invoice number, e.g. "SARI-12-11-2026-00042". */
    private String invoiceId;

    @DBRef
    private InvoiceCustomer customer;
    private String customerState;
    private String shopId;

    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    private String transactionId;

    // Amounts (all rounded to 2 decimals)
    private double totalAmount;
    private double discount;
    private double taxableAmount;
    private double cgstRate;
    private double sgstRate;
    private double igstRate;
    private double cgstAmount;
    private double sgstAmount;
    private double igstAmount;
    private double totalAmountWithGST;
    private double advancePaid;
    private double balanceDue;

    private String gstin;
    private String sacCode;
    private String hsnCode;

    private String paymentMethod;
    /** "Dues", "Partial" or "Paid". */
    private String paymentStatus;
    private String orderStatus;

    @Builder.Default
    private List<InvoicePayment> paymentHistories = new ArrayList<>();

    private Boolean isBackDate;
    private String backDate;
    private String expectedDeliveryDate;
    private Date orderDate;
    private Date invoiceDate;

    private String notes;

    /** Set by receipt generation; read-only here (null = invoice can still be edited / deleted). */
    private String receiptId;

    /** Kept for compatibility with tailor assignment, which fills these later. */
    @Builder.Default
    private List<InvoiceAssignmentEntry> assignmentHistory = new ArrayList<>();
}
