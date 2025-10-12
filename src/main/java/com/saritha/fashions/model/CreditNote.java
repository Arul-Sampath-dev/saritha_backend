package com.saritha.fashions.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

@Document(collection = "credit_notes")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreditNote {
    @Id
    private String id;
    private String creditNoteNo;
    private String invoiceId;
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
