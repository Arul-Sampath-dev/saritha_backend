package com.saritha.fashions.modules.invoicemanagement.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A payment made against an invoice (same stored shape as Invoice.PaymentHistory).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "InvoicePayment", description = "A payment made against the invoice")
public class InvoicePayment {

    @Schema(description = "Amount paid", example = "1000.0")
    private Double amount;

    @Schema(description = "When it was paid")
    private Instant paidAt;

    @Schema(description = "Payment method", example = "UPI")
    private String paidVia;

    private Boolean discountApplied;
    private Double discountAmount;
}
