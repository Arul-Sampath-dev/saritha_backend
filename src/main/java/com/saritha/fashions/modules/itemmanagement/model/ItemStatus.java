package com.saritha.fashions.modules.itemmanagement.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Production statuses of an invoice item, in workflow order.
 *
 * Stored in invoice.items[].itemOrderStatus with exactly these names (same strings as
 * com.saritha.fashions.config.OrderStatus), so existing screens keep working.
 *
 * ORDER_ACCEPTED → CUTTING → TAILOR_ASSIGNED → STITCHING → SHOP_ASSISTANT_ASSIGNED
 *   → HEMMING → IRONING → ORDER_COMPLETED
 *
 * Delivery is tracked on the invoice (orderStatus = ORDER_DELIVERED when the receipt
 * is generated), not on items.
 */
public enum ItemStatus {
    ORDER_ACCEPTED,
    CUTTING,
    TAILOR_ASSIGNED,
    STITCHING,
    SHOP_ASSISTANT_ASSIGNED,
    HEMMING,
    IRONING,
    ORDER_COMPLETED;

    /** Parses a stored / requested value; empty for unknown values (e.g. typos in old data). */
    public static Optional<ItemStatus> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalised = value.trim().toUpperCase();
        return Arrays.stream(values()).filter(status -> status.name().equals(normalised)).findFirst();
    }
}
