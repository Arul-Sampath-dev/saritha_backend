package com.saritha.fashions.modules.report.service;

import com.saritha.fashions.modules.core.date.DeliveryDates;
import com.saritha.fashions.modules.report.payload.CustomerReportResponse;
import org.bson.Document;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Customer analytics from raw invoice documents (live invoices; deleted ones are archived elsewhere).
 * Each invoice adds to its customer (the id inside the customer DBRef); then {@link #build} works out, for the period
 * [start, before): active / new / returning customers, the repeat rate (all time), months (new vs returning per
 * month), the top 20 by amount in the period, and customers with balance due on undelivered orders.
 */
final class CustomerStats {

    private static final int TOP = 20;
    private static final int DUES = 50;
    private static final double EPSILON = 0.005;

    private final ZoneId zone;
    private final Map<String, Customer> customers = new HashMap<>();

    private static final class Customer {
        final String id;
        Date first;
        Date last;
        long invoices;
        final List<Object[]> bills = new ArrayList<>(); // [Date, amount]
        final Set<String> shops = new HashSet<>();
        long openOrders;
        double balanceDue;
        LocalDate oldestDue;

        Customer(String id) {
            this.id = id;
        }
    }

    CustomerStats(ZoneId zone) {
        this.zone = zone;
    }

    /** customerId = the id inside the invoice's customer DBRef (null: the invoice is skipped). */
    void add(String customerId, Document invoice) {
        if (customerId == null || !(invoice.get("invoiceDate") instanceof Date date)) {
            return;
        }
        Customer c = customers.computeIfAbsent(customerId, Customer::new);
        c.invoices++;
        if (c.first == null || date.before(c.first)) {
            c.first = date;
        }
        if (c.last == null || date.after(c.last)) {
            c.last = date;
        }
        c.bills.add(new Object[]{date, number(invoice.get("totalAmountWithGST"))});
        if (invoice.getString("shopId") != null) {
            c.shops.add(invoice.getString("shopId"));
        }
        double due = number(invoice.get("balanceDue"));
        if (!"ORDER_DELIVERED".equals(invoice.getString("orderStatus")) && due > EPSILON) {
            c.openOrders++;
            c.balanceDue += due;
            Optional<LocalDate> expected = DeliveryDates.parse(text(invoice.get("expectedDeliveryDate")));
            if (expected.isPresent() && (c.oldestDue == null || expected.get().isBefore(c.oldestDue))) {
                c.oldestDue = expected.get();
            }
        }
    }

    /**
     * @param start   period start (null = from the beginning)
     * @param before  period end, exclusive (null = up to now)
     * @param months  months to show, oldest first (yyyy-MM)
     * @param names   customer id -> {name, phone} (may miss ids)
     */
    CustomerReportResponse build(LocalDate from, LocalDate to, Date start, Date before, List<YearMonth> months,
                                 Map<String, Document> names) {
        long active = 0;
        long fresh = 0;
        long repeaters = 0;
        double invoicedInPeriod = 0;
        Map<YearMonth, long[]> perMonth = new LinkedHashMap<>(); // [new, returning]
        months.forEach(m -> perMonth.put(m, new long[2]));
        List<CustomerReportResponse.TopCustomer> top = new ArrayList<>();
        List<CustomerReportResponse.CustomerDue> dues = new ArrayList<>();
        long withDues = 0;
        double totalDue = 0;

        for (Customer c : customers.values()) {
            if (c.invoices >= 2) {
                repeaters++;
            }
            long inPeriod = 0;
            double spent = 0;
            Set<YearMonth> seenMonths = new HashSet<>();
            for (Object[] bill : c.bills) {
                Date at = (Date) bill[0];
                if (inPeriod(at, start, before)) {
                    inPeriod++;
                    spent += (double) bill[1];
                }
                YearMonth month = YearMonth.from(at.toInstant().atZone(zone));
                if (perMonth.containsKey(month) && seenMonths.add(month)) {
                    YearMonth firstMonth = YearMonth.from(c.first.toInstant().atZone(zone));
                    perMonth.get(month)[firstMonth.equals(month) ? 0 : 1]++;
                }
            }
            if (inPeriod > 0) {
                active++;
                invoicedInPeriod += spent;
                if (start == null || !c.first.before(start)) {
                    fresh++;
                }
                top.add(new CustomerReportResponse.TopCustomer(c.id, name(names, c.id), phone(names, c.id), inPeriod,
                        round(spent), c.invoices, c.last, c.shops.size()));
            }
            if (c.openOrders > 0) {
                withDues++;
                totalDue += c.balanceDue;
                dues.add(new CustomerReportResponse.CustomerDue(c.id, name(names, c.id), phone(names, c.id), c.openOrders,
                        round(c.balanceDue), c.oldestDue));
            }
        }
        top.sort(Comparator.comparingDouble(CustomerReportResponse.TopCustomer::spent).reversed()
                .thenComparing(CustomerReportResponse.TopCustomer::invoices, Comparator.reverseOrder()));
        dues.sort(Comparator.comparingDouble(CustomerReportResponse.CustomerDue::balanceDue).reversed());
        List<CustomerReportResponse.Month> monthRows = new ArrayList<>();
        perMonth.forEach((m, v) -> monthRows.add(new CustomerReportResponse.Month(m.toString(), v[0], v[1])));

        long total = customers.size();
        return new CustomerReportResponse(from, to, total, active, fresh, active - fresh,
                total == 0 ? 0 : round1(repeaters * 100.0 / total), active == 0 ? 0 : round(invoicedInPeriod / active),
                monthRows, top.subList(0, Math.min(TOP, top.size())), dues.subList(0, Math.min(DUES, dues.size())),
                withDues, round(totalDue));
    }

    /** Ids of the customers to name (top + dues), so only those are loaded. */
    Set<String> idsToName(Date start, Date before) {
        Set<String> ids = new HashSet<>();
        customers.values().forEach(c -> {
            if (c.openOrders > 0 || c.bills.stream().anyMatch(b -> inPeriod((Date) b[0], start, before))) {
                ids.add(c.id);
            }
        });
        return ids;
    }

    private static boolean inPeriod(Date at, Date start, Date before) {
        return (start == null || !at.before(start)) && (before == null || at.before(before));
    }

    private static String name(Map<String, Document> names, String id) {
        Document d = names.get(id);
        return d == null ? null : d.getString("name");
    }

    private static String phone(Map<String, Document> names, String id) {
        Document d = names.get(id);
        return d == null ? null : d.getString("phone");
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
