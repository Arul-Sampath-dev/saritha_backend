package com.saritha.fashions.modules.expense.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Totals of the expenses matching the list filters (GET /v1/api/expenses/summary). */
@Schema(name = "ExpenseSummary", description = "Totals of the expenses matching the filters")
public record ExpenseSummaryResponse(
        @Schema(example = "42") long count,
        @Schema(example = "38450.0") double totalAmount,
        @Schema(description = "Expenses of the shop itself", example = "21500.0") double shopExpenses,
        @Schema(description = "Expenses recorded for employees (salary, advance, ...)", example = "16950.0") double employeeExpenses,
        @Schema(description = "Per category (expenseType), largest first") List<Group> byType,
        @Schema(description = "Per payment method (canonical names), largest first") List<Group> byPaymentMethod) {

    public record Group(@Schema(example = "Rent") String name, @Schema(example = "12000.0") double amount,
                        @Schema(example = "1") long count) {
    }
}
