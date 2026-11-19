package com.saritha.fashions.modules.report.service;

import com.saritha.fashions.modules.report.payload.DeletedInvoiceDetailResponse;
import com.saritha.fashions.modules.report.payload.MonthlyFinancialsResponse;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.core.activity.ActivityType;
import com.saritha.fashions.modules.core.date.DeliveryDates;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.report.payload.ActivityDayResponse;
import com.saritha.fashions.modules.report.payload.ActivityResponse;
import com.saritha.fashions.modules.report.payload.DailyFinancialsResponse;
import com.saritha.fashions.modules.report.payload.DeletedInvoiceResponse;
import com.saritha.fashions.modules.report.payload.DeliveryDashboardResponse;
import com.saritha.fashions.modules.report.payload.EmployeeActivitySummaryResponse;
import com.saritha.fashions.modules.report.payload.EmployeeAnalyticsResponse;
import com.saritha.fashions.modules.report.payload.FinancialSummaryResponse;
import com.saritha.fashions.modules.report.payload.FinancialSummaryResponse.MethodTotal;
import com.saritha.fashions.modules.report.payload.MyActivityResponse;
import com.saritha.fashions.modules.report.payload.ShopOverviewResponse;
import com.saritha.fashions.modules.report.repository.ReportRepository;
import com.saritha.fashions.modules.report.repository.ReportRepository.ActivityPage;
import com.saritha.fashions.modules.report.repository.ReportRepository.DayAmount;
import com.saritha.fashions.modules.report.repository.ReportRepository.DeletedPage;
import com.saritha.fashions.modules.report.repository.ReportRepository.EmployeeTypeCount;
import com.saritha.fashions.modules.report.repository.ReportRepository.ExpenseRow;
import com.saritha.fashions.modules.report.repository.ReportRepository.MethodAmount;
import com.saritha.fashions.modules.report.repository.ReportRepository.OpenRow;
import com.saritha.fashions.modules.report.repository.ReportRepository.SalesRow;
import com.saritha.fashions.modules.report.repository.ReportRepository.ShopInfo;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.util.AmountUtils;
import com.saritha.fashions.util.AuthUtil;
import org.bson.Document;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reports for the platform owner and shop owners, plus each employee's own activity.
 * Replaces /v1/api/financials/**, /v1/api/dashboard/**, /v1/api/employee/activities/**,
 * /v1/api/deleted/invoices/** and the legacy shop dashboard rows.
 *
 * Fixes vs the legacy reports:
 * - figures are computed from invoices, payments, expenses and petty cash, not from the manually
 *   filled financial_records collection (whose "income" and "profit" nobody kept up to date);
 * - the shop dashboard's expense totals are real (the legacy aggregation always returned 0) and
 *   "paid" invoices match the stored "Paid" (the legacy one compared with "PAID");
 * - reports are limited to the caller's shop: the legacy endpoints accepted any shopId from any
 *   logged-in user;
 * - deleted-invoice search by customer works (the legacy one searched a field the archive never
 *   had, because the customer is stored as a reference).
 */
@Service
public class ReportReadServiceImpl implements ReportReadService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_SERIES_DAYS = 30;
    private static final int MAX_SERIES_DAYS = 366;

    private final ReportRepository repository;
    private final AuthUtil authUtil;

    public ReportReadServiceImpl(ReportRepository repository, AuthUtil authUtil) {
        this.repository = repository;
        this.authUtil = authUtil;
    }

    // ================================================================ financials

    @Override
    public List<ShopOverviewResponse> getShopsOverview(LocalDate from, LocalDate to) {
        User user = currentUser();
        if (!hasRole(user, AppRole.OWNER)) {
            throw new AccessDeniedException("Only the platform owner can view all shops");
        }
        Period period = period(from, to);
        Map<String, SalesRow> sales = repository.salesByShop(null, period.start(), period.before());
        Map<String, Double> collected = sumByShop(repository.collectionsByShop(null, period.start(), period.before()));
        List<ExpenseRow> expenseRows = repository.expensesByShop(null, period.start(), period.before());
        Map<String, OpenRow> open = repository.openInvoicesByShop(null);
        Map<String, Long> receipts = repository.receiptCountByShop(null, period.start(), period.before());

        List<ShopOverviewResponse> rows = new ArrayList<>();
        for (ShopInfo shop : repository.findShops(null)) {
            String id = shop.shopId();
            SalesRow sale = sales.getOrDefault(id, new SalesRow(0, 0, 0, 0, 0, 0, 0));
            double expenses = expenseRows.stream().filter(row -> id.equals(row.shopId())).mapToDouble(ExpenseRow::amount).sum();
            double cashExpenses = expenseRows.stream()
                    .filter(row -> id.equals(row.shopId()) && "cash".equalsIgnoreCase(row.method()))
                    .mapToDouble(ExpenseRow::amount).sum();
            double money = collected.getOrDefault(id, 0.0);
            OpenRow openRow = open.getOrDefault(id, new OpenRow(0, 0));
            rows.add(new ShopOverviewResponse(id, shop.shopName(), shop.shopType(), shop.ownerName(),
                    shop.employeeCount(), round(shop.pettyCash()), sale.invoiceCount(), round(sale.invoiced()),
                    sale.paidCount(), sale.deliveredCount(), round(money), round(expenses), round(cashExpenses),
                    round(money - expenses), openRow.count(), round(openRow.balanceDue()),
                    receipts.getOrDefault(id, 0L)));
        }
        return rows;
    }

    @Override
    public FinancialSummaryResponse getFinancialSummary(String shopId, LocalDate from, LocalDate to) {
        String scope = resolveShop(shopId);
        Period period = period(from, to);
        Date start = period.start();
        Date before = period.before();

        SalesRow sales = sumSales(repository.salesByShop(scope, start, before).values());
        List<MethodAmount> collections = repository.collectionsByShop(scope, start, before);
        List<ExpenseRow> expenses = repository.expensesByShop(scope, start, before);
        OpenRow open = repository.openInvoicesByShop(scope).values().stream()
                .reduce(new OpenRow(0, 0), (a, b) -> new OpenRow(a.count() + b.count(), a.balanceDue() + b.balanceDue()));
        List<ShopInfo> shops = repository.findShops(scope);
        double pettyCashBalance = shops.stream().mapToDouble(ShopInfo::pettyCash).sum();
        double pettyCashAdded = repository.pettyCashAddedByShop(scope, start, before).values().stream()
                .mapToDouble(Double::doubleValue).sum();
        long receipts = repository.receiptCountByShop(scope, start, before).values().stream().mapToLong(Long::longValue).sum();
        long deleted = repository.deletedInvoiceCountByShop(scope, start, before).values().stream().mapToLong(Long::longValue).sum();

        double collected = collections.stream().mapToDouble(MethodAmount::amount).sum();
        double expenseTotal = expenses.stream().mapToDouble(ExpenseRow::amount).sum();
        double employeeExpenses = expenses.stream().filter(ExpenseRow::employee).mapToDouble(ExpenseRow::amount).sum();

        return new FinancialSummaryResponse(
                scope,
                scope == null || shops.isEmpty() ? null : shops.get(0).shopName(),
                from, to,
                new FinancialSummaryResponse.Sales(sales.invoiceCount(), round(sales.invoiced()), round(sales.taxable()),
                        round(sales.gst()), round(sales.discount())),
                new FinancialSummaryResponse.Collections(round(collected),
                        byMethod(collections.stream().map(row -> new MethodAmount(null, row.method(), row.amount(), row.count())).toList())),
                new FinancialSummaryResponse.Expenses(round(expenseTotal),
                        expenses.stream().mapToLong(ExpenseRow::count).sum(),
                        round(expenseTotal - employeeExpenses), round(employeeExpenses),
                        byMethod(expenses.stream().map(row -> new MethodAmount(null, row.method(), row.amount(), row.count())).toList()),
                        repository.expensesByType(scope, start, before).stream()
                                .map(row -> new FinancialSummaryResponse.CategoryTotal(row.type(), round(row.amount()), row.count()))
                                .toList()),
                new FinancialSummaryResponse.PettyCash(round(pettyCashAdded), round(pettyCashBalance)),
                round(collected - expenseTotal),
                new FinancialSummaryResponse.Outstanding(open.count(), round(open.balanceDue())),
                receipts, deleted);
    }

    @Override
    public List<DailyFinancialsResponse> getDailyFinancials(String shopId, LocalDate from, LocalDate to) {
        String scope = resolveShop(shopId);
        Period period = seriesPeriod(from, to);
        Map<String, DayAmount> sales = repository.salesByDay(scope, period.start(), period.before());
        Map<String, DayAmount> collected = repository.collectionsByDay(scope, period.start(), period.before());
        Map<String, DayAmount> expenses = repository.expensesByDay(scope, period.start(), period.before());

        List<DailyFinancialsResponse> days = new ArrayList<>();
        DayAmount none = new DayAmount(0, 0);
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            String key = day.toString();
            DayAmount sale = sales.getOrDefault(key, none);
            double money = collected.getOrDefault(key, none).amount();
            double spent = expenses.getOrDefault(key, none).amount();
            days.add(new DailyFinancialsResponse(day, sale.count(), round(sale.amount()), round(money), round(spent),
                    round(money - spent)));
        }
        return days;
    }

    /**
     * One row per IST month. Without from: from the first month with any sale, payment or expense; without to: up to
     * the current month. Months without data are listed with zeros.
     */
    @Override
    public List<MonthlyFinancialsResponse> getMonthlyFinancials(String shopId, LocalDate from, LocalDate to) {
        String scope = resolveShop(shopId);
        Period period = period(from, to);
        Map<String, DayAmount> sales = repository.salesBy(scope, period.start(), period.before(), ReportRepository.MONTH);
        Map<String, DayAmount> collected = repository.collectionsBy(scope, period.start(), period.before(), ReportRepository.MONTH);
        Map<String, DayAmount> expenses = repository.expensesBy(scope, period.start(), period.before(), ReportRepository.MONTH);

        java.util.TreeSet<String> months = new java.util.TreeSet<>();
        months.addAll(sales.keySet());
        months.addAll(collected.keySet());
        months.addAll(expenses.keySet());
        if (from == null && months.isEmpty()) {
            return List.of();
        }
        java.time.YearMonth first = from != null ? java.time.YearMonth.from(from) : java.time.YearMonth.parse(months.first());
        java.time.YearMonth last = to != null ? java.time.YearMonth.from(to) : java.time.YearMonth.now(IST);
        if (!months.isEmpty() && to == null && java.time.YearMonth.parse(months.last()).isAfter(last)) {
            last = java.time.YearMonth.parse(months.last()); // data dated in the future (e.g. back-dated forward)
        }

        List<MonthlyFinancialsResponse> rows = new ArrayList<>();
        DayAmount none = new DayAmount(0, 0);
        for (java.time.YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            String key = month.toString();
            DayAmount sale = sales.getOrDefault(key, none);
            double money = collected.getOrDefault(key, none).amount();
            double spent = expenses.getOrDefault(key, none).amount();
            rows.add(new MonthlyFinancialsResponse(key, sale.count(), round(sale.amount()), round(money), round(spent),
                    round(money - spent)));
        }
        return rows;
    }

    // ================================================================ activities

    @Override
    public PaginatedResponse<ActivityResponse> listActivities(String shopId, String employeeId, String type,
                                                              String invoiceId, LocalDate from, LocalDate to,
                                                              int pageNumber, int pageSize) {
        checkPaging(pageNumber, pageSize);
        String scope = resolveShop(shopId);
        Period period = period(from, to);
        ActivityPage page = repository.findActivities(scope, blankToNull(employeeId), activityType(type),
                blankToNull(invoiceId),
                period.start(), period.before(), pageNumber, pageSize);
        List<ActivityResponse> content = page.activities().stream()
                .map(activity -> new ActivityResponse(
                        asString(activity.get("_id")), activity.getString("shopId"), activity.getString("employeeId"),
                        activity.getString("employeeName"), asString(activity.get("type")), activity.getString("invoiceId"),
                        activity.getString("assignedTo"), activity.getString("notes"), activity.getDate("timestamp")))
                .toList();
        return page(content, pageNumber, pageSize, page.total());
    }

    @Override
    public List<EmployeeActivitySummaryResponse> getActivityByEmployee(String shopId, LocalDate from, LocalDate to) {
        String scope = resolveShop(shopId);
        Period period = period(from, to);
        Map<String, List<EmployeeTypeCount>> byEmployee = new LinkedHashMap<>();
        for (EmployeeTypeCount count : repository.activityCountsByEmployee(scope, period.start(), period.before())) {
            byEmployee.computeIfAbsent(String.valueOf(count.employeeId()), key -> new ArrayList<>()).add(count);
        }
        // Prefer the current account name; fall back to the name stored with the activity.
        Map<String, String> names = repository.findUserNames(byEmployee.keySet());

        List<EmployeeActivitySummaryResponse> rows = new ArrayList<>();
        byEmployee.forEach((employeeId, counts) -> {
            Map<String, Long> byType = new TreeMap<>();
            counts.forEach(count -> byType.merge(String.valueOf(count.type()), count.count(), Long::sum));
            Date last = counts.stream().map(EmployeeTypeCount::lastActivityAt).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            String storedName = counts.stream().map(EmployeeTypeCount::employeeName).filter(Objects::nonNull)
                    .findFirst().orElse(null);
            rows.add(new EmployeeActivitySummaryResponse("null".equals(employeeId) ? null : employeeId,
                    names.getOrDefault(employeeId, storedName),
                    byType.values().stream().mapToLong(Long::longValue).sum(), byType, last));
        });
        rows.sort(Comparator.comparingLong(EmployeeActivitySummaryResponse::total).reversed()
                .thenComparing(row -> row.employeeName() == null ? "" : row.employeeName()));
        return rows;
    }

    @Override
    public List<ActivityDayResponse> getActivityDaily(String shopId, String employeeId, LocalDate from, LocalDate to) {
        String scope = resolveShop(shopId);
        Period period = seriesPeriod(from, to);
        return fillDays(period, repository.activityCountsByDay(scope, blankToNull(employeeId), period.start(), period.before()));
    }

    /** Production statuses always listed for SHOP_OWNER analytics (0 when absent), in workflow order. */
    private static final List<String> ITEM_STATUSES = List.of("ORDER_ACCEPTED", "CUTTING", "TAILOR_ASSIGNED", "STITCHING",
            "SHOP_ASSISTANT_ASSIGNED", "HEMMING", "IRONING", "ORDER_COMPLETED", "ORDER_DELIVERED");

    private static final List<String> ANALYTICS_ROLES = List.of("TAILOR_MASTER", "TAILOR", "SHOP_ASSISTANT", "SHOP_OWNER");

    @Override
    public EmployeeAnalyticsResponse getEmployeeAnalytics(String shopId, String employeeId, String role,
                                                          LocalDate from, LocalDate to) {
        String scope = resolveShop(shopId);
        Period period = period(from, to);
        String normalizedRole = blankToNull(role) == null ? null : role.trim().toUpperCase(Locale.ROOT);
        if (normalizedRole == null || !ANALYTICS_ROLES.contains(normalizedRole)) {
            throw new APIException("role must be one of " + ANALYTICS_ROLES);
        }

        if ("SHOP_OWNER".equals(normalizedRole)) {
            Document items = repository.shopItemAnalytics(scope, period.start(), period.before());
            Map<String, Long> statusCounts = new LinkedHashMap<>();
            ITEM_STATUSES.forEach(status -> statusCounts.put(status, 0L));
            @SuppressWarnings("unchecked")
            Map<String, Long> found = (Map<String, Long>) items.get("statusCounts");
            found.forEach((status, count) -> statusCounts.merge(status, count, Long::sum));
            double totalRevenue = round(number(items.get("totalRevenue")));
            long invoiceCount = (long) number(items.get("invoiceCount"));
            long receipts = repository.receiptCountByShop(scope, period.start(), period.before())
                    .values().stream().mapToLong(Long::longValue).sum();
            long deleted = repository.deletedInvoiceCountByShop(scope, period.start(), period.before())
                    .values().stream().mapToLong(Long::longValue).sum();
            return new EmployeeAnalyticsResponse(blankToNull(employeeId), normalizedRole, period.from(), period.to(),
                    null, null, totalRevenue, null, null, null, null, null,
                    statusCounts, round(number(items.get("totalItems"))), invoiceCount,
                    invoiceCount > 0 ? round(totalRevenue / invoiceCount) : 0.0, receipts, deleted);
        }

        String employee = blankToNull(employeeId);
        if (employee == null) {
            throw new APIException("employeeId is required");
        }
        Document row = repository.employeeAnalytics(scope, employee, normalizedRole, period.start(), period.before());
        boolean worker = !"TAILOR_MASTER".equals(normalizedRole);
        return new EmployeeAnalyticsResponse(employee, normalizedRole, period.from(), period.to(),
                (long) number(row.get("assigned")), round(number(row.get("totalQuantity"))),
                round(number(row.get("totalRevenue"))), (long) number(row.get("totalReassignments")),
                round(number(row.get("reassignmentQuantity"))), round(number(row.get("reassignmentRevenue"))),
                worker ? (long) number(row.get("totalAssigned")) : null,
                worker ? (long) number(row.get("totalCompleted")) : null,
                null, null, null, null, null, null);
    }

    @Override
    public MyActivityResponse getMyActivity(LocalDate from, LocalDate to) {
        User user = currentUser();
        boolean allowed = hasRole(user, AppRole.TAILOR_MASTER) || hasRole(user, AppRole.TAILOR)
                || hasRole(user, AppRole.SHOP_ASSISTANT) || hasRole(user, AppRole.SHOP_OWNER);
        if (!allowed) {
            throw new AccessDeniedException("Only shop staff have an activity log");
        }
        Period period = seriesPeriod(from, to);
        Map<String, Long> byType = new TreeMap<>(
                repository.activityCountsByType(null, user.getUserId(), period.start(), period.before()));
        List<ActivityDayResponse> daily = fillDays(period,
                repository.activityCountsByDay(null, user.getUserId(), period.start(), period.before()));
        return new MyActivityResponse(user.getUserId(), period.from(), period.to(),
                byType.values().stream().mapToLong(Long::longValue).sum(), byType, daily);
    }

    // ================================================================ deleted invoices

    @Override
    public DeletedInvoiceDetailResponse getDeletedInvoice(String archiveId, String shopId) {
        String scope = resolveShop(shopId);
        Document archive = repository.findDeletedInvoice(archiveId, scope)
                .orElseThrow(() -> new APIException("Deleted invoice not found"));
        Document invoice = invoiceOf(archive);
        Object customerId = ReportRepository.customerRefId(invoice.get("customer"));
        Document customer = customerId == null ? null
                : repository.findCustomers(Set.of(customerId)).get(customerId.toString());
        Map<String, Object> details = new LinkedHashMap<>();
        invoice.forEach((key, value) -> {
            if (!"_class".equals(key)) {
                details.put("_id".equals(key) ? "id" : key, plain(value));
            }
        });
        Map<String, Object> customerDetails = new LinkedHashMap<>();
        if (customer != null) {
            customerDetails.put("id", asString(customer.get("_id")));
            customerDetails.put("customerId", customer.get("customerId"));
            customerDetails.put("name", customer.get("name"));
            customerDetails.put("phone", customer.get("phone"));
            customerDetails.put("email", customer.get("email"));
        } else if (customerId != null) {
            customerDetails.put("id", customerId.toString());
        }
        details.put("customer", customerDetails.isEmpty() ? null : customerDetails);
        String deletedBy = archive.getString("deletedBy");
        String deletedByName = deletedBy == null ? null : repository.findUserNames(Set.of(deletedBy)).get(deletedBy);
        return new DeletedInvoiceDetailResponse(asString(archive.get("_id")), archive.getDate("deletedAt"), deletedBy,
                deletedByName, archive.getString("reason"), details);
    }

    /** BSON -> plain JSON-friendly values: ObjectId / DBRef ids as strings, nested documents and lists converted. */
    private static Object plain(Object value) {
        if (value instanceof org.bson.types.ObjectId id) {
            return id.toHexString();
        }
        if (value instanceof com.mongodb.DBRef ref) {
            return ref.getId() == null ? null : ref.getId().toString();
        }
        if (value instanceof org.bson.types.Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        if (value instanceof Document document) {
            Map<String, Object> map = new LinkedHashMap<>();
            document.forEach((key, inner) -> {
                if (!"_class".equals(key)) {
                    map.put(key, plain(inner));
                }
            });
            return map;
        }
        if (value instanceof Collection<?> list) {
            return list.stream().map(ReportReadServiceImpl::plain).toList();
        }
        return value;
    }

    @Override
    public PaginatedResponse<DeletedInvoiceResponse> listDeletedInvoices(String shopId, String search, LocalDate from,
                                                                         LocalDate to, int pageNumber, int pageSize) {
        checkPaging(pageNumber, pageSize);
        String text = blankToNull(search);
        if (text != null && text.length() > 100) {
            throw new APIException("search must be at most 100 characters");
        }
        String scope = resolveShop(shopId);
        Period period = period(from, to);
        DeletedPage page = repository.findDeletedInvoices(scope, text, period.start(), period.before(), pageNumber, pageSize);

        Set<Object> customerIds = new HashSet<>();
        Set<String> userIds = new HashSet<>();
        for (Document archive : page.archives()) {
            Document invoice = invoiceOf(archive);
            Object customerId = ReportRepository.customerRefId(invoice.get("customer"));
            if (customerId != null) {
                customerIds.add(customerId);
            }
            if (archive.getString("deletedBy") != null) {
                userIds.add(archive.getString("deletedBy"));
            }
        }
        Map<String, Document> customers = repository.findCustomers(customerIds);
        Map<String, String> names = repository.findUserNames(userIds);

        List<DeletedInvoiceResponse> content = page.archives().stream().map(archive -> {
            Document invoice = invoiceOf(archive);
            Object customerId = ReportRepository.customerRefId(invoice.get("customer"));
            Document customer = customerId == null ? null : customers.get(customerId.toString());
            Object items = invoice.get("items");
            String deletedBy = archive.getString("deletedBy");
            return new DeletedInvoiceResponse(
                    asString(archive.get("_id")), invoice.getString("shopId"), invoice.getString("invoiceId"),
                    invoice.get("invoiceDate") instanceof Date date ? date : null,
                    customer == null ? null : customer.getString("name"),
                    customer == null ? null : customer.getString("phone"),
                    items instanceof Collection<?> list ? list.size() : 0,
                    number(invoice.get("totalAmountWithGST")), number(invoice.get("advancePaid")),
                    number(invoice.get("balanceDue")), invoice.getString("paymentStatus"),
                    archive.getDate("deletedAt"), deletedBy, names.get(deletedBy), archive.getString("reason"));
        }).toList();
        return page(content, pageNumber, pageSize, page.total());
    }

    // ================================================================ deliveries

    private static final String COMPLETED = "ORDER_COMPLETED";

    @Override
    public DeliveryDashboardResponse getDeliveries(String shopId, LocalDate date) {
        String scope = resolveShop(shopId);
        LocalDate day = date != null ? date : LocalDate.now(IST);

        List<Document> open = repository.findOpenInvoices(scope);
        List<Document> receipts = repository.findReceipts(scope,
                Date.from(day.atStartOfDay(IST).toInstant()), Date.from(day.plusDays(1).atStartOfDay(IST).toInstant()));

        // Customers of all rows in one query.
        Set<Object> customerIds = new HashSet<>();
        open.forEach(invoice -> addCustomerId(customerIds, invoice));
        receipts.forEach(receipt -> addCustomerId(customerIds, receipt));
        Map<String, Document> customers = repository.findCustomers(customerIds);

        List<DeliveryDashboardResponse.DeliveryOrder> due = new ArrayList<>();
        List<DeliveryDashboardResponse.DeliveryOrder> overdue = new ArrayList<>();
        int withoutDate = 0;
        for (Document invoice : open) {
            Optional<LocalDate> expected = DeliveryDates.parse(invoice.getString("expectedDeliveryDate"));
            if (expected.isEmpty()) {
                withoutDate++;
                continue;
            }
            LocalDate expectedDay = expected.get();
            if (expectedDay.isEqual(day)) {
                due.add(deliveryOrder(invoice, expectedDay, 0, customers));
            } else if (expectedDay.isBefore(day)) {
                overdue.add(deliveryOrder(invoice, expectedDay, ChronoUnit.DAYS.between(expectedDay, day), customers));
            }
        }
        // Due: ready to hand over first, then oldest invoice. Overdue: most overdue first.
        due.sort(Comparator.comparing((DeliveryDashboardResponse.DeliveryOrder order) -> !order.ready())
                .thenComparing(order -> order.invoiceDate() == null ? new Date(0) : order.invoiceDate()));
        overdue.sort(Comparator.comparing(DeliveryDashboardResponse.DeliveryOrder::expectedDeliveryDate)
                .thenComparing(order -> order.invoiceDate() == null ? new Date(0) : order.invoiceDate()));

        // A receipt keeps the invoice's database id; show and link the invoice number, with the invoice's dates and items.
        Map<String, Document> invoicesOfReceipts = repository.findInvoicesByDbId(
                receipts.stream().map(receipt -> receipt.getString("invoiceId")).toList());
        List<DeliveryDashboardResponse.DeliveredOrder> delivered = receipts.stream().map(receipt -> {
            Document customer = customerOf(receipt, customers);
            String invoiceDbId = receipt.getString("invoiceId");
            Document invoice = invoiceDbId == null ? null : invoicesOfReceipts.get(invoiceDbId);
            String invoiceNumber = invoice != null && invoice.get("invoiceId") != null
                    ? invoice.get("invoiceId").toString() : invoiceDbId;
            List<DeliveryDashboardResponse.DeliveryItem> items = deliveryItems(invoice != null ? invoice : receipt);
            Object expected = (invoice != null ? invoice : receipt).get("expectedDeliveryDate");
            return new DeliveryDashboardResponse.DeliveredOrder(receipt.getString("receiptNo"), invoiceNumber,
                    receipt.getString("shopId"), customer.getString("name"), customer.getString("phone"),
                    receipt.getDate("receiptDate"), round(number(receipt.get("totalAmountWithGST"))),
                    invoice != null && invoice.get("invoiceDate") instanceof Date d ? d : null,
                    expected == null ? null : DeliveryDates.parse(expected.toString()).orElse(null),
                    items.size(), items);
        }).toList();

        int dueReady = (int) due.stream().filter(DeliveryDashboardResponse.DeliveryOrder::ready).count();
        double toCollect = due.stream().mapToDouble(DeliveryDashboardResponse.DeliveryOrder::balanceDue).sum()
                + overdue.stream().mapToDouble(DeliveryDashboardResponse.DeliveryOrder::balanceDue).sum();
        DeliveryDashboardResponse.Summary summary = new DeliveryDashboardResponse.Summary(
                due.size(), dueReady, due.size() - dueReady, overdue.size(), delivered.size(), withoutDate, round(toCollect));
        return new DeliveryDashboardResponse(day, scope, summary, due, overdue, delivered);
    }

    private static DeliveryDashboardResponse.DeliveryOrder deliveryOrder(Document invoice, LocalDate expected,
                                                                         long daysOverdue, Map<String, Document> customers) {
        List<DeliveryDashboardResponse.DeliveryItem> items = deliveryItems(invoice);
        int completed = (int) items.stream().filter(item -> COMPLETED.equals(item.status())).count();
        Document customer = customerOf(invoice, customers);
        return new DeliveryDashboardResponse.DeliveryOrder(invoice.getString("invoiceId"), invoice.getString("shopId"),
                customer.getString("name"), customer.getString("phone"), expected, daysOverdue,
                !items.isEmpty() && completed == items.size(), items.size(), completed, items,
                round(number(invoice.get("totalAmountWithGST"))), round(Math.max(0, number(invoice.get("balanceDue")))),
                invoice.getString("paymentStatus"), invoice.get("invoiceDate") instanceof Date d ? d : null);
    }

    /** Items (name, quantity, status) of an invoice or receipt document. */
    private static List<DeliveryDashboardResponse.DeliveryItem> deliveryItems(Document document) {
        List<DeliveryDashboardResponse.DeliveryItem> items = new ArrayList<>();
        if (document != null && document.get("items") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Document item) {
                    items.add(new DeliveryDashboardResponse.DeliveryItem(item.getString("itemName"),
                            number(item.get("itemQuantity")), asString(item.get("itemOrderStatus"))));
                }
            }
        }
        return items;
    }

    private static void addCustomerId(Set<Object> ids, Document document) {
        Object id = ReportRepository.customerRefId(document.get("customer"));
        if (id != null) {
            ids.add(id);
        }
    }

    private static Document customerOf(Document document, Map<String, Document> customers) {
        Object id = ReportRepository.customerRefId(document.get("customer"));
        Document customer = id == null ? null : customers.get(id.toString());
        return customer == null ? new Document() : customer;
    }

    // ================================================================ access

    /** Shop owner: their own shop. Platform owner: the requested shop (must exist), or all shops (null). */
    private String resolveShop(String requestedShopId) {
        User user = currentUser();
        if (hasRole(user, AppRole.SHOP_OWNER)) {
            return repository.findShopIdOfUser(user.getUserId())
                    .orElseThrow(() -> new APIException("User is not assigned to any shop"));
        }
        if (hasRole(user, AppRole.OWNER)) {
            String shopId = blankToNull(requestedShopId);
            if (shopId != null && !repository.shopExists(shopId)) {
                throw new APIException("Shop Not Found");
            }
            return shopId;
        }
        throw new AccessDeniedException("Only shop owners and the platform owner can view reports");
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required");
        }
        return authUtil.loggedInUser();
    }

    private static boolean hasRole(User user, AppRole role) {
        return user.getRoles() != null && user.getRoles().stream()
                .filter(Objects::nonNull).anyMatch(r -> r.getRoleName() == role);
    }

    // ================================================================ periods

    /** [start of from, start of the day after to) in IST; either side may be open. */
    private record Period(LocalDate from, LocalDate to, Date start, Date before) {
    }

    private static Period period(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new APIException("to must not be before from");
        }
        return new Period(from, to,
                from == null ? null : Date.from(from.atStartOfDay(IST).toInstant()),
                to == null ? null : Date.from(to.plusDays(1).atStartOfDay(IST).toInstant()));
    }

    /** Closed range for day-by-day series: default the last 30 days, at most 366 days. */
    private static Period seriesPeriod(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(IST);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_SERIES_DAYS - 1L);
        if (end.isBefore(start)) {
            throw new APIException("to must not be before from");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_SERIES_DAYS) {
            throw new APIException("A daily report can cover at most " + MAX_SERIES_DAYS + " days");
        }
        return period(start, end);
    }

    private static List<ActivityDayResponse> fillDays(Period period, Map<String, Long> counts) {
        List<ActivityDayResponse> days = new ArrayList<>();
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            days.add(new ActivityDayResponse(day, counts.getOrDefault(day.toString(), 0L)));
        }
        return days;
    }

    // ================================================================ helpers

    private static String activityType(String type) {
        String value = blankToNull(type);
        if (value == null) {
            return null;
        }
        try {
            return ActivityType.valueOf(value.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw new APIException("type must be one of " + java.util.Arrays.toString(ActivityType.values()));
        }
    }

    private static SalesRow sumSales(Collection<SalesRow> rows) {
        return rows.stream().reduce(new SalesRow(0, 0, 0, 0, 0, 0, 0), (a, b) -> new SalesRow(
                a.invoiceCount() + b.invoiceCount(), a.invoiced() + b.invoiced(), a.taxable() + b.taxable(),
                a.gst() + b.gst(), a.discount() + b.discount(), a.paidCount() + b.paidCount(),
                a.deliveredCount() + b.deliveredCount()));
    }

    private static Map<String, Double> sumByShop(List<MethodAmount> rows) {
        Map<String, Double> totals = new LinkedHashMap<>();
        rows.forEach(row -> totals.merge(row.shopId(), row.amount(), Double::sum));
        return totals;
    }

    /** Merges rows of the same method (case-insensitive) across shops; largest first. */
    private static List<MethodTotal> byMethod(List<MethodAmount> rows) {
        Map<String, MethodAmount> merged = new LinkedHashMap<>();
        for (MethodAmount row : rows) {
            merged.merge(row.method().toLowerCase(Locale.ROOT), row, (a, b) ->
                    new MethodAmount(null, a.method(), a.amount() + b.amount(), a.count() + b.count()));
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(MethodAmount::amount).reversed())
                .map(row -> new MethodTotal(row.method(), round(row.amount()), row.count()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Document invoiceOf(Document archive) {
        Object invoice = archive.get("invoice");
        return invoice instanceof Document document ? document : new Document();
    }

    private static <T> PaginatedResponse<T> page(List<T> content, int pageNumber, int pageSize, long total) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PaginatedResponse<>(content, pageNumber, pageSize, total, totalPages, pageNumber >= totalPages - 1);
    }

    private static void checkPaging(int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new APIException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new APIException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static double round(double value) {
        return AmountUtils.round(value);
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
