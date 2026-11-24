package com.saritha.fashions.modules.report.service;

import com.saritha.fashions.modules.report.payload.CustomerReportResponse;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerStatsTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Date day(int y, int m, int d) {
        return Date.from(LocalDate.of(y, m, d).atStartOfDay(IST).plusHours(11).toInstant());
    }

    private static Document invoice(Date date, double total, double due, String status, String shop, String expected) {
        return new Document("invoiceDate", date).append("totalAmountWithGST", total).append("balanceDue", due)
                .append("orderStatus", status).append("shopId", shop).append("expectedDeliveryDate", expected);
    }

    private static final Date START = Date.from(LocalDate.of(2026, 11, 1).atStartOfDay(IST).toInstant());
    private static final Date BEFORE = Date.from(LocalDate.of(2026, 12, 1).atStartOfDay(IST).toInstant());

    @Test
    void newReturningTopAndDues() {
        CustomerStats stats = new CustomerStats(IST, START, BEFORE);
        // A: first in October, again in November (returning), still owes on the November order
        stats.add("A", invoice(day(2026, 10, 5), 1000, 0, "ORDER_DELIVERED", "s1", "2026-10-10"));
        stats.add("A", invoice(day(2026, 11, 3), 2500, 500, "ORDER_ACCEPTED", "s2", "2026-11-20"));
        // B: first in November (new), two orders there
        stats.add("B", invoice(day(2026, 11, 8), 800, 0, "ORDER_DELIVERED", "s1", null));
        stats.add("B", invoice(day(2026, 11, 15), 700, 300, "STITCHING", "s1", "2026-11-18"));
        // C: only in September (not active in November)
        stats.add("C", invoice(day(2026, 9, 1), 400, 0, "ORDER_DELIVERED", "s1", null));
        // no customer -> skipped
        stats.add(null, invoice(day(2026, 11, 2), 999, 0, "ORDER_DELIVERED", "s1", null));

        CustomerReportResponse r = stats.build(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                List.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)),
                Map.of("A", new Document("name", "Anu").append("phone", "9000000001")));

        assertEquals(3, r.totalCustomers());
        assertEquals(2, r.activeCustomers());
        assertEquals(1, r.newCustomers());
        assertEquals(1, r.returningCustomers());
        assertEquals(66.7, r.repeatRate(), "A and B have 2 invoices, C one");
        assertEquals(2000.0, r.avgSpendPerCustomer(), "(2500 + 1500) / 2");
        assertEquals("A", r.top().get(0).customerId());
        assertEquals("Anu", r.top().get(0).name());
        assertEquals(2, r.top().get(0).branches());
        assertEquals(2, r.customersWithDues());
        assertEquals(800.0, r.totalDue());
        assertEquals("A", r.withDues().get(0).customerId(), "largest due first");
        assertEquals(LocalDate.of(2026, 11, 18), r.withDues().get(1).oldestDue());
        // months: Sep C new · Oct A new · Nov B new, A returning (B counted once although 2 invoices)
        assertEquals(List.of(new CustomerReportResponse.Month("2026-09", 1, 0), new CustomerReportResponse.Month("2026-10", 1, 0),
                new CustomerReportResponse.Month("2026-11", 1, 1)), r.months());
    }

    /** The same customers as MongoDB's customerSummaries rows give the same report. */
    @Test
    void summaryRowsGiveTheSameReport() {
        CustomerStats stats = new CustomerStats(IST, START, BEFORE);
        stats.addSummary("A", new Document("first", day(2026, 10, 5)).append("last", day(2026, 11, 3))
                .append("invoices", 2).append("inPeriod", 1).append("spent", 2500.0)
                .append("months", java.util.Arrays.asList("2026-10", "2026-11")).append("shops", List.of("s1", "s2"))
                .append("openOrders", 1).append("balanceDue", 500.0)
                .append("expected", java.util.Arrays.asList(null, "2026-11-20")));
        stats.addSummary("B", new Document("first", day(2026, 11, 8)).append("last", day(2026, 11, 15))
                .append("invoices", 2).append("inPeriod", 2).append("spent", 1500.0)
                .append("months", List.of("2026-11")).append("shops", List.of("s1"))
                .append("openOrders", 1).append("balanceDue", 300.0)
                .append("expected", java.util.Arrays.asList("2026-11-18", null)));
        stats.addSummary("C", new Document("first", day(2026, 9, 1)).append("last", day(2026, 9, 1))
                .append("invoices", 1).append("inPeriod", 0).append("spent", 0.0)
                .append("months", java.util.Arrays.asList("2026-09", null)).append("shops", List.of("s1"))
                .append("openOrders", 0).append("balanceDue", 0.0).append("expected", java.util.Arrays.asList((Object) null)));
        stats.addSummary(null, new Document("first", day(2026, 11, 2)).append("last", day(2026, 11, 2)));

        CustomerReportResponse r = stats.build(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                List.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), Map.of());

        assertEquals(3, r.totalCustomers());
        assertEquals(2, r.activeCustomers());
        assertEquals(1, r.newCustomers());
        assertEquals(66.7, r.repeatRate());
        assertEquals(2000.0, r.avgSpendPerCustomer());
        assertEquals("A", r.top().get(0).customerId());
        assertEquals(2, r.top().get(0).branches());
        assertEquals(800.0, r.totalDue());
        assertEquals(LocalDate.of(2026, 11, 18), r.withDues().get(1).oldestDue());
        assertEquals(List.of(new CustomerReportResponse.Month("2026-09", 1, 0), new CustomerReportResponse.Month("2026-10", 1, 0),
                new CustomerReportResponse.Month("2026-11", 1, 1)), r.months());
        assertEquals(java.util.Set.of("A", "B"), stats.idsToName());
    }
}
