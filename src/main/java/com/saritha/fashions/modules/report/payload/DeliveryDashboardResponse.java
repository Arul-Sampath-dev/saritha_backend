package com.saritha.fashions.modules.report.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Delivery dashboard for one day: orders due that day (ready or still in production), overdue
 * orders, and orders delivered that day.
 *
 * @param shopId null when the dashboard covers all shops (platform owner)
 */
@Schema(name = "DeliveryDashboard", description = "Orders due for delivery on a day, overdue orders and deliveries made")
public record DeliveryDashboardResponse(
        @Schema(example = "2026-11-14") LocalDate date,
        @Schema(description = "The shop, or null for all shops", example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
        Summary summary,
        @Schema(description = "Not delivered, expected delivery = date. Ready orders first, then by invoice date.")
        List<DeliveryOrder> due,
        @Schema(description = "Not delivered, expected delivery before date. Oldest first.")
        List<DeliveryOrder> overdue,
        @Schema(description = "Receipts (deliveries) made on date, in time order")
        List<DeliveredOrder> delivered) {

    @Schema(name = "DeliveryDashboardSummary")
    public record Summary(
            @Schema(description = "Orders due on the date", example = "6") int dueCount,
            @Schema(description = "Of those, all items completed (ready to hand over)", example = "4") int dueReadyCount,
            @Schema(description = "Of those, items still in production", example = "2") int dueInProductionCount,
            @Schema(description = "Orders past their delivery date and not delivered", example = "3") int overdueCount,
            @Schema(description = "Orders delivered (receipt generated) on the date", example = "5") int deliveredCount,
            @Schema(description = "Open orders without a readable delivery date (not in the lists)", example = "1")
            int withoutDeliveryDateCount,
            @Schema(description = "Balance still to collect on due + overdue orders", example = "8450.0")
            double balanceToCollect) {
    }

    @Schema(name = "DeliveryOrder", description = "An order waiting for delivery")
    public record DeliveryOrder(
            @Schema(example = "SARI-10-11-2026-00031") String invoiceId,
            @Schema(example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
            @Schema(example = "Priya K") String customerName,
            @Schema(example = "9876543210") String customerPhone,
            @Schema(example = "2026-11-14") LocalDate expectedDeliveryDate,
            @Schema(description = "0 when due today; days late for overdue orders", example = "0") long daysOverdue,
            @Schema(description = "All items ORDER_COMPLETED", example = "true") boolean ready,
            @Schema(example = "3") int itemCount,
            @Schema(example = "3") int completedItemCount,
            List<DeliveryItem> items,
            @Schema(example = "3150.0") double totalAmountWithGST,
            @Schema(description = "To collect before the receipt can be generated", example = "1650.0") double balanceDue,
            @Schema(example = "Partial") String paymentStatus,
            Date invoiceDate) {
    }

    @Schema(name = "DeliveryItem")
    public record DeliveryItem(
            @Schema(example = "Blouse") String itemName,
            @Schema(example = "2.5") double itemQuantity,
            @Schema(example = "ORDER_COMPLETED") String status) {
    }

    @Schema(name = "DeliveredOrder", description = "An order delivered on the date")
    public record DeliveredOrder(
            @Schema(example = "SARI-14-11-2026-00007") String receiptNo,
            @Schema(example = "SARI-10-11-2026-00031") String invoiceId,
            @Schema(example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
            @Schema(example = "Priya K") String customerName,
            @Schema(example = "9876543210") String customerPhone,
            Date deliveredAt,
            @Schema(example = "3150.0") double totalAmountWithGST,
            @Schema(description = "Invoice date (null if the invoice is missing)") Date invoiceDate,
            @Schema(description = "Expected delivery date of the invoice", example = "2026-11-14") LocalDate expectedDeliveryDate,
            @Schema(example = "2") int itemCount,
            @Schema(description = "Items of the invoice") List<DeliveryItem> items) {
    }
}
