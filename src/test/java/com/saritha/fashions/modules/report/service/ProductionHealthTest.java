package com.saritha.fashions.modules.report.service;

import com.saritha.fashions.modules.report.payload.ProductionReportResponse;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductionHealthTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final Date NOW = at(2026, 11, 19, 12);

    private static Date at(int y, int m, int d, int h) {
        return Date.from(LocalDateTime.of(y, m, d, h, 0).atZone(IST).toInstant());
    }

    private static Document entry(String action, Date when) {
        return new Document("action", action).append("timestamp", when).append("tailorId", "u1");
    }

    private static Document item(String status, Document... history) {
        return new Document("itemName", "Blouse").append("itemOrderStatus", status).append("assignmentHistory", List.of(history));
    }

    private static Map<String, ProductionReportResponse.Stage> stages(ProductionReportResponse.Branch b) {
        return b.stages().stream().collect(Collectors.toMap(ProductionReportResponse.Stage::status, Function.identity()));
    }

    @Test
    void countsOpenItemsPerStageWithOverdueAndAge() {
        ProductionHealth h = new ProductionHealth(TODAY, NOW, IST);
        h.addOpenInvoice(new Document("invoiceDate", at(2026, 11, 10, 12)).append("expectedDeliveryDate", "2026-11-15")
                .append("items", List.of(
                        item("STITCHING", entry("CUTTING", at(2026, 11, 11, 12)), entry("TAILOR_ASSIGNED", at(2026, 11, 12, 12)),
                                entry("STITCHING", at(2026, 11, 17, 12))),
                        item("ORDER_ACCEPTED"),
                        new Document("itemName", "NoUse").append("itemOrderStatus", "ORDER_ACCEPTED"))));
        h.addOpenInvoice(new Document("invoiceDate", at(2026, 11, 18, 12)).append("expectedDeliveryDate", "2026-12-01")
                .append("items", List.of(item("STITCHING", entry("STITCHING", at(2026, 11, 19, 0))))));

        ProductionReportResponse.Branch b = h.toBranch("s1", "Navalur");
        Map<String, ProductionReportResponse.Stage> s = stages(b);
        assertEquals(3, b.openItems(), "NoUse items are left out");
        assertEquals(2, b.overdueItems());
        assertEquals(2, s.get("STITCHING").items());
        assertEquals(1, s.get("STITCHING").overdue());
        assertEquals(1.3, s.get("STITCHING").avgDaysInStage(), "(2 days + 0.5 day) / 2, rounded");
        assertEquals(2.0, s.get("STITCHING").maxDaysInStage());
        assertEquals(9.0, s.get("ORDER_ACCEPTED").avgDaysInStage(), "no history: since the invoice date");
        assertNull(s.get("HEMMING").avgDaysInStage());
    }

    @Test
    void turnaroundUsesHistoryAndDeliveryDate() {
        ProductionHealth h = new ProductionHealth(TODAY, NOW, IST);
        Document invoice = new Document("invoiceDate", at(2026, 11, 1, 10)).append("expectedDeliveryDate", "05-11-2026")
                .append("items", List.of(item("ORDER_COMPLETED",
                        entry("CUTTING", at(2026, 11, 1, 12)),
                        entry("TAILOR_ASSIGNED", at(2026, 11, 2, 12)),
                        entry("STITCHING", at(2026, 11, 2, 18)),
                        entry("SHOP_ASSISTANT_ASSIGNED", at(2026, 11, 3, 18)),
                        entry("HEMMING", at(2026, 11, 3, 20)),
                        entry("IRONING", at(2026, 11, 3, 22)),
                        entry("ORDER_COMPLETED", at(2026, 11, 4, 10)))));
        h.addDeliveredInvoice(invoice, at(2026, 11, 5, 10));
        // late: delivered after the expected date
        h.addDeliveredInvoice(new Document("invoiceDate", at(2026, 11, 1, 10)).append("expectedDeliveryDate", "2026-11-03")
                .append("items", List.of()), at(2026, 11, 7, 10));

        ProductionReportResponse.Turnaround t = h.toBranch("s1", "Navalur").turnaround();
        assertEquals(2, t.deliveredOrders());
        assertEquals(5.0, t.avgDaysToDeliver(), "(4 + 6) / 2 days");
        assertEquals(50.0, t.onTimePercent());
        Map<String, Double> hours = t.stageTimes().stream()
                .collect(Collectors.toMap(ProductionReportResponse.StageTime::status, ProductionReportResponse.StageTime::avgHours));
        assertEquals(2.0, hours.get("ORDER_ACCEPTED"), "invoice 10:00 -> cutting 12:00");
        assertEquals(24.0, hours.get("CUTTING"));
        assertEquals(6.0, hours.get("TAILOR_ASSIGNED"));
        assertEquals(24.0, hours.get("STITCHING"));
        assertEquals(24.0, hours.get("READY"), "completed -> delivered");
    }

    @Test
    void mergeAddsBranchesTogether() {
        ProductionHealth a = new ProductionHealth(TODAY, NOW, IST);
        a.addOpenInvoice(new Document("items", List.of(item("CUTTING"))));
        ProductionHealth b = new ProductionHealth(TODAY, NOW, IST);
        b.addOpenInvoice(new Document("expectedDeliveryDate", "2026-11-01").append("items", List.of(item("CUTTING"), item("STITCHING"))));
        ProductionHealth total = new ProductionHealth(TODAY, NOW, IST);
        total.merge(a);
        total.merge(b);
        ProductionReportResponse.Branch t = total.toBranch(null, "All branches");
        assertEquals(3, t.openItems());
        assertEquals(2, t.overdueItems());
        assertEquals(2, stages(t).get("CUTTING").items());
    }
}
