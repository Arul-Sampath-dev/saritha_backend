package com.saritha.fashions.modules.receipt.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Receipt as read by the Receipt module.
 *
 * Maps to the same "receipt" collection as com.saritha.fashions.model.Receipt with the same
 * field names, so receipts created here work with the existing PDF generation and the public
 * customer download link. Receipts are WRITTEN by ReceiptRecordRepositoryCustomImpl as raw
 * documents (copying the invoice items exactly); this class is used for reading.
 *
 * The customer is a DBRef in the stored document; only its id is needed here, so the
 * customer field is read as the raw reference and resolved separately.
 */
@Document(collection = "receipt")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptRecord {

    @Id
    private String id;

    /** Human-readable, unique receipt number, e.g. "SARI-13-11-2026-00007". */
    private String receiptNo;

    /** Database id (_id) of the invoice — same as the old Receipt.invoiceId. */
    private String invoiceId;

    private String shopId;
    private String customerState;

    private List<ReceiptItem> items = new ArrayList<>();

    private String transactionId;
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

    private String paymentMethod;
    private String paymentStatus;
    private String orderStatus;
    private String expectedDeliveryDate;
    private String receiptNotes;

    private Date receiptDate;
    private Date orderDate;
    private Date paymentCompletedDate;

    private String gstin;
    private String sacCode;
    private String hsnCode;
}
