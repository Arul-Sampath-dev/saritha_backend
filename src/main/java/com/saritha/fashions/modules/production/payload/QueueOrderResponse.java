package com.saritha.fashions.modules.production.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * One order (invoice) of the work queue with ITS items that are in the queue — the queue grouped by invoice.
 * Items of the invoice outside the caller's queue (e.g. already with the shop assistant) are not included.
 */
@Schema(name = "QueueOrder", description = "An invoice of my work queue with its queue items")
public record QueueOrderResponse(
        @Schema(example = "SARI-18-11-2026-00042") String invoiceId,
        @Schema(example = "Priya R") String customerName,
        Date invoiceDate,
        @Schema(description = "As stored on the invoice", example = "2026-11-22") String expectedDeliveryDate,
        @Schema(description = "expectedDeliveryDate as yyyy-MM-dd; null when unreadable", example = "2026-11-22") String dueDate,
        @Schema(description = "Days past dueDate; 0 when not late", example = "0") long daysOverdue,
        @Schema(description = "Queue items of this order per status",
                example = "{\"ORDER_ACCEPTED\": 1, \"CUTTING\": 1, \"TAILOR_ASSIGNED\": 0, \"STITCHING\": 1}")
        Map<String, Long> countsByStatus,
        @Schema(description = "Number of queue items of this order", example = "3") int itemCount,
        List<WorkItemResponse> items) {
}
