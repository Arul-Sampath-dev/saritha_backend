package com.saritha.fashions.modules.report.export;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.expense.payload.DeletedExpenseResponse;
import com.saritha.fashions.modules.expense.payload.ExpenseResponse;
import com.saritha.fashions.modules.expense.service.ExpenseReadService;
import com.saritha.fashions.modules.report.export.BranchReportData.Amount;
import com.saritha.fashions.modules.report.export.BranchReportData.Customers;
import com.saritha.fashions.modules.report.export.BranchReportData.Day;
import com.saritha.fashions.modules.report.export.BranchReportData.DeletedExpense;
import com.saritha.fashions.modules.report.export.BranchReportData.DeletedInvoice;
import com.saritha.fashions.modules.report.export.BranchReportData.Delivery;
import com.saritha.fashions.modules.report.export.BranchReportData.Expense;
import com.saritha.fashions.modules.report.export.BranchReportData.Figures;
import com.saritha.fashions.modules.report.export.BranchReportData.GstRow;
import com.saritha.fashions.modules.report.export.BranchReportData.Header;
import com.saritha.fashions.modules.report.export.BranchReportData.ItemSold;
import com.saritha.fashions.modules.report.export.BranchReportData.Order;
import com.saritha.fashions.modules.report.export.BranchReportData.Payment;
import com.saritha.fashions.modules.report.export.BranchReportData.Staff;
import com.saritha.fashions.modules.report.export.BranchReportData.StageNow;
import com.saritha.fashions.modules.report.export.BranchReportData.StageTime;
import com.saritha.fashions.modules.report.export.BranchReportData.TopCustomer;
import com.saritha.fashions.modules.report.export.BranchReportData.TopUp;
import com.saritha.fashions.modules.report.export.BranchReportData.Turnaround;
import com.saritha.fashions.modules.report.payload.CustomerReportResponse;
import com.saritha.fashions.modules.report.payload.DailyFinancialsResponse;
import com.saritha.fashions.modules.report.payload.DeletedInvoiceResponse;
import com.saritha.fashions.modules.report.payload.EmployeeActivitySummaryResponse;
import com.saritha.fashions.modules.report.payload.FinancialSummaryResponse;
import com.saritha.fashions.modules.report.payload.ProductionReportResponse;
import com.saritha.fashions.modules.report.repository.ReportRepository;
import com.saritha.fashions.modules.report.service.ReportReadService;
import com.saritha.fashions.modules.shop.payload.PettyCashHistoryResponse;
import com.saritha.fashions.modules.shop.service.ShopReadService;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.util.AuthUtil;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Builds {@link BranchReportData} for one branch and period. The figures come from the existing report services
 * (financial summary, daily series, production, customers, staff activity, deleted invoices) and the expense /
 * petty cash services, so the report shows the same numbers as the screens. The lists that no screen loads whole
 * (orders with GST, payments, deliveries, items sold) come from {@link ReportRepository}.
 *
 * Access is the reports' rule: a SHOP_OWNER always gets their own shop; the platform OWNER must pass shopId.
 */
@Service
public class BranchReportService {

    /** Longest period (same limit as the daily series). */
    public static final int MAX_DAYS = 366;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int PAGE = 100;

    private static final Map<String, String> STAGE_LABEL = Map.of(
            "ORDER_ACCEPTED", "Item accepted", "CUTTING", "Cutting", "TAILOR_ASSIGNED", "Tailor assigned",
            "STITCHING", "Stitching", "SHOP_ASSISTANT_ASSIGNED", "Shop assistant assigned", "HEMMING", "Hemming",
            "IRONING", "Ironing", "ORDER_COMPLETED", "Item completed", "READY", "Ready → delivered");
    private static final Map<String, String> ROLE_LABEL = Map.of(
            "SHOP_OWNER", "Shop owner", "TAILOR_MASTER", "Tailor master", "TAILOR", "Tailor",
            "SHOP_ASSISTANT", "Shop assistant", "OWNER", "Platform owner");
    private static final List<String> ROLE_ORDER = List.of("SHOP_OWNER", "TAILOR_MASTER", "TAILOR", "SHOP_ASSISTANT", "OWNER");

    private final ReportReadService reports;
    private final ReportRepository repository;
    private final ExpenseReadService expenses;
    private final ShopReadService shops;
    private final AuthUtil authUtil;

    public BranchReportService(ReportReadService reports, ReportRepository repository, ExpenseReadService expenses,
                               ShopReadService shops, AuthUtil authUtil) {
        this.reports = reports;
        this.repository = repository;
        this.expenses = expenses;
        this.shops = shops;
        this.authUtil = authUtil;
    }

    /** Validates the period; returns the number of days (inclusive). */
    public static long checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new APIException("from and to are required (yyyy-MM-dd); for one day send the same date twice");
        }
        if (to.isBefore(from)) {
            throw new APIException("to must not be before from");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new APIException("A report can cover at most " + MAX_DAYS + " days");
        }
        return days;
    }

    public BranchReportData build(String shopId, LocalDate from, LocalDate to) {
        long days = checkPeriod(from, to);
        // the summary resolves the shop with the reports' access rule (and fails for other roles)
        FinancialSummaryResponse summary = reports.getFinancialSummary(shopId, from, to);
        String scope = summary.shopId();
        if (scope == null) {
            throw new APIException("shopId is required: a report covers one branch");
        }
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);
        FinancialSummaryResponse previous = reports.getFinancialSummary(scope, previousFrom, previousTo);

        Date start = Date.from(from.atStartOfDay(IST).toInstant());
        Date before = Date.from(to.plusDays(1).atStartOfDay(IST).toInstant());
        Date now = Date.from(LocalDate.now(IST).plusDays(1).atStartOfDay(IST).toInstant());

        Document shop = repository.findShopProfile(scope).orElseThrow(() -> new APIException("Shop Not Found"));
        Header header = new Header(scope, shop.getString("shopName"), shop.getString("shopAddress"),
                shop.getString("shopContactNumber"), shop.getString("gstNumber"), shop.getString("ownerName"),
                from, to, previousFrom, previousTo, LocalDateTime.now(IST), currentUserName());

        // raw lists + the customers / invoice numbers they point to
        List<Document> invoices = repository.findInvoicesInPeriod(scope, start, before);
        List<Document> paymentRows = repository.findPaymentsInPeriod(scope, start, before);
        List<Document> slips = repository.findPaymentReceipts(scope, start, before);
        List<Document> receiptsInPeriod = repository.findReceipts(scope, start, before);
        // deliveries up to today (an order of the period may be delivered after it)
        List<Document> receiptsSince = repository.findReceipts(scope, start, now);

        List<Object> customerIds = new ArrayList<>();
        invoices.forEach(d -> addCustomer(customerIds, d));
        paymentRows.forEach(d -> addCustomer(customerIds, d));
        receiptsInPeriod.forEach(d -> addCustomer(customerIds, d));
        Map<String, Document> customers = repository.findCustomers(customerIds);
        List<String> receiptInvoiceIds = receiptsInPeriod.stream().map(r -> r.getString("invoiceId")).toList();
        Map<String, Document> receiptInvoices = repository.findInvoicesByDbId(receiptInvoiceIds);

        ProductionReportResponse production = reports.getProduction(scope, from, to);
        ProductionReportResponse.Branch branch = production.total();
        CustomerReportResponse customerReport = reports.getCustomers(scope, from, to);

        return new BranchReportData(
                header,
                figures(summary),
                figures(previous),
                gstRows(invoices),
                summary.collections().byPaymentMethod().stream()
                        .map(m -> new Amount(blank(m.paymentMethod(), "Not recorded"), m.count(), m.amount())).toList(),
                summary.expenses().byType().stream()
                        .map(c -> new Amount(blank(c.expenseType(), "Uncategorised"), c.count(), c.amount())).toList(),
                days(scope, from, to),
                orders(invoices, customers, receiptsSince),
                payments(paymentRows, slips, customers),
                deliveries(receiptsInPeriod, receiptInvoices, customers),
                expenseList(scope, from, to),
                deletedExpenses(scope, from, to),
                topUps(scope, from, to),
                branch == null ? List.of() : branch.stages().stream()
                        .map(s -> new StageNow(stageLabel(s.status()), s.items(), s.overdue(), s.avgDaysInStage())).toList(),
                branch == null || branch.turnaround() == null ? new Turnaround(0, null, null, 0)
                        : new Turnaround(branch.turnaround().deliveredOrders(), branch.turnaround().avgDaysToDeliver(),
                        branch.turnaround().onTimePercent(), branch.turnaround().withDueDate()),
                branch == null || branch.turnaround() == null ? List.of() : branch.turnaround().stageTimes().stream()
                        .map(t -> new StageTime(stageLabel(t.status()), t.avgHours(), t.samples())).toList(),
                staff(scope, from, to),
                new Customers(customerReport.activeCustomers(), customerReport.newCustomers(),
                        customerReport.returningCustomers(), customerReport.repeatRate(), customerReport.avgSpendPerCustomer()),
                customerReport.top().stream().limit(10)
                        .map(c -> new TopCustomer(c.name(), c.phone(), c.invoices(), c.spent(), c.lastVisit())).toList(),
                deletedInvoices(scope, from, to),
                itemsSold(invoices));
    }

    // ================================================================ sections

    private static Figures figures(FinancialSummaryResponse s) {
        long payments = s.collections().byPaymentMethod().stream().mapToLong(FinancialSummaryResponse.MethodTotal::count).sum();
        return new Figures(s.sales().invoiceCount(), s.sales().invoicedAmount(), s.sales().taxableAmount(),
                s.sales().gstAmount(), s.sales().discount(), s.collections().total(), payments,
                s.expenses().total(), s.expenses().count(), s.expenses().shopExpenses(), s.expenses().employeeExpenses(),
                s.netCashFlow(), s.receiptCount(), s.outstanding().openInvoiceCount(), s.outstanding().balanceDue(),
                s.pettyCash().added(), s.pettyCash().currentBalance(), s.deletedInvoiceCount());
    }

    /** Intra-state (CGST + SGST) and inter-state (IGST) totals of the invoices dated in the period. */
    static List<GstRow> gstRows(List<Document> invoices) {
        long intraCount = 0;
        long interCount = 0;
        double[] intra = new double[4];
        double[] inter = new double[4];
        for (Document invoice : invoices) {
            double igst = number(invoice.get("igstAmount"));
            double[] target = igst > 0 ? inter : intra;
            if (igst > 0) {
                interCount++;
            } else {
                intraCount++;
            }
            target[0] += number(invoice.get("taxableAmount"));
            target[1] += number(invoice.get("cgstAmount"));
            target[2] += number(invoice.get("sgstAmount"));
            target[3] += igst;
        }
        return List.of(
                new GstRow("Intra-state (CGST + SGST)", intraCount, round(intra[0]), round(intra[1]), round(intra[2]), 0,
                        round(intra[0] + intra[1] + intra[2])),
                new GstRow("Inter-state (IGST)", interCount, round(inter[0]), 0, 0, round(inter[3]), round(inter[0] + inter[3])));
    }

    private List<Day> days(String shopId, LocalDate from, LocalDate to) {
        List<Day> days = new ArrayList<>();
        for (DailyFinancialsResponse d : reports.getDailyFinancials(shopId, from, to)) {
            days.add(new Day(d.date(), d.invoiceCount(), d.invoicedAmount(), d.collected(), d.expenses(), d.netCashFlow()));
        }
        return days;
    }

    private static List<Order> orders(List<Document> invoices, Map<String, Document> customers, List<Document> receipts) {
        Map<String, Date> deliveredAt = new HashMap<>();
        receipts.forEach(r -> deliveredAt.put(r.getString("invoiceId"), r.getDate("receiptDate")));
        List<Order> orders = new ArrayList<>();
        for (Document invoice : invoices) {
            List<Document> items = items(invoice);
            int done = (int) items.stream()
                    .filter(i -> "ORDER_COMPLETED".equals(i.getString("itemOrderStatus")) || "ORDER_DELIVERED".equals(i.getString("itemOrderStatus")))
                    .count();
            Date delivered = deliveredAt.get(invoice.get("_id").toString());
            String stage = delivered != null || "ORDER_DELIVERED".equals(invoice.getString("orderStatus")) ? "Delivered"
                    : !items.isEmpty() && done == items.size() ? "Ready"
                    : items.stream().anyMatch(i -> !"ORDER_ACCEPTED".equals(i.getString("itemOrderStatus"))) ? "In production" : "New";
            StringBuilder names = new StringBuilder();
            for (Document item : items) {
                if (!names.isEmpty()) {
                    names.append(", ");
                }
                names.append(blank(item.getString("itemName"), "Item"));
                double qty = number(item.get("itemQuantity"));
                if (qty != 1) {
                    names.append(" ×").append(qty == Math.rint(qty) ? String.valueOf((long) qty) : String.valueOf(qty));
                }
            }
            Document customer = customerOf(invoice, customers);
            double gst = number(invoice.get("cgstAmount")) + number(invoice.get("sgstAmount")) + number(invoice.get("igstAmount"));
            double total = number(invoice.get("totalAmountWithGST"));
            double balance = number(invoice.get("balanceDue"));
            orders.add(new Order(invoice.getString("invoiceId"), invoice.getDate("invoiceDate"), name(customer), phone(customer),
                    done, items.size(), names.toString(), number(invoice.get("taxableAmount")), round(gst), total,
                    round(total - balance), balance, blank(invoice.getString("paymentStatus"), "—"), stage,
                    stringOf(invoice.get("expectedDeliveryDate")), delivered));
        }
        return orders;
    }

    /** Payment entries of the period, with their payment receipt (number, transaction id, who took it) when there is one. */
    private static List<Payment> payments(List<Document> rows, List<Document> slips, Map<String, Document> customers) {
        Map<String, Document> slipByKey = new HashMap<>();
        for (Document slip : slips) {
            Date paidAt = slip.getDate("paidAt");
            if (paidAt != null) {
                slipByKey.putIfAbsent(slip.getString("invoiceId") + "|" + paidAt.getTime(), slip);
            }
        }
        List<Payment> payments = new ArrayList<>();
        for (Document row : rows) {
            Date paidAt = row.getDate("paidAt");
            Document slip = paidAt == null ? null : slipByKey.get(row.getString("invoiceId") + "|" + paidAt.getTime());
            if (slip == null && paidAt != null) {
                slip = slipByKey.get(row.getString("invoiceDbId") + "|" + paidAt.getTime());
            }
            payments.add(new Payment(paidAt, slip == null ? null : slip.getString("paymentReceiptNo"), row.getString("invoiceId"),
                    name(customerOf(row, customers)), blank(row.getString("paidVia"), "Not recorded"),
                    slip == null ? null : slip.getString("transactionId"), number(row.get("amount")),
                    slip == null ? null : slip.getString("createdByName")));
        }
        return payments;
    }

    private static List<Delivery> deliveries(List<Document> receipts, Map<String, Document> invoices, Map<String, Document> customers) {
        List<Delivery> deliveries = new ArrayList<>();
        for (Document receipt : receipts) {
            Document invoice = invoices.get(receipt.getString("invoiceId"));
            String invoiceNo = invoice != null && invoice.get("invoiceId") != null ? invoice.get("invoiceId").toString() : receipt.getString("invoiceId");
            deliveries.add(new Delivery(receipt.getString("receiptNo"), receipt.getDate("receiptDate"), invoiceNo,
                    name(customerOf(receipt, customers)), number(receipt.get("totalAmountWithGST")),
                    blank(receipt.getString("paymentMethod"), "—")));
        }
        return deliveries;
    }

    private List<Expense> expenseList(String shopId, LocalDate from, LocalDate to) {
        return allPages(page -> expenses.listExpenses(shopId, from, to, null, null, null, null, null, "oldest", page, PAGE)).stream()
                .map(e -> new Expense(e.getExpenseDate(), blank(e.getExpenseType(), "Uncategorised"),
                        e.getUserId() == null || e.getUserId().isBlank() ? "Shop" : blank(e.getEmployeeName(), "Employee"),
                        blank(e.getPaymentMethod(), "—"), e.getNotes(), e.getExpenseAmount()))
                .toList();
    }

    private List<DeletedExpense> deletedExpenses(String shopId, LocalDate from, LocalDate to) {
        List<DeletedExpenseResponse> all = allPages(page -> expenses.listDeletedExpenses(shopId, from, to, null, null, page, PAGE));
        return all.stream()
                .map(e -> new DeletedExpense(e.expenseDate(), blank(e.expenseType(), "Uncategorised"), e.expenseAmount(),
                        e.deletedAt(), blank(e.deletedByName(), "—"), e.reason()))
                .toList();
    }

    private List<BranchReportData.TopUp> topUps(String shopId, LocalDate from, LocalDate to) {
        List<PettyCashHistoryResponse> all = allPages(page -> shops.getPettyCashHistory(shopId, from, to, null, "oldest", page, PAGE));
        return all.stream().map(t -> new TopUp(t.addedAt(), blank(t.addedByName(), "—"), t.amount())).toList();
    }

    /** One row per person with work in the period: role, the counts per activity type (largest first), last activity. */
    private List<Staff> staff(String shopId, LocalDate from, LocalDate to) {
        List<EmployeeActivitySummaryResponse> rows = reports.getActivityByEmployee(shopId, from, to);
        Map<String, List<String>> roles = repository.findUserRoles(rows.stream().map(EmployeeActivitySummaryResponse::employeeId).toList());
        List<Staff> staff = new ArrayList<>();
        for (EmployeeActivitySummaryResponse row : rows) {
            String role = roles.getOrDefault(row.employeeId(), List.of()).stream()
                    .filter(ROLE_LABEL::containsKey).min(Comparator.comparingInt(ROLE_ORDER::indexOf)).orElse(null);
            StringBuilder work = new StringBuilder();
            row.countsByType().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> {
                        if (!work.isEmpty()) {
                            work.append(" · ");
                        }
                        work.append(e.getValue()).append(' ').append(humanize(e.getKey()).toLowerCase());
                    });
            staff.add(new Staff(blank(row.employeeName(), "—"), role == null ? "—" : ROLE_LABEL.get(role), work.toString(),
                    row.total(), row.lastActivityAt()));
        }
        staff.sort(Comparator.comparing((Staff s) -> roleRank(s.role())).thenComparing(Staff::name, String.CASE_INSENSITIVE_ORDER));
        return staff;
    }

    private List<DeletedInvoice> deletedInvoices(String shopId, LocalDate from, LocalDate to) {
        List<DeletedInvoiceResponse> all = allPages(page -> reports.listDeletedInvoices(shopId, null, from, to, page, PAGE));
        return all.stream()
                .map(d -> new DeletedInvoice(d.invoiceId(), d.invoiceDate(), d.customerName(), d.totalAmountWithGST(),
                        d.deletedAt(), blank(d.deletedByName(), "—"), d.reason()))
                .toList();
    }

    /** Pieces and amount (before GST) per item name, largest amount first. */
    static List<ItemSold> itemsSold(List<Document> invoices) {
        Map<String, double[]> byName = new LinkedHashMap<>();
        for (Document invoice : invoices) {
            for (Document item : items(invoice)) {
                String name = blank(item.getString("itemName"), "Item").trim();
                double qty = number(item.get("itemQuantity"));
                double[] sum = byName.computeIfAbsent(name, k -> new double[2]);
                sum[0] += qty;
                sum[1] += qty * number(item.get("itemPrice"));
            }
        }
        return byName.entrySet().stream()
                .map(e -> new ItemSold(e.getKey(), e.getValue()[0], round(e.getValue()[1])))
                .sorted(Comparator.comparingDouble(ItemSold::amount).reversed())
                .toList();
    }

    // ================================================================ helpers

    private static <T> List<T> allPages(IntFunction<PaginatedResponse<T>> load) {
        List<T> all = new ArrayList<>();
        for (int page = 0; page < 10_000; page++) {
            PaginatedResponse<T> response = load.apply(page);
            if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                break;
            }
            all.addAll(response.getContent());
            if (response.isLastPage()) {
                break;
            }
        }
        return all;
    }

    private String currentUserName() {
        try {
            return blank(authUtil.loggedInUser().getName(), "—");
        } catch (RuntimeException exception) {
            return "—";
        }
    }

    private static int roleRank(String label) {
        for (int i = 0; i < ROLE_ORDER.size(); i++) {
            if (ROLE_LABEL.get(ROLE_ORDER.get(i)).equals(label)) {
                return i;
            }
        }
        return ROLE_ORDER.size();
    }

    @SuppressWarnings("unchecked")
    private static List<Document> items(Document invoice) {
        return invoice.get("items") instanceof List<?> list
                ? list.stream().filter(Document.class::isInstance).map(Document.class::cast).toList()
                : List.of();
    }

    private static void addCustomer(List<Object> ids, Document document) {
        Object id = ReportRepository.customerRefId(document.get("customer"));
        if (id != null) {
            ids.add(id);
        }
    }

    private static Document customerOf(Document document, Map<String, Document> customers) {
        Object id = ReportRepository.customerRefId(document.get("customer"));
        return id == null ? null : customers.get(id.toString());
    }

    private static String name(Document customer) {
        return customer == null ? "—" : blank(customer.getString("name"), "—");
    }

    private static String phone(Document customer) {
        return customer == null ? null : customer.getString("phone");
    }

    static String stageLabel(String status) {
        return STAGE_LABEL.getOrDefault(status, humanize(status));
    }

    static String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String s = value.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String stringOf(Object value) {
        return value == null ? null : value.toString();
    }

    static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
