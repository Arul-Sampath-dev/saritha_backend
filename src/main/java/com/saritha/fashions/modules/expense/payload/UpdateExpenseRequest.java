package com.saritha.fashions.modules.expense.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update of an expense (shop or employee). Only the amount and the notes can
 * change; send at least one of them. Fields left out (or null) keep their current value.
 *
 * Everything else (shop, employee, type, payment method, date) is fixed; to change
 * those, delete the expense and create a new one.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateExpenseRequest",
        description = "Change the amount and/or notes of an expense. Send at least one field.")
public class UpdateExpenseRequest {

    @DecimalMin(value = "0.01", message = "expenseAmount must be greater than 0")
    @DecimalMax(value = "10000000", message = "expenseAmount must not exceed 1,00,00,000")
    @Schema(description = "New amount. The difference from the old amount is applied to the shop's "
            + "total expense and, for Cash expenses, to petty cash.",
            example = "2100", nullable = true)
    private Double expenseAmount;

    @Size(max = 500, message = "notes must be at most 500 characters")
    @Schema(description = "New notes. Send \"\" to clear them.",
            example = "EB bill for October (revised)", nullable = true)
    private String notes;

    /** Bean Validation rule: the body must change something. Not part of the JSON. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Provide expenseAmount and/or notes to update")
    public boolean isAnyFieldProvided() {
        return expenseAmount != null || notes != null;
    }
}
