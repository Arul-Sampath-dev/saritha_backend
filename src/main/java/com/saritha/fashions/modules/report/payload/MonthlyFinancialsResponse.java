package com.saritha.fashions.modules.report.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/** One month of the financial series (GET /v1/api/reports/financials/monthly). */
@Schema(name = "MonthlyFinancials", description = "Sales, collections and expenses of one month (IST)")
public record MonthlyFinancialsResponse(
        @Schema(description = "yyyy-MM", example = "2026-11") String month,
        @Schema(example = "84") long invoiceCount,
        @Schema(description = "Invoiced amount with GST", example = "196800.0") double invoicedAmount,
        @Schema(description = "Money received", example = "125500.0") double collected,
        @Schema(example = "43000.0") double expenses,
        @Schema(description = "collected - expenses", example = "82500.0") double netCashFlow) {
}
