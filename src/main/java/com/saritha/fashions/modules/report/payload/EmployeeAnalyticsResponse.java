package com.saritha.fashions.modules.report.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Map;

/**
 * Performance figures for one employee (or, for role SHOP_OWNER, the shop's items) in a period.
 * Replaces GET /v1/api/employee/activities/employee/{employeeId}/analytics and computes the same figures
 * as the legacy EmployeeActivityServiceImpl.getAnalyticsForRole:
 *
 * <ul>
 *   <li><b>TAILOR_MASTER</b> - the master's own activities: {@code assigned} = items assigned to a tailor (first
 *       assignments), quantity / revenue of all first-time activities, reassignments.</li>
 *   <li><b>TAILOR</b> - activities by or assigned to the tailor: {@code totalAssigned} = items assigned to them,
 *       {@code assigned} / {@code totalCompleted} = items they handed to a shop assistant (= stitched).</li>
 *   <li><b>SHOP_ASSISTANT</b> - activities by or assigned to the assistant: {@code totalAssigned} = items assigned to
 *       them, {@code assigned} / {@code totalCompleted} = items they completed.</li>
 *   <li><b>SHOP_OWNER</b> - the shop's invoices in the period: item counts per production status, revenue
 *       (Σ price × quantity, before GST and discount), invoice / receipt / deleted-invoice counts.</li>
 * </ul>
 * Fields that do not apply to the role are left out of the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "EmployeeAnalytics", description = "Performance figures of one employee in a period")
public record EmployeeAnalyticsResponse(
        @Schema(example = "665f0a1b2c3d4e5f6a7b8c9e") String employeeId,
        @Schema(description = "Role the figures are computed for", example = "TAILOR") String role,
        @Schema(description = "First day (null = from the beginning)", example = "2026-11-01") LocalDate from,
        @Schema(description = "Last day, inclusive (null = up to now)", example = "2026-11-30") LocalDate to,

        // ---- workflow roles (TAILOR_MASTER, TAILOR, SHOP_ASSISTANT)
        @Schema(description = "First-time (not re-) assignments / completions counted for the role", example = "12")
        Long assigned,
        @Schema(description = "Quantity of those items", example = "18.5") Double totalQuantity,
        @Schema(description = "Their value (price × quantity, before GST)", example = "15400.0") Double totalRevenue,
        @Schema(description = "Reassignments", example = "1") Long totalReassignments,
        @Schema(description = "Quantity of reassigned items", example = "2.0") Double reassignmentQuantity,
        @Schema(description = "Value of reassigned items", example = "1700.0") Double reassignmentRevenue,
        @Schema(description = "TAILOR / SHOP_ASSISTANT: items assigned to the employee", example = "14") Long totalAssigned,
        @Schema(description = "TAILOR / SHOP_ASSISTANT: items the employee finished their step on", example = "12")
        Long totalCompleted,

        // ---- SHOP_OWNER (the shop's invoices)
        @Schema(description = "SHOP_OWNER: items per production status (ORDER_ACCEPTED, CUTTING, ...)")
        Map<String, Long> itemStatusCounts,
        @Schema(description = "SHOP_OWNER: sum of the item quantities", example = "86.5") Double totalItems,
        @Schema(description = "SHOP_OWNER: invoices dated in the period", example = "42") Long invoiceCount,
        @Schema(description = "SHOP_OWNER: totalRevenue / invoiceCount", example = "2342.86") Double averageRevenuePerInvoice,
        @Schema(description = "SHOP_OWNER: receipts generated in the period", example = "38") Long receiptCount,
        @Schema(description = "SHOP_OWNER: invoices deleted in the period", example = "2") Long deletedInvoiceCount) {
}
