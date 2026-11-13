package com.saritha.fashions.modules.reporting.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One shop on the platform owner's dashboard. Replaces the legacy shop dashboard rows
 * (AllShopDTO), whose expense totals were always 0.
 */
@Schema(name = "ShopOverview", description = "One shop's figures for the period")
public record ShopOverviewResponse(
        @Schema(example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
        @Schema(example = "Saritha Fashions - Anna Nagar") String shopName,
        @Schema(example = "Boutique") String shopType,
        @Schema(example = "Saritha R") String ownerName,
        @Schema(description = "Users assigned to the shop (incl. the shop owner)", example = "6") int employeeCount,
        @Schema(description = "Current petty cash balance", example = "4500.0") double pettyCash,
        @Schema(description = "Invoices dated in the period", example = "42") long invoiceCount,
        @Schema(description = "Their total with GST", example = "98400.0") double invoicedAmount,
        @Schema(description = "Of those, fully paid", example = "30") long paidInvoiceCount,
        @Schema(description = "Of those, delivered", example = "28") long deliveredInvoiceCount,
        @Schema(description = "Money received in the period", example = "62750.0") double collected,
        @Schema(description = "Expenses in the period, all payment methods", example = "21500.0") double expenses,
        @Schema(description = "Of those, paid in cash (from petty cash)", example = "9800.0") double cashExpenses,
        @Schema(description = "collected − expenses", example = "41250.0") double netCashFlow,
        @Schema(description = "Invoices not yet delivered (now)", example = "12") long pendingOrders,
        @Schema(description = "Balance due on those (now)", example = "18600.0") double outstanding,
        @Schema(description = "Receipts generated in the period", example = "38") long receiptCount) {
}
