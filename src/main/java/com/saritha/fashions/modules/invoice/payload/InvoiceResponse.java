package com.saritha.fashions.modules.invoice.payload;

import com.saritha.fashions.modules.invoice.model.InvoiceItem;
import com.saritha.fashions.modules.invoice.model.InvoicePayment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * A created invoice with its calculated amounts, plus where to download the PDF and
 * which customer notifications were queued.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "InvoiceResponse", description = "A created invoice")
public class InvoiceResponse {

    @Schema(description = "Database id", example = "6672b1c0d2e3f4a5b6c7d8e9")
    private String id;

    @Schema(description = "Invoice number", example = "SARI-12-11-2026-00042")
    private String invoiceId;

    @Schema(description = "Shop id", example = "665f1c2e9b1e8a3d4c7f0a12")
    private String shopId;

    private CustomerSummary customer;

    @Schema(description = "Customer state used for GST", example = "Tamil Nadu")
    private String customerState;

    private List<InvoiceItem> items;

    @Schema(description = "Sum of item price × quantity", example = "1700.0")
    private double totalAmount;
    @Schema(example = "100.0")
    private double discount;
    @Schema(description = "totalAmount − discount", example = "1600.0")
    private double taxableAmount;
    @Schema(example = "2.5")
    private double cgstRate;
    @Schema(example = "2.5")
    private double sgstRate;
    @Schema(example = "0.0")
    private double igstRate;
    @Schema(example = "40.0")
    private double cgstAmount;
    @Schema(example = "40.0")
    private double sgstAmount;
    @Schema(example = "0.0")
    private double igstAmount;
    @Schema(description = "taxableAmount + GST", example = "1680.0")
    private double totalAmountWithGST;
    @Schema(example = "1000.0")
    private double advancePaid;
    @Schema(description = "totalAmountWithGST − advancePaid", example = "680.0")
    private double balanceDue;

    @Schema(example = "33ABCDE1234F1Z5")
    private String gstin;
    @Schema(example = "9988")
    private String sacCode;
    @Schema(example = "998821")
    private String hsnCode;

    @Schema(example = "UPI")
    private String paymentMethod;
    @Schema(description = "Dues (nothing paid), Partial (balance due) or Paid", example = "Partial")
    private String paymentStatus;
    @Schema(example = "ORDER_ACCEPTED")
    private String orderStatus;
    private List<InvoicePayment> paymentHistories;

    private Date invoiceDate;
    private Date orderDate;
    @Schema(example = "2026-11-25")
    private String expectedDeliveryDate;
    @Schema(example = "Thank you for your order")
    private String notes;

    @Schema(description = "Receipt number once the receipt is generated (then the invoice can no longer be "
            + "edited or deleted); null before that")
    private String receiptId;

    @Schema(description = "Relative URL to download the invoice PDF",
            example = "/v1/api/invoices/SARI-12-11-2026-00042/pdf")
    private String pdfUrl;

    private NotificationStatus notifications;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "InvoiceCustomerSummary", description = "Customer on the invoice")
    public static class CustomerSummary {
        @Schema(example = "6672b0a1c2d3e4f5a6b7c8d9")
        private String id;
        @Schema(example = "CUS-8F3K2Q1ZXA")
        private String customerId;
        @Schema(example = "Priya K")
        private String name;
        @Schema(example = "9876543210")
        private String phone;
        @Schema(example = "priya@example.com")
        private String email;
        @Schema(description = "True if this invoice created the customer", example = "false")
        private boolean newCustomer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "InvoiceNotificationStatus",
            description = "Which notifications were queued. They are sent in the background after the "
                    + "invoice is saved; a delivery failure never affects the invoice.")
    public static class NotificationStatus {
        @Schema(description = "QUEUED or SKIPPED", example = "QUEUED")
        private String whatsApp;
        @Schema(description = "QUEUED, or SKIPPED when the customer has no email", example = "SKIPPED")
        private String email;
    }
}
