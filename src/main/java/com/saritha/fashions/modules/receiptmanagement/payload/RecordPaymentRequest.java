package com.saritha.fashions.modules.receiptmanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A payment collected from the customer against an invoice.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RecordPaymentRequest", description = "A payment against an invoice")
public class RecordPaymentRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @DecimalMax(value = "100000000", message = "amount is too large")
    @Schema(description = "Amount paid now. Must not exceed the balance due.", example = "650",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Double amount;

    @NotBlank(message = "paymentMethod is required")
    @Size(max = 30, message = "paymentMethod must be at most 30 characters")
    @Schema(example = "UPI", requiredMode = Schema.RequiredMode.REQUIRED)
    private String paymentMethod;

    @Size(max = 100, message = "transactionId must be at most 100 characters")
    @Schema(description = "Optional UPI / card reference", example = "UPI-4589123")
    private String transactionId;
}
