package com.saritha.fashions.modules.invoice.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where an invoice is in production, derived from its items' statuses. Used for the "stage" column and the
 * stage filter of GET /v1/api/invoices; {@code InvoiceRecordRepositoryCustomImpl.stageCriteria} must match
 * {@link #of} exactly.
 *
 * <ul>
 *   <li>{@code NEW} - not delivered, and no item has moved past ORDER_ACCEPTED (also: no items)</li>
 *   <li>{@code IN_PRODUCTION} - not delivered, at least one item started, not every item ORDER_COMPLETED</li>
 *   <li>{@code READY} - not delivered, every item ORDER_COMPLETED (waiting for payment / handover)</li>
 *   <li>{@code DELIVERED} - the receipt was generated (orderStatus ORDER_DELIVERED)</li>
 * </ul>
 */
public enum InvoiceStage {
    NEW, IN_PRODUCTION, READY, DELIVERED;

    public static final String ACCEPTED = "ORDER_ACCEPTED";
    public static final String COMPLETED = "ORDER_COMPLETED";
    public static final String DELIVERED_ORDER = "ORDER_DELIVERED";

    /** Item statuses of the production workflow, in order (for counts and the itemStatus filter). */
    public static final List<String> ITEM_STATUSES = List.of(ACCEPTED, "CUTTING", "TAILOR_ASSIGNED", "STITCHING",
            "SHOP_ASSISTANT_ASSIGNED", "HEMMING", "IRONING", COMPLETED);

    public static InvoiceStage of(String orderStatus, List<InvoiceItem> items) {
        if (DELIVERED_ORDER.equals(orderStatus)) {
            return DELIVERED;
        }
        List<InvoiceItem> list = items == null ? List.of() : items;
        if (!list.isEmpty() && list.stream().allMatch(item -> COMPLETED.equals(item.getItemOrderStatus()))) {
            return READY;
        }
        boolean started = list.stream().map(InvoiceItem::getItemOrderStatus)
                .anyMatch(status -> status != null && !ACCEPTED.equals(status));
        return started ? IN_PRODUCTION : NEW;
    }

    /** Item count per status: every workflow status (0 when none), then any other status found. */
    public static Map<String, Integer> statusCounts(List<InvoiceItem> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ITEM_STATUSES.forEach(status -> counts.put(status, 0));
        if (items != null) {
            for (InvoiceItem item : items) {
                String status = item.getItemOrderStatus() == null ? ACCEPTED : item.getItemOrderStatus();
                counts.merge(status, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** Parses a stage name (case-insensitive; "in-production" and "in production" also accepted). */
    public static Optional<InvoiceStage> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (InvoiceStage stage : values()) {
            if (stage.name().equals(key)) {
                return Optional.of(stage);
            }
        }
        return Optional.empty();
    }
}
