package com.saritha.fashions.modules.report.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Financial summary of one shop or all shops for a period, computed from the live data.
 *
 * @param shopId      null when the summary covers all shops
 * @param netCashFlow collections.total − expenses.total (money in − money out in the period)
 */
@Schema(name = "FinancialSummary", description = "Sales, collections, expenses and petty cash for a period")
public record FinancialSummaryResponse(
        @Schema(description = "The shop, or null for all shops", example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
        @Schema(example = "Saritha Fashions - Anna Nagar") String shopName,
        @Schema(description = "First day (null = from the beginning)", example = "2026-11-01") LocalDate from,
        @Schema(description = "Last day, inclusive (null = up to now)", example = "2026-11-30") LocalDate to,
        Sales sales,
        Collections collections,
        Expenses expenses,
        PettyCash pettyCash,
        @Schema(description = "collections.total − expenses.total", example = "41250.0") double netCashFlow,
        Outstanding outstanding,
        @Schema(description = "Receipts generated in the period", example = "38") long receiptCount,
        @Schema(description = "Invoices deleted in the period", example = "2") long deletedInvoiceCount) {

    /** Invoices dated in the period. */
    @Schema(name = "FinancialSummarySales")
    public record Sales(
            @Schema(example = "42") long invoiceCount,
            @Schema(description = "Sum of totalAmountWithGST", example = "98400.0") double invoicedAmount,
            @Schema(description = "Sum of taxableAmount", example = "93714.29") double taxableAmount,
            @Schema(description = "CGST + SGST + IGST", example = "4685.71") double gstAmount,
            @Schema(example = "1200.0") double discount) {
    }

    /** Money received in the period (payment entries by payment date). */
    @Schema(name = "FinancialSummaryCollections")
    public record Collections(
            @Schema(example = "62750.0") double total,
            List<MethodTotal> byPaymentMethod) {
    }

    /** Expenses dated in the period. */
    @Schema(name = "FinancialSummaryExpenses")
    public record Expenses(
            @Schema(example = "21500.0") double total,
            @Schema(example = "17") long count,
            @Schema(description = "Expenses of the shop itself", example = "18000.0") double shopExpenses,
            @Schema(description = "Expenses recorded for employees", example = "3500.0") double employeeExpenses,
            List<MethodTotal> byPaymentMethod,
            @Schema(description = "Per category (expenseType), largest first") List<CategoryTotal> byType) {
    }

    @Schema(name = "ExpenseCategoryTotal")
    public record CategoryTotal(
            @Schema(example = "Rent") String expenseType,
            @Schema(example = "12000.0") double amount,
            @Schema(example = "1") long count) {
    }

    @Schema(name = "FinancialSummaryPettyCash")
    public record PettyCash(
            @Schema(description = "Top-ups in the period", example = "15000.0") double added,
            @Schema(description = "Current balance (not limited to the period)", example = "4500.0") double currentBalance) {
    }

    /** Current state, not limited to the period. */
    @Schema(name = "FinancialSummaryOutstanding")
    public record Outstanding(
            @Schema(description = "Invoices not yet delivered", example = "12") long openInvoiceCount,
            @Schema(description = "Their total balance due", example = "18600.0") double balanceDue) {
    }

    @Schema(name = "MethodTotal")
    public record MethodTotal(
            @Schema(example = "Cash") String paymentMethod,
            @Schema(example = "30500.0") double amount,
            @Schema(example = "21") long count) {
    }
}
