package com.saritha.fashions.modules.report.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Customer analytics from the invoices (a customer is "new" in the month / period of their first invoice in scope).
 */
@Schema(name = "CustomerReport", description = "New vs returning customers, top customers and customers with dues")
public record CustomerReportResponse(
        @Schema(description = "Period start (null = from the beginning)") LocalDate from,
        @Schema(description = "Period end, inclusive (null = up to now)") LocalDate to,
        @Schema(description = "Customers with at least one invoice (all time, in scope)", example = "812") long totalCustomers,
        @Schema(description = "Customers with an invoice in the period", example = "143") long activeCustomers,
        @Schema(description = "Of those, whose first invoice is in the period", example = "61") long newCustomers,
        @Schema(description = "Of those, who had an invoice before the period", example = "82") long returningCustomers,
        @Schema(description = "% of all customers with 2 or more invoices (all time)", example = "38.5") double repeatRate,
        @Schema(description = "Invoiced (incl. GST) in the period / active customers", example = "1850.0") double avgSpendPerCustomer,
        @Schema(description = "Per month: customers with their first invoice that month, and returning ones") List<Month> months,
        @Schema(description = "Top 20 customers by amount invoiced in the period") List<TopCustomer> top,
        @Schema(description = "Customers with balance due on undelivered orders, largest first (max 50)") List<CustomerDue> withDues,
        @Schema(description = "Customers with dues (all, not only the 50 listed)", example = "57") long customersWithDues,
        @Schema(description = "Their total balance due", example = "86400.0") double totalDue) {

    @Schema(name = "CustomerMonth")
    public record Month(@Schema(example = "2026-11") String month,
                        @Schema(example = "21") long newCustomers,
                        @Schema(example = "34") long returningCustomers) {
    }

    @Schema(name = "TopCustomer")
    public record TopCustomer(String customerId, String name, String phone,
                              @Schema(description = "Invoices in the period") long invoices,
                              @Schema(description = "Invoiced incl. GST in the period") double spent,
                              @Schema(description = "Invoices all time (in scope)") long lifetimeInvoices,
                              @Schema(description = "Latest invoice date") Date lastVisit,
                              @Schema(description = "Branches the customer bought from (in scope)") int branches) {
    }

    @Schema(name = "CustomerDue")
    public record CustomerDue(String customerId, String name, String phone,
                              @Schema(description = "Undelivered orders with a balance") long openOrders,
                              double balanceDue,
                              @Schema(description = "Earliest expected delivery of those orders (null when none readable)") LocalDate oldestDue) {
    }
}
