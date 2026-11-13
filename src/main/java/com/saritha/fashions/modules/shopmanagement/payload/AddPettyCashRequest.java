package com.saritha.fashions.modules.shopmanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for adding petty cash to a shop.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AddPettyCashRequest", description = "Amount of petty cash to add to the shop")
public class AddPettyCashRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @DecimalMax(value = "10000000", message = "amount must not exceed 1,00,00,000")
    @Schema(description = "Amount to add. Must be greater than 0.",
            example = "5000", minimum = "0.01", maximum = "10000000",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Double amount;
}
