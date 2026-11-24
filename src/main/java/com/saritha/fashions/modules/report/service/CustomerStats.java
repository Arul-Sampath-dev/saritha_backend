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
 * Customer analytics for the period [start, before) (live invoices; deleted ones are archived elsewhere).
 * Fed either with one summary row per customer summed in MongoDB ({@link #addSummary}, what the report uses), or
 * invoice by invoice ({@link #add}); then {@link #build} works out active / new / returning customers, the repeat
 * rate (all time), months (new vs returning per month), the top 20 by amount in the period, and customers with
 * balance due on undelivered orders.
 */
final class CustomerStats {

    private static final int TOP = 20;
    private static final int DUES = 50;
    private static final double EPSILON = 0.005;

    private final ZoneId zone;
    private final Date start;
    private final Date before;
    private final Map<String, Customer> customers = new HashMap<>();

    private static final class Customer {
        final String id;
        Date first;
        Date last;
        long invoices;
        long inPeriod;
        double spent;
        final Set<YearMonth> months = new HashSet<>();
        final Set<String> shops = new HashSet<>();
        long openOrders;
        double balanceDue;
        LocalDate oldestDue;

        Customer(String id) {
            this.id = id;
        }
    }

    /**
     * @param start  period start (null = from the beginning)
     * @param before period end, exclusive (null = up to now)
     */
    CustomerStats(ZoneId zone, Date start, Date before) {
        this.zone = zone;
        this.start = start;
        this.before = before;
    }

    /** One customer's row from ReportRepository.customerSummaries (null id: skipped). */
    void addSummary(String customerId, Document row) {
        if (customerId == null || !(row.get("first") instanceof Date first) || !(row.get("last") instanceof Date last)) {
            return;
        }
        Customer c = customers.computeIfAbsent(customerId, Customer::new);
        c.first = first;
        c.last = last;
        c.invoices = (long) number(row.get("invoices"));
        c.inPeriod = (long) number(row.get("inPeriod"));
        c.spent = number(row.get("spent"));
        if (row.get("months") instanceof List<?> months) {
            for (Object month : months) {
                if (month instanceof String text) {
                    c.months.add(YearMonth.parse(text));
                }
            }
        }
        if (row.get("shops") instanceof List<?> shops) {
            for (Object shop : shops) {
                if (shop instanceof String text) {
                    c.shops.add(text);
                }
            }
        }
        c.openOrders = (long) number(row.get("openOrders"));
        c.balanceDue = number(row.get("balanceDue"));
        if (row.get("expected") instanceof List<?> expected) {
            for (Object value : expected) {
                Optional<LocalDate> date = DeliveryDates.parse(text(value));
                if (date.isPresent() && (c.oldestDue == null || date.get().isBefore(c.oldestDue))) {
                    c.oldestDue = date.get();
                }
            }
        }
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
        if (inPeriod(date, start, before)) {
            c.inPeriod++;
            c.spent += number(invoice.get("totalAmountWithGST"));
        }
        c.months.add(YearMonth.from(date.toInstant().atZone(zone)));
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
     * @param months  months to show, oldest first (yyyy-MM)
     * @param names   customer id -> {name, phone} (may miss ids)
     */
    CustomerReportResponse build(LocalDate from, LocalDate to, List<YearMonth> months, Map<String, Document> names) {
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
            long inPeriod = c.inPeriod;
            double spent = c.spent;
            YearMonth firstMonth = YearMonth.from(c.first.toInstant().atZone(zone));
            for (YearMonth month : c.months) {
                if (perMonth.containsKey(month)) {
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
    Set<String> idsToName() {
        Set<String> ids = new HashSet<>();
        customers.values().forEach(c -> {
            if (c.openOrders > 0 || c.inPeriod > 0) {
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
