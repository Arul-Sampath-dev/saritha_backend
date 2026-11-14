package com.saritha.fashions.modules.receipt.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.saritha.fashions.modules.receipt.model.ReceiptItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * A receipt, with where to download its PDF.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Receipt", description = "A receipt (final, fully paid bill of a delivered order)")
public class ReceiptResponse {

    @Schema(example = "6673d4e5f6a7b8c9d0e1f2a3")
    private String id;
    @Schema(example = "SARI-13-11-2026-00007")
    private String receiptNo;
    @Schema(description = "Invoice number", example = "SARI-12-11-2026-00042")
    private String invoiceId;
    @Schema(example = "665f1c2e9b1e8a3d4c7f0a12")
    private String shopId;
    @Schema(example = "Priya K")
    private String customerName;
    @Schema(example = "9876543210")
    private String customerPhone;
    private Date receiptDate;

    private List<ReceiptItem> items;

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
    @Schema(example = "3045.0")
    private double amountPaid;
    @Schema(example = "Paid")
    private String paymentStatus;
    @Schema(example = "UPI")
    private String paymentMethod;
    @Schema(example = "ORDER_DELIVERED")
    private String orderStatus;
    @Schema(example = "33ABCDE1234F1Z5")
    private String gstin;

    @Schema(description = "Relative URL of the receipt PDF",
            example = "/v1/api/receipts/SARI-13-11-2026-00007/pdf")
    private String pdfUrl;

    @Schema(description = "Only on creation: which customer notifications were queued")
    private Notifications notifications;

    @Schema(description = "Only on creation: false if the receipt already existed (returned unchanged)")
    private Boolean created;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ReceiptNotifications")
    public static class Notifications {
        @Schema(description = "QUEUED or SKIPPED", example = "QUEUED")
        private String whatsApp;
        @Schema(description = "QUEUED, or SKIPPED when the customer has no email", example = "SKIPPED")
        private String email;
    }
}
