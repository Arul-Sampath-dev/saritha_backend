package com.saritha.fashions.modules.production.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Map;

/**
 * Counts of the logged-in user's work queue (every status the queue can contain), for dashboards and tab badges.
 * "Late" / "due" use the invoice's expected delivery date and today's date in IST.
 */
@Schema(name = "QueueSummary", description = "Counts of my work queue")
public record QueueSummaryResponse(
        @Schema(description = "Today (IST) used for the due / late counts", example = "2026-11-18") LocalDate today,
        @Schema(description = "Items per status (every status of the queue, 0 when none)",
                example = "{\"ORDER_ACCEPTED\": 4, \"CUTTING\": 2, \"TAILOR_ASSIGNED\": 3, \"STITCHING\": 5}")
        Map<String, Long> countsByStatus,
        @Schema(description = "All items in the queue", example = "14") long totalItems,
        @Schema(description = "Orders (invoices) with at least one item in the queue", example = "6") long orders,
        @Schema(description = "Items whose delivery date has passed", example = "2") long late,
        @Schema(description = "Items due today", example = "1") long dueToday,
        @Schema(description = "Items due in the next 7 days (today + 6)", example = "5") long dueThisWeek,
        @Schema(description = "Items whose invoice has no readable delivery date", example = "0") long withoutDueDate) {
}
