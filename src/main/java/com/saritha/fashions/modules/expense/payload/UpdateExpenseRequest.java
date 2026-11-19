package com.saritha.fashions.modules.expense.payload;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Partial update of an expense (shop or employee). Send at least one field; fields left out (or null) keep their
 * current value.
 *
 * The shop and the kind (shop vs employee expense) are fixed. Line items (refreshment / travel / misc) are not
 * edited here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateExpenseRequest",
        description = "Change an expense: amount, notes, category, date, payment method, or the employee of an "
                + "employee expense. Send at least one field.")
public class UpdateExpenseRequest {

    @DecimalMin(value = "0.01", message = "expenseAmount must be greater than 0")
    @DecimalMax(value = "10000000", message = "expenseAmount must not exceed 1,00,00,000")
    @Schema(description = "New amount. The difference is applied to the shop's total expense and, for Cash, to petty cash.",
            example = "2100", nullable = true)
    private Double expenseAmount;

    @Size(max = 500, message = "notes must be at most 500 characters")
    @Schema(description = "New notes. Send \"\" to clear them.",
            example = "EB bill for October (revised)", nullable = true)
    private String notes;

    @Size(max = 100, message = "expenseType must be at most 100 characters")
    @Pattern(regexp = "^(?!\\s*$)[\\s\\S]+$", message = "expenseType must not be blank")
    @Schema(description = "New category", example = "Electricity Bill", nullable = true)
    private String expenseType;

    @JsonFormat(timezone = "Asia/Kolkata")
    @Schema(description = "New date of the expense (yyyy-MM-dd or a timestamp)", example = "2026-11-12", nullable = true)
    private Date expenseDate;

    @Size(max = 30, message = "paymentMethod must be at most 30 characters")
    @Schema(description = "New payment method (Cash, UPI, Card, Bank Transfer, Other). Changing to or from Cash moves "
            + "the amount out of / back into petty cash (refused if petty cash is too low).",
            example = "UPI", nullable = true)
    private String paymentMethod;

    @Schema(description = "Employee expenses only: move the expense to another employee of the shop",
            example = "665f0a1b2c3d4e5f6a7b8c9e", nullable = true)
    private String employeeUserId;

    /** Bean Validation rule: the body must change something. Not part of the JSON. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Provide at least one of expenseAmount, notes, expenseType, expenseDate, paymentMethod, employeeUserId")
    public boolean isAnyFieldProvided() {
        return expenseAmount != null || notes != null || expenseType != null || expenseDate != null
                || paymentMethod != null || employeeUserId != null;
    }
}
