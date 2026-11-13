package com.saritha.fashions.modules.reporting.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * One day of the daily financial series (every day of the range is present, zeros included).
 */
@Schema(name = "DailyFinancials", description = "Sales, collections and expenses of one day (IST)")
public record DailyFinancialsResponse(
        @Schema(example = "2026-11-12") LocalDate date,
        @Schema(example = "3") long invoiceCount,
        @Schema(description = "Invoiced amount with GST", example = "6200.0") double invoicedAmount,
        @Schema(description = "Money received", example = "4500.0") double collected,
        @Schema(example = "850.0") double expenses,
        @Schema(description = "collected − expenses", example = "3650.0") double netCashFlow) {
}
