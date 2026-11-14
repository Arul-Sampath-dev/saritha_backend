package com.saritha.fashions.modules.receipt.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * The payment receipt of one recorded payment: what was paid now, and the invoice's position
 * (total paid so far, balance) right after this payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PaymentReceipt", description = "Receipt for one payment against an invoice")
public class PaymentReceiptResponse {

    @Schema(example = "SARI-PR-14-11-2026-00003")
    private String paymentReceiptNo;
    @Schema(example = "SARI-12-11-2026-00042")
    private String invoiceId;
    @Schema(example = "665f1c2e9b1e8a3d4c7f0a12")
    private String shopId;
    @Schema(example = "Priya K")
    private String customerName;
    @Schema(example = "9876543210")
    private String customerPhone;
    private Date paidAt;
    @Schema(description = "Amount received with this payment", example = "650.0")
    private double amount;
    @Schema(example = "UPI")
    private String paidVia;
    @Schema(example = "UPI-4589123")
    private String transactionId;
    @Schema(description = "Invoice total with GST at the time of the payment", example = "3150.0")
    private double invoiceTotalWithGST;
    @Schema(description = "Total paid on the invoice including this payment", example = "2150.0")
    private double totalPaid;
    @Schema(description = "Balance left after this payment", example = "1000.0")
    private double balanceDue;
    @Schema(description = "Dues, Partial or Paid after this payment", example = "Partial")
    private String paymentStatus;
    @Schema(description = "Name of who recorded the payment", example = "Saritha R")
    private String receivedBy;
    @Schema(example = "/v1/api/payment-receipts/SARI-PR-14-11-2026-00003/pdf")
    private String pdfUrl;
    @Schema(description = "Only right after the payment was recorded")
    private ReceiptResponse.Notifications notifications;
}
