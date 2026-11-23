package com.saritha.fashions.modules.report.service;

import com.saritha.fashions.modules.core.date.DeliveryDates;
import com.saritha.fashions.modules.report.payload.ProductionReportResponse;
import org.bson.Document;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Production health figures for one branch (or all of them together), built from raw invoice documents.
 *
 * - Work in progress: every item of an undelivered invoice, per stage (itemOrderStatus), overdue when the
 *   invoice's expected delivery date is before today, and how long it has been in that stage (since the latest
 *   history entry with that status; ORDER_ACCEPTED without one: since the invoice date).
 * - Turnaround: invoices delivered in the period (their receipt date): days from the invoice to delivery,
 *   on time when delivered on or before the expected date, and the time each item spent in each stage: from
 *   its history (sorted), each entry lasts until the next one; the last (ORDER_COMPLETED) until delivery, counted
 *   as READY. Re-assignments (TAILOR_ASSIGNED twice) add to the same stage.
 *
 * Not thread-safe; one instance per branch per request.
 */
final class ProductionHealth {

    static final List<String> STAGES = List.of("ORDER_ACCEPTED", "CUTTING", "TAILOR_ASSIGNED", "STITCHING",
            "SHOP_ASSISTANT_ASSIGNED", "HEMMING", "IRONING", "ORDER_COMPLETED");
    static final String READY = "READY";
    private static final double HOUR_MS = 3_600_000d;
    private static final double DAY_MS = 86_400_000d;

    private final LocalDate today;
    private final long now;
    private final ZoneId zone;

    // work in progress
    private long openItems;
    private long overdueItems;
    private final Map<String, long[]> stageCounts = new LinkedHashMap<>(); // status -> [items, overdue]
    private final Map<String, double[]> stageAges = new LinkedHashMap<>(); // status -> [sum days, samples, max days]

    // turnaround
    private long delivered;
    private double daysToDeliverSum;
    private long daysToDeliverSamples;
    private long withDueDate;
    private long onTime;
    private final Map<String, double[]> stageHours = new LinkedHashMap<>(); // status -> [sum hours, samples]

    ProductionHealth(LocalDate today, Date now, ZoneId zone) {
        this.today = today;
        this.now = now.getTime();
        this.zone = zone;
        STAGES.forEach(s -> {
            stageCounts.put(s, new long[2]);
            stageAges.put(s, new double[3]);
            stageHours.put(s, new double[2]);
        });
        stageHours.put(READY, new double[2]);
    }

    /** Adds the items of one undelivered invoice. */
    void addOpenInvoice(Document invoice) {
        Optional<LocalDate> due = DeliveryDates.parse(text(invoice.get("expectedDeliveryDate")));
        boolean late = due.isPresent() && due.get().isBefore(today);
        Date invoiceDate = invoice.get("invoiceDate") instanceof Date d ? d : null;
        for (Document item : items(invoice)) {
            String status = text(item.get("itemOrderStatus"));
            if (status == null || !stageCounts.containsKey(status)) {
                continue;
            }
            openItems++;
            long[] counts = stageCounts.get(status);
            counts[0]++;
            if (late) {
                overdueItems++;
                counts[1]++;
            }
            Date since = latestEntry(item, status).orElse("ORDER_ACCEPTED".equals(status) ? invoiceDate : null);
            if (since != null && since.getTime() <= now) {
                double days = (now - since.getTime()) / DAY_MS;
                double[] age = stageAges.get(status);
                age[0] += days;
                age[1]++;
                age[2] = Math.max(age[2], days);
            }
        }
    }

    /** Adds one invoice delivered on {@code deliveredAt}. */
    void addDeliveredInvoice(Document invoice, Date deliveredAt) {
        if (deliveredAt == null) {
            return;
        }
        delivered++;
        Date invoiceDate = invoice.get("invoiceDate") instanceof Date d ? d : null;
        if (invoiceDate != null && !invoiceDate.after(deliveredAt)) {
            daysToDeliverSum += (deliveredAt.getTime() - invoiceDate.getTime()) / DAY_MS;
            daysToDeliverSamples++;
        }
        Optional<LocalDate> due = DeliveryDates.parse(text(invoice.get("expectedDeliveryDate")));
        if (due.isPresent()) {
            withDueDate++;
            if (!deliveredAt.toInstant().atZone(zone).toLocalDate().isAfter(due.get())) {
                onTime++;
            }
        }
        for (Document item : items(invoice)) {
            List<Object[]> entries = history(item); // [status, Date] sorted
            if (entries.isEmpty() || !"ORDER_ACCEPTED".equals(entries.get(0)[0])) {
                if (invoiceDate != null) {
                    entries.add(0, new Object[]{"ORDER_ACCEPTED", invoiceDate});
                }
            }
            for (int i = 0; i < entries.size(); i++) {
                String status = (String) entries.get(i)[0];
                Date start = (Date) entries.get(i)[1];
                Date end = i + 1 < entries.size() ? (Date) entries.get(i + 1)[1] : deliveredAt;
                String key = i + 1 < entries.size() ? status : "ORDER_COMPLETED".equals(status) ? READY : status;
                double[] bucket = stageHours.get(key);
                if (bucket == null || end.before(start)) {
                    continue;
                }
                bucket[0] += (end.getTime() - start.getTime()) / HOUR_MS;
                bucket[1]++;
            }
        }
    }

    /** Adds another branch's raw figures (for the all-branches total). */
    void merge(ProductionHealth other) {
        openItems += other.openItems;
        overdueItems += other.overdueItems;
        other.stageCounts.forEach((k, v) -> {
            long[] c = stageCounts.get(k);
            c[0] += v[0];
            c[1] += v[1];
        });
        other.stageAges.forEach((k, v) -> {
            double[] a = stageAges.get(k);
            a[0] += v[0];
            a[1] += v[1];
            a[2] = Math.max(a[2], v[2]);
        });
        delivered += other.delivered;
        daysToDeliverSum += other.daysToDeliverSum;
        daysToDeliverSamples += other.daysToDeliverSamples;
        withDueDate += other.withDueDate;
        onTime += other.onTime;
        other.stageHours.forEach((k, v) -> {
            double[] h = stageHours.get(k);
            h[0] += v[0];
            h[1] += v[1];
        });
    }

    ProductionReportResponse.Branch toBranch(String shopId, String shopName) {
        List<ProductionReportResponse.Stage> stages = new ArrayList<>();
        for (String status : STAGES) {
            long[] c = stageCounts.get(status);
            double[] a = stageAges.get(status);
            stages.add(new ProductionReportResponse.Stage(status, c[0], c[1],
                    a[1] > 0 ? round1(a[0] / a[1]) : null, a[1] > 0 ? round1(a[2]) : null));
        }
        List<ProductionReportResponse.StageTime> times = new ArrayList<>();
        stageHours.forEach((status, h) -> {
            if (h[1] > 0) {
                times.add(new ProductionReportResponse.StageTime(status, round1(h[0] / h[1]), (long) h[1]));
            }
        });
        ProductionReportResponse.Turnaround turnaround = new ProductionReportResponse.Turnaround(delivered,
                daysToDeliverSamples > 0 ? round1(daysToDeliverSum / daysToDeliverSamples) : null,
                withDueDate > 0 ? round1(onTime * 100.0 / withDueDate) : null, withDueDate, times);
        return new ProductionReportResponse.Branch(shopId, shopName, openItems, overdueItems, stages, turnaround);
    }

    // ------------------------------------------------------------------ helpers

    private static List<Document> items(Document invoice) {
        List<Document> items = new ArrayList<>();
        if (invoice.get("items") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Document item && !"NoUse".equals(item.get("itemName"))) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** History entries [status, timestamp], oldest first. */
    private static List<Object[]> history(Document item) {
        List<Object[]> entries = new ArrayList<>();
        if (item.get("assignmentHistory") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Document e && e.get("timestamp") instanceof Date at && e.get("action") != null) {
                    entries.add(new Object[]{e.get("action").toString(), at});
                }
            }
        }
        entries.sort(Comparator.comparing(e -> (Date) e[1]));
        return entries;
    }

    /** When the item last entered {@code status}. */
    private static Optional<Date> latestEntry(Document item, String status) {
        Date latest = null;
        for (Object[] entry : history(item)) {
            if (status.equals(entry[0])) {
                latest = (Date) entry[1];
            }
        }
        return Optional.ofNullable(latest);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
