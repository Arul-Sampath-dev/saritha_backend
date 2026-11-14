package com.saritha.fashions.modules.expense.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.saritha.fashions.modules.expense.model.ExpenseItem;
import com.saritha.fashions.modules.expense.model.TravelExpenseItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * The expense that was recorded, plus the shop's balances after recording it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ExpenseResponse", description = "An expense (and, after a change, the shop's new balances)")
public class ExpenseResponse {

    @Schema(description = "Expense id", example = "6672a9f0c1d2e3f4a5b6c7d8")
    private String id;

    @Schema(description = "Shop the expense belongs to", example = "665f1c2e9b1e8a3d4c7f0a12")
    private String shopId;

    @Schema(description = "Employee the expense belongs to. null for a shop expense.",
            example = "665f0a1b2c3d4e5f6a7b8c9d", nullable = true)
    private String userId;

    @Schema(description = "Category of the expense", example = "Electricity")
    private String expenseType;

    @Schema(description = "Amount spent", example = "1850.0")
    private double expenseAmount;

    @Schema(description = "How it was paid", example = "Cash")
    private String paymentMethod;

    @Schema(description = "Notes", example = "EB bill for October")
    private String notes;

    @Schema(description = "When the expense happened")
    private Date expenseDate;

    @Schema(description = "Refreshment line items")
    private List<ExpenseItem> refreshmentDetails;

    @Schema(description = "Miscellaneous line items")
    private List<ExpenseItem> miscellaneousDetails;

    @Schema(description = "Travel line items")
    private List<TravelExpenseItem> travelDetails;

    @Schema(description = "Employee's name (employee expenses, in lists)", example = "Lakshmi")
    private String employeeName;

    @Schema(description = "Shop's petty cash balance after this change. Only on create / update / delete responses.",
            example = "2650.0")
    private Double pettyCashBalance;

    @Schema(description = "Shop's total expense after this change. Only on create / update / delete responses.",
            example = "14650.0")
    private Double shopTotalExpense;
}
