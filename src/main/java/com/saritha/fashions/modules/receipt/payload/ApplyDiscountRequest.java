package com.saritha.fashions.modules.receipt.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A discount given on an existing invoice (documented with a credit note).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ApplyDiscountRequest", description = "Discount on the invoice amount before GST")
public class ApplyDiscountRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @DecimalMax(value = "100000000", message = "amount is too large")
    @Schema(description = "Discount on the pre-GST total. GST is recalculated on the reduced amount.",
            example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double amount;

    @Size(max = 200, message = "reason must be at most 200 characters")
    @Schema(description = "Shown on the credit note. Defaults to \"Owner discount\".", example = "Delay in delivery")
    private String reason;
}
