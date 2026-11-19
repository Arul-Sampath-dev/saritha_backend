package com.saritha.fashions.modules.production.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.production.repository.ItemWorkflowRepository.ItemSnapshot;
import com.saritha.fashions.modules.production.service.ProductionReadServiceImpl.QueueEntry;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests of the queue search / due filter / sort (no database). */
class QueueFilterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private static QueueEntry entry(String invoiceId, String item, String customer, String invoiceDay, LocalDate due) {
        Date invoiceDate = invoiceDay == null ? null
                : Date.from(LocalDate.parse(invoiceDay).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
        Document doc = new Document("itemName", item).append("itemUniqueId", item + "_1").append("itemOrderStatus", "CUTTING");
        ItemSnapshot snapshot = new ItemSnapshot("db-" + invoiceId, invoiceId, "shop", "ORDER_ACCEPTED", null,
                invoiceDate, due == null ? null : due.toString(), doc);
        return new QueueEntry(snapshot, due, customer);
    }

    private final List<QueueEntry> entries = List.of(
            entry("SARI-1", "Blouse", "Priya", "2026-11-10", TODAY.minusDays(2)),   // late
            entry("SARI-2", "Kurti", "Anu", "2026-11-12", TODAY),                    // today
            entry("SARI-3", "Saree fall", "Meena", "2026-11-15", TODAY.plusDays(6)), // this week (last day)
            entry("SARI-4", "Lehenga", "Ravi", "2026-11-16", TODAY.plusDays(7)),     // next week
            entry("SARI-5", "Gown", "Kala", "2026-11-17", null));                    // no date

    private List<String> ids(List<QueueEntry> list) {
        return list.stream().map(e -> e.snapshot().invoiceId()).toList();
    }

    @Test
    void parsesParameters() {
        QueueFilter plain = QueueFilter.of(null, "  ", null, "");
        assertTrue(plain.isPlain());
        assertEquals(QueueFilter.Sort.DUE_SOONEST, QueueFilter.of(null, null, "dueSoonest", null).sort());
        assertEquals(QueueFilter.Due.WEEK, QueueFilter.of(null, null, null, "WEEK").due());
        assertFalse(QueueFilter.of(null, "x", null, null).isPlain());
        assertThrows(APIException.class, () -> QueueFilter.of(null, null, "price", null));
        assertThrows(APIException.class, () -> QueueFilter.of(null, null, null, "tomorrow"));
        assertThrows(APIException.class, () -> QueueFilter.of(null, "a".repeat(101), null, null));
    }

    @Test
    void searchMatchesInvoiceCustomerAndItem() {
        assertEquals(List.of("SARI-2"), ids(ProductionReadServiceImpl.matching(entries, QueueFilter.of(null, "kurti", null, null), TODAY)));
        assertEquals(List.of("SARI-3"), ids(ProductionReadServiceImpl.matching(entries, QueueFilter.of(null, "MEENA", null, null), TODAY)));
        assertEquals(List.of("SARI-4"), ids(ProductionReadServiceImpl.matching(entries, QueueFilter.of(null, "sari-4", null, null), TODAY)));
        assertTrue(ProductionReadServiceImpl.matching(entries, QueueFilter.of(null, "nothing", null, null), TODAY).isEmpty());
    }

    @Test
    void dueFilters() {
        assertEquals(List.of("SARI-1"), ids(ProductionReadServiceImpl.matching(entries, QueueFilter.of(null, null, null, "overdue"), TODAY)));
        assertEquals(List.of("SARI-2"), ids(ProductionReadServiceImpl.matching(entries, QueueFilter.of(null, null, null, "today"), TODAY)));
        assertEquals(List.of("SARI-2", "SARI-3"), ids(ProductionReadServiceImpl.matching(entries, QueueFilter.of(null, null, null, "week"), TODAY)));
        assertEquals(List.of("SARI-5"), ids(ProductionReadServiceImpl.matching(entries, QueueFilter.of(null, null, null, "none"), TODAY)));
    }

    @Test
    void sorts() {
        List<QueueEntry> list = new ArrayList<>(entries);
        list.sort(ProductionReadServiceImpl.order(QueueFilter.Sort.NEWEST));
        assertEquals(List.of("SARI-5", "SARI-4", "SARI-3", "SARI-2", "SARI-1"), ids(list));
        list.sort(ProductionReadServiceImpl.order(QueueFilter.Sort.OLDEST));
        assertEquals(List.of("SARI-1", "SARI-2", "SARI-3", "SARI-4", "SARI-5"), ids(list));
        List<QueueEntry> shuffled = new ArrayList<>(List.of(entries.get(4), entries.get(3), entries.get(0), entries.get(2), entries.get(1)));
        shuffled.sort(ProductionReadServiceImpl.order(QueueFilter.Sort.DUE_SOONEST));
        assertEquals(List.of("SARI-1", "SARI-2", "SARI-3", "SARI-4", "SARI-5"), ids(shuffled)); // no date last
    }

    @Test
    void daysOverdue() {
        assertEquals(2, ProductionReadServiceImpl.daysOverdue(TODAY.minusDays(2), TODAY));
        assertEquals(0, ProductionReadServiceImpl.daysOverdue(TODAY, TODAY));
        assertEquals(0, ProductionReadServiceImpl.daysOverdue(null, TODAY));
    }
}
