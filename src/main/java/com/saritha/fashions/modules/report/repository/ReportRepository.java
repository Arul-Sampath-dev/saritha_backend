package com.saritha.fashions.modules.report.repository;

import com.saritha.fashions.modules.core.payment.PaymentMethods;
import com.mongodb.DBRef;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Read-only aggregations for the reporting module. Nothing here writes.
 *
 * Every report is computed from the source collections (invoice, expenses, shops, receipt,
 * employee_activities, deleted_invoices) at request time, so the numbers always match the
 * data. The legacy reports read "financial_records", which was only filled by a manual
 * endpoint, and the legacy shop dashboard dropped the expense fields in its $lookup, so its
 * expense totals were always 0.
 *
 * Most aggregations group by shop, so one query serves both a single shop (shopId given) and
 * all shops (shopId null). Date ranges are [from, before): "before" is the start of the day
 * after the last day asked for.
 */
@Repository
public class ReportRepository {

    private static final String INVOICES = "invoice";
    private static final String EXPENSES = "expenses";
    private static final String SHOPS = "shops";
    private static final String RECEIPTS = "receipt";
    private static final String ACTIVITIES = "employee_activities";
    private static final String DELETED_INVOICES = "deleted_invoices";
    private static final String CUSTOMERS = "customers";
    private static final String USERS = "users";
    private static final String DELIVERED = "ORDER_DELIVERED";
    private static final String IST = "Asia/Kolkata";
    private static final int MAX_CUSTOMER_MATCHES = 1000;

    private final MongoTemplate mongoTemplate;

    public ReportRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // ================================================================ shops

    public boolean shopExists(String shopId) {
        return mongoTemplate.exists(new Query(Criteria.where("_id").is(toId(shopId))), SHOPS);
    }

    /** The shop whose employeeIds contains the user. */
    public Optional<String> findShopIdOfUser(String userId) {
        Query query = new Query(Criteria.where("employeeIds").is(userId)).limit(1);
        query.fields().include("_id");
        Document shop = mongoTemplate.findOne(query, Document.class, SHOPS);
        return Optional.ofNullable(shop).map(found -> found.get("_id").toString());
    }

    /** Basic info of one shop (shopId given) or all shops, sorted by name. */
    public List<ShopInfo> findShops(String shopId) {
        Query query = shopId == null ? new Query() : new Query(Criteria.where("_id").is(toId(shopId)));
        query.with(Sort.by("shopName"));
        query.fields().include("_id").include("shopName").include("shopType").include("ownerName")
                .include("pettyCash").include("employeeIds");
        List<ShopInfo> shops = new ArrayList<>();
        for (Document shop : mongoTemplate.find(query, Document.class, SHOPS)) {
            Object employees = shop.get("employeeIds");
            shops.add(new ShopInfo(shop.get("_id").toString(), shop.getString("shopName"), shop.getString("shopType"),
                    shop.getString("ownerName"), number(shop.get("pettyCash")),
                    employees instanceof Collection<?> list ? list.size() : 0));
        }
        return shops;
    }

    /** Petty cash added (top-ups) per shop in the period. */
    public Map<String, Double> pettyCashAddedByShop(String shopId, Date from, Date before) {
        List<Document> pipeline = new ArrayList<>();
        if (shopId != null) {
            pipeline.add(new Document("$match", new Document("_id", toId(shopId))));
        }
        pipeline.add(new Document("$unwind", "$allPettyCashDetails"));
        Document range = range(from, before);
        if (!range.isEmpty()) {
            pipeline.add(new Document("$match", new Document("allPettyCashDetails.addingDate", range)));
        }
        pipeline.add(new Document("$group", new Document("_id", "$_id")
                .append("added", new Document("$sum", "$allPettyCashDetails.amount"))));
        Map<String, Double> added = new HashMap<>();
        for (Document row : aggregate(SHOPS, pipeline)) {
            added.put(String.valueOf(row.get("_id")), number(row.get("added")));
        }
        return added;
    }

    // ================================================================ invoices / payments

    /** Invoices dated in the period, per shop. */
    public Map<String, SalesRow> salesByShop(String shopId, Date from, Date before) {
        List<Document> pipeline = List.of(
                new Document("$match", match("shopId", shopId, "invoiceDate", from, before)),
                new Document("$group", new Document("_id", "$shopId")
                        .append("invoiceCount", new Document("$sum", 1))
                        .append("invoiced", new Document("$sum", "$totalAmountWithGST"))
                        .append("taxable", new Document("$sum", "$taxableAmount"))
                        .append("gst", new Document("$sum", new Document("$add", List.of(
                                ifNull("$cgstAmount", 0), ifNull("$sgstAmount", 0), ifNull("$igstAmount", 0)))))
                        .append("discount", new Document("$sum", "$discount"))
                        .append("paidCount", countIf(new Document("$eq", List.of(
                                new Document("$toLower", ifNull("$paymentStatus", "")), "paid"))))
                        .append("deliveredCount", countIf(new Document("$eq", List.of("$orderStatus", DELIVERED))))));
        Map<String, SalesRow> sales = new HashMap<>();
        for (Document row : aggregate(INVOICES, pipeline)) {
            sales.put(String.valueOf(row.get("_id")), new SalesRow(
                    (long) number(row.get("invoiceCount")), number(row.get("invoiced")), number(row.get("taxable")),
                    number(row.get("gst")), number(row.get("discount")),
                    (long) number(row.get("paidCount")), (long) number(row.get("deliveredCount"))));
        }
        return sales;
    }

    /**
     * Money received in the period (payment entries by paidAt, amount &gt; 0), per shop and payment
     * method. Payment methods are grouped by canonical name (PaymentMethods: "cash", "CASH" -> Cash).
     */
    public List<MethodAmount> collectionsByShop(String shopId, Date from, Date before) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", match("shopId", shopId, "paymentHistories.paidAt", from, before)));
        pipeline.add(new Document("$unwind", "$paymentHistories"));
        Document entryMatch = new Document("paymentHistories.amount", new Document("$gt", 0));
        Document range = range(from, before);
        if (!range.isEmpty()) {
            entryMatch.append("paymentHistories.paidAt", range);
        }
        pipeline.add(new Document("$match", entryMatch));
        pipeline.add(new Document("$group", new Document("_id", new Document("shop", "$shopId")
                .append("method", methodKey("$paymentHistories.paidVia")))
                .append("name", new Document("$first", "$paymentHistories.paidVia"))
                .append("amount", new Document("$sum", "$paymentHistories.amount"))
                .append("count", new Document("$sum", 1))));
        return methodAmounts(aggregate(INVOICES, pipeline));
    }

    /** Invoices not yet delivered (as of now), per shop: count and balance due. */
    public Map<String, OpenRow> openInvoicesByShop(String shopId) {
        Document match = new Document("orderStatus", new Document("$ne", DELIVERED));
        if (shopId != null) {
            match.append("shopId", shopId);
        }
        List<Document> pipeline = List.of(
                new Document("$match", match),
                new Document("$group", new Document("_id", "$shopId")
                        .append("count", new Document("$sum", 1))
                        .append("balanceDue", new Document("$sum",
                                new Document("$max", List.of(ifNull("$balanceDue", 0), 0))))));
        Map<String, OpenRow> open = new HashMap<>();
        for (Document row : aggregate(INVOICES, pipeline)) {
            open.put(String.valueOf(row.get("_id")),
                    new OpenRow((long) number(row.get("count")), number(row.get("balanceDue"))));
        }
        return open;
    }

    // ================================================================ expenses

    /** Expenses dated in the period per category (expenseType, compared case-insensitively), largest first. */
    public List<TypeAmount> expensesByType(String shopId, Date from, Date before) {
        Document name = new Document("$trim", new Document("input", ifNull("$expenseType", "Unknown")));
        List<Document> pipeline = List.of(
                new Document("$match", match("shopId", shopId, "expenseDate", from, before)),
                new Document("$group", new Document("_id", new Document("$toLower", name))
                        .append("name", new Document("$first", name))
                        .append("amount", new Document("$sum", "$expenseAmount"))
                        .append("count", new Document("$sum", 1))),
                new Document("$sort", new Document("amount", -1)));
        List<TypeAmount> rows = new ArrayList<>();
        for (Document row : aggregate(EXPENSES, pipeline)) {
            String label = row.get("name") == null || row.get("name").toString().isBlank() ? "Unknown" : row.get("name").toString();
            rows.add(new TypeAmount(label, number(row.get("amount")), (long) number(row.get("count"))));
        }
        return rows;
    }

    /** Expenses dated in the period, per shop, payment method and shop/employee. */
    public List<ExpenseRow> expensesByShop(String shopId, Date from, Date before) {
        List<Document> pipeline = List.of(
                new Document("$match", match("shopId", shopId, "expenseDate", from, before)),
                new Document("$group", new Document("_id", new Document("shop", "$shopId")
                        .append("method", methodKey("$paymentMethod"))
                        // Arrays.asList, not List.of: the comparison needs a literal null element.
                        .append("employee", new Document("$ne", Arrays.asList(ifNull("$userId", null), null))))
                        .append("name", new Document("$first", "$paymentMethod"))
                        .append("amount", new Document("$sum", "$expenseAmount"))
                        .append("count", new Document("$sum", 1))));
        List<ExpenseRow> rows = new ArrayList<>();
        for (Document row : aggregate(EXPENSES, pipeline)) {
            Document id = (Document) row.get("_id");
            rows.add(new ExpenseRow(String.valueOf(id.get("shop")), displayMethod(id.get("method")),
                    Boolean.TRUE.equals(id.get("employee")), number(row.get("amount")), (long) number(row.get("count"))));
        }
        return rows;
    }

    // ================================================================ receipts / deleted invoices

    public Map<String, Long> receiptCountByShop(String shopId, Date from, Date before) {
        return countByShop(RECEIPTS, match("shopId", shopId, "receiptDate", from, before), "$shopId");
    }

    public Map<String, Long> deletedInvoiceCountByShop(String shopId, Date from, Date before) {
        Document match = match("invoice.shopId", shopId, "deletedAt", from, before)
                .append("invoice.receiptDate", new Document("$exists", false));
        return countByShop(DELETED_INVOICES, match, "$invoice.shopId");
    }

    // ================================================================ daily series

    /** Invoices per IST day: count and amount (with GST). */
    public Map<String, DayAmount> salesByDay(String shopId, Date from, Date before) {
        return salesBy(shopId, from, before, DAY);
    }

    /** Invoices per IST day (DAY) or month (MONTH, "yyyy-MM"): count and amount (with GST). */
    public Map<String, DayAmount> salesBy(String shopId, Date from, Date before, String bucketFormat) {
        List<Document> pipeline = List.of(
                new Document("$match", match("shopId", shopId, "invoiceDate", from, before)),
                new Document("$group", new Document("_id", istBucket("$invoiceDate", bucketFormat))
                        .append("count", new Document("$sum", 1))
                        .append("amount", new Document("$sum", "$totalAmountWithGST"))));
        return byDay(aggregate(INVOICES, pipeline));
    }

    /** Money received per IST day. */
    public Map<String, DayAmount> collectionsByDay(String shopId, Date from, Date before) {
        return collectionsBy(shopId, from, before, DAY);
    }

    /** Money received per IST day (DAY) or month (MONTH). */
    public Map<String, DayAmount> collectionsBy(String shopId, Date from, Date before, String bucketFormat) {
        Document entryMatch = new Document("paymentHistories.amount", new Document("$gt", 0));
        Document range = range(from, before);
        if (!range.isEmpty()) {
            entryMatch.append("paymentHistories.paidAt", range);
        }
        List<Document> pipeline = List.of(
                new Document("$match", match("shopId", shopId, "paymentHistories.paidAt", from, before)),
                new Document("$unwind", "$paymentHistories"),
                new Document("$match", entryMatch),
                new Document("$group", new Document("_id", istBucket("$paymentHistories.paidAt", bucketFormat))
                        .append("count", new Document("$sum", 1))
                        .append("amount", new Document("$sum", "$paymentHistories.amount"))));
        return byDay(aggregate(INVOICES, pipeline));
    }

    /** Expenses per IST day. */
    public Map<String, DayAmount> expensesByDay(String shopId, Date from, Date before) {
        return expensesBy(shopId, from, before, DAY);
    }

    /** Expenses per IST day (DAY) or month (MONTH). */
    public Map<String, DayAmount> expensesBy(String shopId, Date from, Date before, String bucketFormat) {
        List<Document> pipeline = List.of(
                new Document("$match", match("shopId", shopId, "expenseDate", from, before)),
                new Document("$group", new Document("_id", istBucket("$expenseDate", bucketFormat))
                        .append("count", new Document("$sum", 1))
                        .append("amount", new Document("$sum", "$expenseAmount"))));
        return byDay(aggregate(EXPENSES, pipeline));
    }

    // ================================================================ activities

    /** One page of activities, newest first. */
    public ActivityPage findActivities(String shopId, String employeeId, String type, Date from, Date before,
                                       int page, int size) {
        return findActivities(shopId, employeeId, type, null, from, before, page, size);
    }

    /** One page of activities, newest first; invoiceId (the invoice number) narrows them to one invoice. */
    public ActivityPage findActivities(String shopId, String employeeId, String type, String invoiceId,
                                       Date from, Date before,
                                       int page, int size) {
        Query query = new Query(activityCriteria(shopId, employeeId, type, from, before));
        if (invoiceId != null) {
            query.addCriteria(Criteria.where("invoiceId").is(invoiceId));
        }
        long total = mongoTemplate.count(query, ACTIVITIES);
        query.with(Sort.by(Sort.Direction.DESC, "timestamp")).skip((long) page * size).limit(size);
        return new ActivityPage(mongoTemplate.find(query, Document.class, ACTIVITIES), total);
    }

    /** Activity counts per employee and type in the period. */
    public List<EmployeeTypeCount> activityCountsByEmployee(String shopId, Date from, Date before) {
        List<Document> pipeline = List.of(
                new Document("$match", match("shopId", shopId, "timestamp", from, before)),
                new Document("$group", new Document("_id", new Document("employee", "$employeeId").append("type", "$type"))
                        .append("count", new Document("$sum", 1))
                        .append("name", new Document("$last", "$employeeName"))
                        .append("last", new Document("$max", "$timestamp"))));
        List<EmployeeTypeCount> counts = new ArrayList<>();
        for (Document row : aggregate(ACTIVITIES, pipeline)) {
            Document id = (Document) row.get("_id");
            counts.add(new EmployeeTypeCount(asString(id.get("employee")), asString(row.get("name")),
                    asString(id.get("type")), (long) number(row.get("count")), (Date) row.get("last")));
        }
        return counts;
    }

    /** Activity counts per type for one employee (any shop) or one shop. */
    public Map<String, Long> activityCountsByType(String shopId, String employeeId, Date from, Date before) {
        Document match = match("shopId", shopId, "timestamp", from, before);
        if (employeeId != null) {
            match.append("employeeId", employeeId);
        }
        List<Document> pipeline = List.of(new Document("$match", match),
                new Document("$group", new Document("_id", "$type").append("count", new Document("$sum", 1))));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Document row : aggregate(ACTIVITIES, pipeline)) {
            counts.put(asString(row.get("_id")), (long) number(row.get("count")));
        }
        return counts;
    }

    /** Activities per IST day (oldest first) for a shop and/or one employee. */
    public Map<String, Long> activityCountsByDay(String shopId, String employeeId, Date from, Date before) {
        Document match = match("shopId", shopId, "timestamp", from, before);
        if (employeeId != null) {
            match.append("employeeId", employeeId);
        }
        List<Document> pipeline = List.of(new Document("$match", match),
                new Document("$group", new Document("_id", istDay("$timestamp")).append("count", new Document("$sum", 1))),
                new Document("$sort", new Document("_id", 1)));
        Map<String, Long> days = new LinkedHashMap<>();
        for (Document row : aggregate(ACTIVITIES, pipeline)) {
            if (row.get("_id") != null) {
                days.put(row.get("_id").toString(), (long) number(row.get("count")));
            }
        }
        return days;
    }

    // ================================================================ employee analytics

    /**
     * The legacy per-employee analytics (EmployeeActivityServiceImpl.getAnalyticsForRole) over employee_activities.
     *
     * @param role TAILOR_MASTER: the master's own activities; TAILOR / SHOP_ASSISTANT: activities done by OR
     *             assigned to the employee, where "their" step is ASSIGNED_TO_ASSISTANT (tailor hands the item on)
     *             or ORDER_COMPLETED (assistant finishes it)
     * @return assigned, totalQuantity, totalRevenue, totalReassignments, reassignmentQuantity, reassignmentRevenue
     *         and, for TAILOR / SHOP_ASSISTANT, totalAssigned and totalCompleted
     */
    public Document employeeAnalytics(String shopId, String employeeId, String role, Date from, Date before) {
        Document match = match("shopId", shopId, "timestamp", from, before);
        Object rc = new Document("$ifNull", Arrays.asList("$reassignmentCount", 0));
        Object qty = new Document("$ifNull", Arrays.asList("$itemQuantity", 0));
        Object revenue = new Document("$ifNull", Arrays.asList("$totalRevenueGenerated", 0));
        Document firstTime = new Document("$eq", Arrays.asList(rc, 0));
        Document reassigned = new Document("$gt", Arrays.asList(rc, 0));

        Document group = new Document("_id", null);
        if ("TAILOR_MASTER".equals(role)) {
            match.append("employeeId", employeeId);
            group.append("assigned", sumIf(and(isType("ASSIGNED_TO_TAILOR"), firstTime), 1))
                    .append("totalQuantity", sumIf(firstTime, qty))
                    .append("totalRevenue", sumIf(firstTime, revenue))
                    .append("totalReassignments", new Document("$sum", rc))
                    .append("reassignmentQuantity", sumIf(reassigned, qty))
                    .append("reassignmentRevenue", sumIf(reassigned, revenue));
        } else {
            // TAILOR: their step = handing the item to a shop assistant; SHOP_ASSISTANT: completing it
            String step = "TAILOR".equals(role) ? "ASSIGNED_TO_ASSISTANT" : "ORDER_COMPLETED";
            String assignedType = "TAILOR".equals(role) ? "ASSIGNED_TO_TAILOR" : "ASSIGNED_TO_ASSISTANT";
            match.append("$or", List.of(new Document("employeeId", employeeId), new Document("assignedTo", employeeId)));
            Document isStep = isType(step);
            group.append("assigned", sumIf(and(isStep, firstTime), 1))
                    .append("totalQuantity", sumIf(and(isStep, firstTime), qty))
                    .append("totalRevenue", sumIf(and(isStep, firstTime), revenue))
                    .append("totalReassignments", sumIf(and(isStep, reassigned), rc))
                    .append("reassignmentQuantity", sumIf(and(isStep, reassigned), qty))
                    .append("reassignmentRevenue", sumIf(and(isStep, reassigned), revenue))
                    .append("totalAssigned", sumIf(and(isType(assignedType),
                            new Document("$eq", Arrays.asList("$assignedTo", employeeId))), 1))
                    .append("totalCompleted", sumIf(and(isStep,
                            new Document("$eq", Arrays.asList("$employeeId", employeeId))), 1));
        }
        List<Document> rows = aggregate(ACTIVITIES, List.of(new Document("$match", match), new Document("$group", group)));
        return rows.isEmpty() ? new Document() : rows.get(0);
    }

    /**
     * Item figures of a shop's invoices dated in the period (legacy getShopOwnerAnalytics): per production status
     * the item count, plus revenue (Σ itemPrice × itemQuantity) and quantity over all items, and the number of
     * invoices that have items with a status.
     *
     * @return { statusCounts: { STATUS: count }, totalRevenue, totalItems, invoiceCount }
     */
    public Document shopItemAnalytics(String shopId, Date from, Date before) {
        Object qty = new Document("$ifNull", Arrays.asList("$items.itemQuantity", 1));
        Object price = new Document("$ifNull", Arrays.asList("$items.itemPrice", 0));
        List<Document> pipeline = List.of(
                new Document("$match", match("shopId", shopId, "invoiceDate", from, before)),
                new Document("$unwind", "$items"),
                new Document("$match", new Document("items.itemOrderStatus", new Document("$exists", true).append("$ne", null))),
                new Document("$facet", new Document()
                        .append("byStatus", List.of(new Document("$group", new Document("_id", "$items.itemOrderStatus")
                                .append("count", new Document("$sum", 1))
                                .append("revenue", new Document("$sum", new Document("$multiply", Arrays.asList(price, qty))))
                                .append("quantity", new Document("$sum", qty)))))
                        .append("invoices", List.of(new Document("$group", new Document("_id", "$invoiceId")),
                                new Document("$count", "count")))));
        List<Document> result = aggregate(INVOICES, pipeline);
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        double totalRevenue = 0;
        double totalItems = 0;
        long invoiceCount = 0;
        if (!result.isEmpty()) {
            for (Object row : (List<?>) result.get(0).get("byStatus", List.class)) {
                Document status = (Document) row;
                statusCounts.put(asString(status.get("_id")), (long) number(status.get("count")));
                totalRevenue += number(status.get("revenue"));
                totalItems += number(status.get("quantity"));
            }
            List<?> invoices = result.get(0).get("invoices", List.class);
            if (invoices != null && !invoices.isEmpty()) {
                invoiceCount = (long) number(((Document) invoices.get(0)).get("count"));
            }
        }
        return new Document("statusCounts", statusCounts).append("totalRevenue", totalRevenue)
                .append("totalItems", totalItems).append("invoiceCount", invoiceCount);
    }

    private static Document isType(String type) {
        return new Document("$eq", Arrays.asList("$type", type));
    }

    private static Document and(Document... conditions) {
        return new Document("$and", Arrays.asList(conditions));
    }

    private static Document sumIf(Document condition, Object value) {
        return new Document("$sum", new Document("$cond", Arrays.asList(condition, value, 0)));
    }

    // ================================================================ deleted invoices

    /**
     * One page of archived invoices, newest deletion first. search matches the invoice number, or
     * the customer's name / phone (literal, case-insensitive).
     */
    public DeletedPage findDeletedInvoices(String shopId, String search, Date from, Date before, int page, int size) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("invoice").exists(true));
        criteria.add(Criteria.where("invoice.receiptDate").exists(false)); // legacy DeletedReceipt shares the collection
        if (shopId != null) {
            criteria.add(Criteria.where("invoice.shopId").is(shopId));
        }
        if (from != null) {
            criteria.add(Criteria.where("deletedAt").gte(from));
        }
        if (before != null) {
            criteria.add(Criteria.where("deletedAt").lt(before));
        }
        if (search != null) {
            Pattern pattern = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
            List<Object> customerIds = matchingCustomerIds(pattern);
            criteria.add(customerIds.isEmpty()
                    ? Criteria.where("invoice.invoiceId").regex(pattern)
                    : new Criteria().orOperator(Criteria.where("invoice.invoiceId").regex(pattern),
                    Criteria.where("invoice.customer.$id").in(customerIds)));
        }
        Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        long total = mongoTemplate.count(query, DELETED_INVOICES);
        query.with(Sort.by(Sort.Direction.DESC, "deletedAt")).skip((long) page * size).limit(size);
        return new DeletedPage(mongoTemplate.find(query, Document.class, DELETED_INVOICES), total);
    }

    /** One archived (deleted) invoice, by archive id, within the shop (null = any shop). */
    public Optional<Document> findDeletedInvoice(String archiveId, String shopId) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("_id").is(ObjectId.isValid(archiveId) ? new ObjectId(archiveId) : archiveId));
        criteria.add(Criteria.where("invoice").exists(true));
        criteria.add(Criteria.where("invoice.receiptDate").exists(false)); // legacy DeletedReceipt shares the collection
        if (shopId != null) {
            criteria.add(Criteria.where("invoice.shopId").is(shopId));
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0]))), Document.class, DELETED_INVOICES));
    }

    // ================================================================ deliveries

    /**
     * All invoices not yet delivered (one shop, or all shops), with only the fields the delivery
     * dashboard needs. Open orders are a small set (delivered invoices are excluded), so the
     * delivery dates are read in Java: they are stored as text in several formats.
     */
    public List<Document> findOpenInvoices(String shopId) {
        Criteria criteria = Criteria.where("orderStatus").ne(DELIVERED);
        if (shopId != null) {
            criteria = criteria.and("shopId").is(shopId);
        }
        Query query = new Query(criteria).with(Sort.by("invoiceDate"));
        query.fields().include("invoiceId").include("shopId").include("customer").include("expectedDeliveryDate")
                .include("invoiceDate").include("totalAmountWithGST").include("balanceDue").include("paymentStatus")
                .include("items.itemName").include("items.itemQuantity").include("items.itemOrderStatus");
        return mongoTemplate.find(query, Document.class, INVOICES);
    }

    /** Receipts generated in [from, before), oldest first. */
    public List<Document> findReceipts(String shopId, Date from, Date before) {
        Query query = new Query(new Criteria().andOperator(
                shopId == null ? new Criteria() : Criteria.where("shopId").is(shopId),
                Criteria.where("receiptDate").gte(from).lt(before)))
                .with(Sort.by("receiptDate"));
        query.fields().include("receiptNo").include("invoiceId").include("shopId").include("customer")
                .include("receiptDate").include("totalAmountWithGST");
        return mongoTemplate.find(query, Document.class, RECEIPTS);
    }

    /**
     * The invoices of delivered orders, keyed by invoice database id: number (invoiceId), invoiceDate,
     * expectedDeliveryDate and items (name, quantity, status). A delivery receipt stores the invoice's database id
     * in its "invoiceId" field, not the invoice number the pages and links use.
     */
    public Map<String, Document> findInvoicesByDbId(Collection<String> invoiceDbIds) {
        Map<String, Document> invoices = new HashMap<>();
        List<Object> ids = invoiceDbIds.stream().filter(id -> id != null && !id.isBlank()).distinct()
                .map(id -> (Object) (ObjectId.isValid(id) ? new ObjectId(id) : id)).toList();
        if (ids.isEmpty()) {
            return invoices;
        }
        Query query = new Query(Criteria.where("_id").in(ids));
        query.fields().include("_id").include("invoiceId").include("invoiceDate").include("expectedDeliveryDate")
                .include("items.itemName").include("items.itemQuantity").include("items.itemOrderStatus");
        mongoTemplate.find(query, Document.class, INVOICES)
                .forEach(invoice -> invoices.put(invoice.get("_id").toString(), invoice));
        return invoices;
    }

    /** Customers (name, phone) by the ids inside invoice customer DBRefs. */
    public Map<String, Document> findCustomers(Collection<Object> customerIds) {
        Map<String, Document> customers = new HashMap<>();
        if (customerIds.isEmpty()) {
            return customers;
        }
        Query query = new Query(Criteria.where("_id").in(customerIds));
        query.fields().include("_id").include("name").include("phone");
        for (Document customer : mongoTemplate.find(query, Document.class, CUSTOMERS)) {
            customers.put(customer.get("_id").toString(), customer);
        }
        return customers;
    }

    /** userId → name for the given user ids (unknown ids are left out). */
    public Map<String, String> findUserNames(Collection<String> userIds) {
        Map<String, String> names = new HashMap<>();
        List<String> ids = userIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (ids.isEmpty()) {
            return names;
        }
        Query query = new Query(Criteria.where("userId").in(ids));
        query.fields().include("userId").include("name");
        for (Document user : mongoTemplate.find(query, Document.class, USERS)) {
            names.put(user.getString("userId"), user.getString("name"));
        }
        return names;
    }

    /** The id inside a customer DBRef (or null). */
    public static Object customerRefId(Object customerRef) {
        return customerRef instanceof DBRef ref ? ref.getId() : null;
    }

    // ================================================================ helpers

    private List<Object> matchingCustomerIds(Pattern pattern) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("name").regex(pattern), Criteria.where("phone").regex(pattern)))
                .limit(MAX_CUSTOMER_MATCHES);
        query.fields().include("_id");
        return mongoTemplate.find(query, Document.class, CUSTOMERS).stream().map(customer -> customer.get("_id")).toList();
    }

    private static Criteria activityCriteria(String shopId, String employeeId, String type, Date from, Date before) {
        List<Criteria> criteria = new ArrayList<>();
        if (shopId != null) {
            criteria.add(Criteria.where("shopId").is(shopId));
        }
        if (employeeId != null) {
            criteria.add(Criteria.where("employeeId").is(employeeId));
        }
        if (type != null) {
            criteria.add(Criteria.where("type").is(type));
        }
        if (from != null) {
            criteria.add(Criteria.where("timestamp").gte(from));
        }
        if (before != null) {
            criteria.add(Criteria.where("timestamp").lt(before));
        }
        return criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria.toArray(new Criteria[0]));
    }

    private Map<String, Long> countByShop(String collection, Document match, String shopField) {
        List<Document> pipeline = List.of(new Document("$match", match),
                new Document("$group", new Document("_id", shopField).append("count", new Document("$sum", 1))));
        Map<String, Long> counts = new HashMap<>();
        for (Document row : aggregate(collection, pipeline)) {
            counts.put(String.valueOf(row.get("_id")), (long) number(row.get("count")));
        }
        return counts;
    }

    private List<Document> aggregate(String collection, List<Document> pipeline) {
        return mongoTemplate.getCollection(collection).aggregate(pipeline).allowDiskUse(true).into(new ArrayList<>());
    }

    private static List<MethodAmount> methodAmounts(List<Document> rows) {
        List<MethodAmount> amounts = new ArrayList<>();
        for (Document row : rows) {
            Document id = (Document) row.get("_id");
            amounts.add(new MethodAmount(String.valueOf(id.get("shop")), displayMethod(id.get("method")),
                    number(row.get("amount")), (long) number(row.get("count"))));
        }
        return amounts;
    }

    private static Map<String, DayAmount> byDay(List<Document> rows) {
        Map<String, DayAmount> days = new HashMap<>();
        for (Document row : rows) {
            if (row.get("_id") != null) {
                days.put(row.get("_id").toString(),
                        new DayAmount((long) number(row.get("count")), number(row.get("amount"))));
            }
        }
        return days;
    }

    /** {shopField: shopId (if any), dateField: [from, before) (if any)} */
    private static Document match(String shopField, String shopId, String dateField, Date from, Date before) {
        Document match = new Document();
        if (shopId != null) {
            match.append(shopField, shopId);
        }
        Document range = range(from, before);
        if (!range.isEmpty()) {
            match.append(dateField, range);
        }
        return match;
    }

    private static Document range(Date from, Date before) {
        Document range = new Document();
        if (from != null) {
            range.append("$gte", from);
        }
        if (before != null) {
            range.append("$lt", before);
        }
        return range;
    }

    /** Bucket formats for the series: one IST day, or one IST month ("yyyy-MM"). */
    public static final String DAY = "%Y-%m-%d";
    public static final String MONTH = "%Y-%m";

    private static Document istDay(String dateExpression) {
        return istBucket(dateExpression, DAY);
    }

    private static Document istBucket(String dateExpression, String format) {
        return new Document("$dateToString", new Document("format", format)
                .append("date", dateExpression).append("timezone", IST));
    }

    /** Canonical payment-method name (one list; old spellings are mapped), used as group key and label. */
    private static Document methodKey(String field) {
        return PaymentMethods.canonicalExpression(field);
    }

    private static Document ifNull(String field, Object fallback) {
        List<Object> args = new ArrayList<>();
        args.add(field);
        args.add(fallback);
        return new Document("$ifNull", args);
    }

    private static Document countIf(Document condition) {
        return new Document("$sum", new Document("$cond", List.of(condition, 1, 0)));
    }

    private static String displayMethod(Object name) {
        return name == null || name.toString().isBlank() ? "Unknown" : name.toString().trim();
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Object toId(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id) : id;
    }

    // ================================================================ result types

    public record ShopInfo(String shopId, String shopName, String shopType, String ownerName, double pettyCash,
                           int employeeCount) {
    }

    public record SalesRow(long invoiceCount, double invoiced, double taxable, double gst, double discount,
                           long paidCount, long deliveredCount) {
    }

    public record MethodAmount(String shopId, String method, double amount, long count) {
    }

    public record OpenRow(long count, double balanceDue) {
    }

    public record ExpenseRow(String shopId, String method, boolean employee, double amount, long count) {
    }

    public record DayAmount(long count, double amount) {
    }

    public record TypeAmount(String type, double amount, long count) {
    }

    public record ActivityPage(List<Document> activities, long total) {
    }

    public record EmployeeTypeCount(String employeeId, String employeeName, String type, long count, Date lastActivityAt) {
    }

    public record DeletedPage(List<Document> archives, long total) {
    }
}
