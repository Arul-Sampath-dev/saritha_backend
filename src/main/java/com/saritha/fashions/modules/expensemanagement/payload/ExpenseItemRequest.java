package com.saritha.fashions.modules.expensemanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A refreshment or miscellaneous line item in an employee expense request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ExpenseItemRequest", description = "A refreshment or miscellaneous line item")
public class ExpenseItemRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Schema(description = "Amount spent on this item", example = "120",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Double amount;

    @Size(max = 200, message = "description must be at most 200 characters")
    @Schema(description = "What the money was spent on", example = "Tea and snacks for staff")
    private String description;
}
