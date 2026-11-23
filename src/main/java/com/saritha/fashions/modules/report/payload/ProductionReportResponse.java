package com.saritha.fashions.modules.report.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Production health: work in progress per stage right now, and turnaround of the orders delivered in the period,
 * per branch and for all of them together.
 */
@Schema(name = "ProductionReport", description = "Work in progress per stage (now) and turnaround of delivered orders (period)")
public record ProductionReportResponse(
        @Schema(description = "Today (IST) used for overdue / age", example = "2026-11-19") LocalDate today,
        @Schema(description = "Period of the turnaround figures (null = from the beginning)") LocalDate from,
        @Schema(description = "Period end, inclusive (null = up to now)") LocalDate to,
        @Schema(description = "One row per branch, by name") List<Branch> branches,
        @Schema(description = "All the branches above together") Branch total) {

    @Schema(name = "ProductionBranch")
    public record Branch(
            @Schema(example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
            @Schema(example = "Saritha Fashions - Navalur") String shopName,
            @Schema(description = "Items not delivered yet (every stage)", example = "42") long openItems,
            @Schema(description = "Of those, on orders whose delivery date has passed", example = "6") long overdueItems,
            @Schema(description = "Open items per stage, in workflow order (ORDER_ACCEPTED … ORDER_COMPLETED)") List<Stage> stages,
            Turnaround turnaround) {
    }

    @Schema(name = "ProductionStage", description = "Open items in one stage")
    public record Stage(
            @Schema(example = "STITCHING") String status,
            @Schema(example = "12") long items,
            @Schema(description = "Of those, overdue", example = "3") long overdue,
            @Schema(description = "Average days the items have been in this stage (null when none)", example = "2.4") Double avgDaysInStage,
            @Schema(description = "Longest time an item has been in this stage, days (null when none)", example = "9.0") Double maxDaysInStage) {
    }

    @Schema(name = "ProductionTurnaround", description = "Orders delivered (receipt generated) in the period")
    public record Turnaround(
            @Schema(example = "38") long deliveredOrders,
            @Schema(description = "Average days from the invoice to delivery (null when none)", example = "11.6") Double avgDaysToDeliver,
            @Schema(description = "Delivered on or before the expected date, % of the orders that had one (null when none)", example = "84.2")
            Double onTimePercent,
            @Schema(description = "Delivered orders with a readable expected date", example = "38") long withDueDate,
            @Schema(description = "Average time items spent in each stage, from the item history; the READY stage is "
                    + "ORDER_COMPLETED until delivery") List<StageTime> stageTimes) {
    }

    @Schema(name = "ProductionStageTime")
    public record StageTime(
            @Schema(example = "STITCHING") String status,
            @Schema(description = "Average hours in the stage", example = "52.5") double avgHours,
            @Schema(description = "Item stays measured", example = "57") long samples) {
    }
}
