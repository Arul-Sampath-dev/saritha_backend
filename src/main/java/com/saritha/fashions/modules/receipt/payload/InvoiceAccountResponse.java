package com.saritha.fashions.modules.receipt.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * The money side of an invoice after a payment or discount: totals, paid, due, payments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "InvoiceAccount", description = "Amounts, payments and balance of an invoice")
public class InvoiceAccountResponse {

    @Schema(example = "SARI-12-11-2026-00042")
    private String invoiceId;
    @Schema(example = "3050.0")
    private double totalAmount;
    @Schema(example = "150.0")
    private double discount;
    @Schema(example = "2900.0")
    private double taxableAmount;
    @Schema(example = "72.5")
    private double cgstAmount;
    @Schema(example = "72.5")
    private double sgstAmount;
    @Schema(example = "0.0")
    private double igstAmount;
    @Schema(example = "3045.0")
    private double totalAmountWithGST;
    @Schema(example = "2150.0")
    private double advancePaid;
    @Schema(example = "895.0")
    private double balanceDue;
    @Schema(description = "Dues, Partial or Paid", example = "Partial")
    private String paymentStatus;
    @Schema(description = "Credit note id, when a discount was just applied")
    private String creditNoteId;
    private List<Payment> payments;

    @Schema(description = "The payment receipt created by this payment (only in the response of POST .../payments)")
    private PaymentReceiptResponse paymentReceipt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "InvoicePaymentEntry")
    public static class Payment {
        @Schema(example = "650.0")
        private Double amount;
        @Schema(example = "UPI")
        private String paidVia;
        private Date paidAt;
        @Schema(example = "UPI-4589123")
        private String transactionId;
        @Schema(description = "Payment receipt of this payment (payments recorded before payment receipts existed, "
                + "and the advance taken on the invoice, have none)", example = "SARI-PR-14-11-2026-00003")
        private String paymentReceiptNo;
    }
}
