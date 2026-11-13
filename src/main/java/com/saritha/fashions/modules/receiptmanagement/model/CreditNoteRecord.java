package com.saritha.fashions.modules.receiptmanagement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Credit note issued when a discount is given on an existing invoice (GST rule: a discount
 * after invoicing is documented by a credit note, the original invoice values stay auditable).
 *
 * Same "credit_notes" collection and fields as com.saritha.fashions.model.CreditNote
 * (BigDecimal fields, stored the same way by Spring Data).
 */
@Document(collection = "credit_notes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditNoteRecord {

    @Id
    private String id;
    private String creditNoteNo;

    /** Human-readable invoice number. */
    private String invoiceId;

    /** Invoice database id. */
    private String invoiceDbId;

    private BigDecimal discountAmount;
    private BigDecimal taxableBefore;
    private BigDecimal taxableAfter;
    private BigDecimal cgstRate;
    private BigDecimal sgstRate;
    private BigDecimal igstRate;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalAmountWithGST;
    private String reason;
    private Date createdAt;
    private String createdBy;
}
