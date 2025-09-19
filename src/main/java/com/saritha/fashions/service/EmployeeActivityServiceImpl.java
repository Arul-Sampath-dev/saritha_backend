package com.saritha.fashions.service;

import com.mongodb.BasicDBObject;
import com.saritha.fashions.model.ActivityType;
import com.saritha.fashions.model.EmployeeActivity;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.activity.DailyCountDTO;
import com.saritha.fashions.payload.activity.EmployeeActivitySummaryDTO;
import com.saritha.fashions.service.Interface.EmployeeActivityService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.time.*;
import java.util.*;

@Service
public class EmployeeActivityServiceImpl implements EmployeeActivityService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AuthUtil authUtil;

    /**
     * Record a new activity (call this from places in your existing flow)
     */
    @Override
    public void recordActivity(String shopId,
                               String invoiceId,
                               String invoiceDbId,
                               String employeeId,
                               String employeeName,
                               ActivityType type,
                               String notes) {
        EmployeeActivity act = new EmployeeActivity();
        act.setShopId(shopId);
        act.setInvoiceId(invoiceId);
        act.setInvoiceDbId(invoiceDbId);
        act.setEmployeeId(employeeId);
        act.setEmployeeName(employeeName);
        act.setType(type);
        act.setTimestamp(DateTimeUtils.getNowInIST());
        act.setNotes(notes);
        mongoTemplate.save(act);
    }

    @Override
    public void recordActivity(
            String shopId,
            String invoiceId,
            String invoiceDbId,
            String employeeId,
            String employeeName,
            ActivityType type,
            String notes,
            int itemQuantity,
            double itemPrice,
            int reassignmentCount,
            String assignedTo
    ) {
        EmployeeActivity activity = new EmployeeActivity();
        activity.setShopId(shopId);
        activity.setInvoiceId(invoiceId);
        activity.setInvoiceDbId(invoiceDbId);
        activity.setEmployeeId(employeeId);
        activity.setEmployeeName(employeeName);
        activity.setType(type);
        activity.setTimestamp(DateTimeUtils.getNowInIST());
        activity.setNotes(notes);

        // ✅ Store new fields
        activity.setItemQuantity(itemQuantity);
        activity.setItemPrice(itemPrice);
        activity.setTotalRevenueGenerated(itemQuantity * itemPrice);

        activity.setReassignmentCount(reassignmentCount);
        activity.setAssignedTo(assignedTo);

        mongoTemplate.save(activity);
    }

    @Override
    public void recordActivity(
            String shopId,
            String invoiceId,
            String invoiceDbId,
            String employeeId,
            String employeeName,
            ActivityType type,
            String notes,
            int itemQuantity,
            double itemPrice,
            int reassignmentCount
    ) {
        EmployeeActivity activity = new EmployeeActivity();
        activity.setShopId(shopId);
        activity.setInvoiceId(invoiceId);
        activity.setInvoiceDbId(invoiceDbId);
        activity.setEmployeeId(employeeId);
        activity.setEmployeeName(employeeName);
        activity.setType(type);
        activity.setTimestamp(DateTimeUtils.getNowInIST());

        // ✅ Store new fields
        activity.setItemQuantity(itemQuantity);
        activity.setItemPrice(itemPrice);
        activity.setTotalRevenueGenerated(itemQuantity * itemPrice);

        activity.setReassignmentCount(reassignmentCount);

        mongoTemplate.save(activity);
    }


    /**
     * Get paginated per-employee daily summary for a given shop and date range.
     * Returns for each employee: employeeId, employeeName, list of {date, count}
     */
    @Override
    public PaginatedResponse<EmployeeActivitySummaryDTO> getEmployeeDailySummary(
            String shopId,
            Date fromInclusive,
            Date toInclusive,
            int pageNumber,
            int pageSize,
            String sortBy,
            String sortOrder
    ) {
        Criteria criteria = Criteria.where("shopId").is(shopId);
        if (fromInclusive != null && toInclusive != null) {
            criteria = criteria.and("timestamp").gte(fromInclusive).lte(toInclusive);
        } else if (fromInclusive != null) {
            criteria = criteria.and("timestamp").gte(fromInclusive);
        } else if (toInclusive != null) {
            criteria = criteria.and("timestamp").lte(toInclusive);
        }

        MatchOperation match = Aggregation.match(criteria);

        ProjectionOperation projectToDate = Aggregation.project("employeeId", "employeeName")
                .andExpression("{ $dateToString: { format: '%Y-%m-%d', date: '$timestamp', timezone: 'Asia/Kolkata' } }")
                .as("day");

        GroupOperation groupByEmployeeDay = Aggregation.group("employeeId", "employeeName", "day")
                .count().as("count");

        GroupOperation groupByEmployee = Aggregation.group("_id.employeeId", "_id.employeeName")
                .push(new BasicDBObject("day", "$_id.day").append("count", "$count")).as("dailyCounts")
                .first("$_id.employeeName").as("employeeName")
                .first("$_id.employeeId").as("employeeId");

        // Sorting
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
        SortOperation sort = Aggregation.sort(Sort.by(direction, sortBy != null ? sortBy : "employeeName"));

        SkipOperation skip = Aggregation.skip((long) pageNumber * pageSize);
        LimitOperation limit = Aggregation.limit(pageSize);

        Aggregation aggregation = Aggregation.newAggregation(
                match,
                projectToDate,
                groupByEmployeeDay,
                groupByEmployee,
                sort,
                skip,
                limit
        );

        AggregationResults<Document> aggregated = mongoTemplate.aggregate(aggregation, "employee_activities", Document.class);
        List<Document> mapped = aggregated.getMappedResults();

        List<EmployeeActivitySummaryDTO> content = new ArrayList<>();
        for (Document doc : mapped) {
            EmployeeActivitySummaryDTO dto = new EmployeeActivitySummaryDTO();
            dto.setEmployeeId(doc.getString("employeeId"));
            dto.setEmployeeName(doc.getString("employeeName"));

            List<Document> daily = (List<Document>) doc.get("dailyCounts");
            List<DailyCountDTO> dailyCounts = new ArrayList<>();
            if (daily != null) {
                for (Document d : daily) {
                    String day = d.getString("day");
                    Integer count = d.getInteger("count", 0);
                    dailyCounts.add(new DailyCountDTO(day, count));
                }
            }
            dailyCounts.sort(Comparator.comparing(DailyCountDTO::getDay));
            dto.setDailyCounts(dailyCounts);
            content.add(dto);
        }

        // Count distinct employees
        Aggregation countAgg = Aggregation.newAggregation(
                match,
                projectToDate,
                groupByEmployeeDay,
                Aggregation.group("_id.employeeId"),
                Aggregation.count().as("totalEmployees")
        );
        AggregationResults<Document> countRes = mongoTemplate.aggregate(countAgg, "employee_activities", Document.class);
        long totalElements = !countRes.getMappedResults().isEmpty()
                ? countRes.getMappedResults().get(0).getInteger("totalEmployees", 0)
                : 0;

        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        boolean lastPage = (pageNumber + 1) >= totalPages;

        return new PaginatedResponse<>(content, pageNumber, pageSize, totalElements, totalPages, lastPage);
    }


    /**
     * Get a single employee daily breakdown (date -> count) for date range
     */
    @Override
    public PaginatedResponse<DailyCountDTO> getEmployeeDailyBreakdown(
            String shopId,
            String employeeId,
            Date fromInclusive,
            Date toInclusive,
            int pageNumber,
            int pageSize,
            String sortBy,
            String sortOrder
    ) {
        Criteria criteria = Criteria.where("shopId").is(shopId).and("employeeId").is(employeeId);
        if (fromInclusive != null && toInclusive != null) {
            criteria = criteria.and("timestamp").gte(fromInclusive).lte(toInclusive);
        } else if (fromInclusive != null) {
            criteria = criteria.and("timestamp").gte(fromInclusive);
        } else if (toInclusive != null) {
            criteria = criteria.and("timestamp").lte(toInclusive);
        }

        MatchOperation match = Aggregation.match(criteria);

        ProjectionOperation projectToDate = Aggregation.project("employeeId")
                .andExpression("{ $dateToString: { format: '%Y-%m-%d', date: '$timestamp', timezone: 'Asia/Kolkata' } }")
                .as("day");

        GroupOperation groupByDay = Aggregation.group("day").count().as("count");

        // Sorting
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
        SortOperation sort = Aggregation.sort(Sort.by(direction, sortBy != null ? sortBy : "_id"));

        SkipOperation skip = Aggregation.skip((long) pageNumber * pageSize);
        LimitOperation limit = Aggregation.limit(pageSize);

        Aggregation agg = Aggregation.newAggregation(match, projectToDate, groupByDay, sort, skip, limit);
        AggregationResults<Document> res = mongoTemplate.aggregate(agg, "employee_activities", Document.class);

        List<DailyCountDTO> content = new ArrayList<>();
        for (Document d : res.getMappedResults()) {
            String day = d.getString("_id");
            Integer count = d.getInteger("count", 0);
            content.add(new DailyCountDTO(day, count));
        }

        // Count total days
        Aggregation countAgg = Aggregation.newAggregation(match, projectToDate, groupByDay, Aggregation.count().as("totalDays"));
        AggregationResults<Document> countRes = mongoTemplate.aggregate(countAgg, "employee_activities", Document.class);
        long totalElements = !countRes.getMappedResults().isEmpty()
                ? countRes.getMappedResults().get(0).getInteger("totalDays", 0)
                : 0;

        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        boolean lastPage = (pageNumber + 1) >= totalPages;

        return new PaginatedResponse<>(content, pageNumber, pageSize, totalElements, totalPages, lastPage);
    }

    @Override
    public Map<String, Object> getAnalyticsForRole(String shopId, String employeeId, String role, Date from, Date to) {
        return switch (role) {
            case "SHOP_OWNER" -> getShopOwnerAnalytics(shopId, from, to);
            case "TAILOR_MASTER" -> getTailorMasterAnalytics(shopId, employeeId, from, to);
            case "TAILOR" -> getEmployeeAnalyticsForTailor(shopId, employeeId, from, to);
            case "SHOP_ASSISTANT" -> getEmployeeAnalyticsForShopAssistant(shopId, employeeId, from, to);
            default -> Collections.emptyMap();
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getShopOwnerAnalytics(String shopId, Date from, Date to) {
        // --- Normalize dates into IST start/end of day ---
        Date startOfDayIST = null;
        Date endOfDayIST = null;

        if (from != null) {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
            cal.setTime(from);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            startOfDayIST = cal.getTime();
        }

        if (to != null) {
            endOfDayIST = DateTimeUtils.endOfDayInIST(to);
        }

        // --- Convert IST → UTC ---
        Date fromUtc = (startOfDayIST != null) ? Date.from(startOfDayIST.toInstant()) : null;
        Date toUtc = (endOfDayIST != null) ? Date.from(endOfDayIST.toInstant()) : null;

        // --- Build $match stage for invoices ---
        Document matchDoc = new Document("shopId", shopId);
        if (fromUtc != null && toUtc != null) {
            matchDoc.append("invoiceDate", new Document("$gte", fromUtc).append("$lte", toUtc));
        }
        AggregationOperation matchStage = context -> new Document("$match", matchDoc);

        // --- $unwind items ---
        AggregationOperation unwindItems = context -> new Document("$unwind", "$items");

        // --- Only valid items with itemOrderStatus ---
        AggregationOperation matchValidItems = context -> new Document("$match",
                new Document("items.itemOrderStatus", new Document("$exists", true).append("$ne", null)));

        // --- Group for statuses, revenue and totalItems ---
        Document groupDoc = new Document("$group", new Document("_id", null)
                .append("ORDER_ACCEPTED", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "ORDER_ACCEPTED")), 1, 0))))
                .append("CUTTING", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "CUTTING")), 1, 0))))
                .append("TAILOR_ASSIGNED", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "TAILOR_ASSIGNED")), 1, 0))))
                .append("STITCHING", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "STITCHING")), 1, 0))))
                .append("SHOP_ASSISTANT_ASSIGNED", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "SHOP_ASSISTANT_ASSIGNED")), 1, 0))))
                .append("IRONING", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "IRONING")), 1, 0))))
                .append("HEMMING", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "HEMMING")), 1, 0))))
                .append("ORDER_COMPLETED", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "ORDER_COMPLETED")), 1, 0))))
                .append("ORDER_DELIVERED", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "ORDER_DELIVERED")), 1, 0))))
                .append("UNDER_PRODUCTION", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "UNDER_PRODUCTION")), 1, 0))))
                .append("ASSIGNED", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "ASSIGNED")), 1, 0))))
                .append("UNASSIGNED_DUE_TO_USER_DELETION", new Document("$sum", new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$items.itemOrderStatus", "UNASSIGNED_DUE_TO_USER_DELETION")), 1, 0))))
                // totalRevenue = sum(price * qty)
                .append("totalRevenue", new Document("$sum", new Document("$multiply", Arrays.asList(
                        new Document("$ifNull", Arrays.asList("$items.itemPrice", 0)),
                        new Document("$ifNull", Arrays.asList("$items.itemQuantity", 1))
                ))))
                // totalItems = sum of itemQuantity
                .append("totalItems", new Document("$sum", new Document("$ifNull", Arrays.asList("$items.itemQuantity", 1)))));

        AggregationOperation groupStage = context -> groupDoc;

        // --- $addFields totalStatusCount ---
        Document addFieldsDoc = new Document("$addFields", new Document("totalStatusCount", new Document("$add", Arrays.asList(
                "$ORDER_ACCEPTED", "$CUTTING", "$TAILOR_ASSIGNED", "$STITCHING",
                "$SHOP_ASSISTANT_ASSIGNED", "$IRONING", "$HEMMING",
                "$ORDER_COMPLETED", "$ORDER_DELIVERED", "$UNDER_PRODUCTION",
                "$ASSIGNED", "$UNASSIGNED_DUE_TO_USER_DELETION"
        ))));
        AggregationOperation addFieldsStage = context -> addFieldsDoc;

        // --- Invoice count pipeline ---
        Document invoiceGroup = new Document("$group", new Document("_id", "$invoiceId"));
        Document invoiceCount = new Document("$count", "count");

        // --- Facet: status+revenue vs invoice count ---
        Document facetDoc = new Document("$facet",
                new Document("statusRevenue", Arrays.asList(groupDoc, addFieldsDoc))
                        .append("invoiceCount", Arrays.asList(invoiceGroup, invoiceCount))
        );
        AggregationOperation facetStage = context -> facetDoc;

        // --- Project: flatten fields ---
        Document projectDoc = new Document("$project",
                new Document("status", new Document("$arrayElemAt", Arrays.asList("$statusRevenue", 0)))
                        .append("invoiceCount", new Document("$ifNull", Arrays.asList(new Document("$arrayElemAt", Arrays.asList("$invoiceCount.count", 0)), 0)))
        );
        AggregationOperation projectStage = context -> projectDoc;

        // --- Lookup receipts ---
        List<Object> andExpr = new ArrayList<>();
        andExpr.add(new Document("$eq", Arrays.asList("$shopId", shopId)));
        if (fromUtc != null && toUtc != null) {
            andExpr.add(new Document("$gte", Arrays.asList("$receiptDate", fromUtc)));
            andExpr.add(new Document("$lte", Arrays.asList("$receiptDate", toUtc)));
        }
        Document receiptLookup = new Document("$lookup",
                new Document("from", "receipt")
                        .append("pipeline", Arrays.asList(
                                new Document("$match", new Document("$expr", new Document("$and", andExpr))),
                                new Document("$count", "count")
                        ))
                        .append("as", "receiptStats")
        );
        AggregationOperation lookupStage = context -> receiptLookup;

        // --- Add receiptCount ---
        Document addFieldsReceipt = new Document("$addFields",
                new Document("receiptCount", new Document("$ifNull", Arrays.asList(new Document("$arrayElemAt", Arrays.asList("$receiptStats.count", 0)), 0)))
        );
        AggregationOperation addFieldsReceiptStage = context -> addFieldsReceipt;

        // --- Lookup deleted_invoices ---
        List<Object> deletedAndExpr = new ArrayList<>();
        deletedAndExpr.add(new Document("$eq", Arrays.asList("$invoice.shopId", shopId)));
        if (fromUtc != null && toUtc != null) {
            deletedAndExpr.add(new Document("$gte", Arrays.asList("$deletedAt", fromUtc)));
            deletedAndExpr.add(new Document("$lte", Arrays.asList("$deletedAt", toUtc)));
        }
        Document deletedLookup = new Document("$lookup",
                new Document("from", "deleted_invoices")
                        .append("pipeline", Arrays.asList(
                                new Document("$match", new Document("$expr", new Document("$and", deletedAndExpr))),
                                new Document("$count", "count")
                        ))
                        .append("as", "deletedStats")
        );
        AggregationOperation deletedLookupStage = context -> deletedLookup;

        // --- Add deletedInvoiceCount ---
        Document addFieldsDeleted = new Document("$addFields",
                new Document("deletedInvoiceCount", new Document("$ifNull",
                        Arrays.asList(new Document("$arrayElemAt", Arrays.asList("$deletedStats.count", 0)), 0)))
        );
        AggregationOperation addFieldsDeletedStage = context -> addFieldsDeleted;

        // --- Replace root ---
        Document replaceRoot = new Document("$replaceRoot",
                new Document("newRoot", new Document("$mergeObjects", Arrays.asList("$status",
                        new Document("invoiceCount", "$invoiceCount")
                                .append("receiptCount", "$receiptCount")
                                .append("deletedInvoiceCount", "$deletedInvoiceCount"))))
        );
        AggregationOperation replaceRootStage = context -> replaceRoot;

        // --- Build pipeline ---
        List<AggregationOperation> pipeline = Arrays.asList(
                matchStage,
                unwindItems,
                matchValidItems,
                facetStage,
                projectStage,
                lookupStage,
                addFieldsReceiptStage,
                deletedLookupStage,
                addFieldsDeletedStage,
                replaceRootStage
        );

        Aggregation agg = Aggregation.newAggregation(pipeline);

        // --- Execute ---
        Document out = mongoTemplate.aggregate(agg, "invoice", Document.class).getUniqueMappedResult();
        Map<String, Object> result = new HashMap<>();
        if (out != null) {
            out.forEach((k, v) -> {
                if (!"_id".equals(k)) result.put(k, v);
            });
        }

        // --- Post processing ---
        double totalRevenue = ((Number) result.getOrDefault("totalRevenue", 0)).doubleValue();
        long invoiceCountVal = ((Number) result.getOrDefault("invoiceCount", 0)).longValue();
        long receiptCountVal = ((Number) result.getOrDefault("receiptCount", 0)).longValue();
        long deletedInvoiceCountVal = ((Number) result.getOrDefault("deletedInvoiceCount", 0)).longValue();
        long totalItems = ((Number) result.getOrDefault("totalItems", 0)).longValue();

        double avg = invoiceCountVal > 0 ? (totalRevenue / invoiceCountVal) : 0.0;
        avg = Math.round(avg * 100.0) / 100.0;

        result.put("totalRevenue", totalRevenue);
        result.put("invoiceCount", invoiceCountVal);
        result.put("receiptCount", receiptCountVal);
        result.put("deletedInvoiceCount", deletedInvoiceCountVal);
        result.put("totalItems", totalItems);
        result.put("averageRevenuePerInvoice", avg);

        return result;
    }

    private Map<String, Object> getTailorMasterAnalytics(String shopId, String employeeId, Date from, Date to) {
        // --- Normalize to IST and then convert to UTC ---
        Date fromUtc = null, toUtc = null;
        if (from != null) {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
            cal.setTime(from);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            fromUtc = Date.from(cal.toInstant());
        }
        if (to != null) {
            Date endOfDayIST = DateTimeUtils.endOfDayInIST(to);
            toUtc = Date.from(endOfDayIST.toInstant());
        }

        // --- Match criteria ---
        Criteria matchCriteria = Criteria.where("shopId").is(shopId).and("employeeId").is(employeeId);
        if (fromUtc != null && toUtc != null) {
            matchCriteria = matchCriteria.and("timestamp").gte(fromUtc).lte(toUtc);
        }

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.group()
                        // ✅ Normal assignment count (exclude reassignments)
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ASSIGNED_TO_TAILOR),
                                        Criteria.where("reassignmentCount").is(0)
                                )
                        ).then(1).otherwise(0)).as("assigned")

                        // ✅ Normal totals
                        .sum(ConditionalOperators.when(Criteria.where("reassignmentCount").is(0))
                                .thenValueOf("itemQuantity").otherwise(0)).as("totalQuantity")
                        .sum(ConditionalOperators.when(Criteria.where("reassignmentCount").is(0))
                                .thenValueOf("totalRevenueGenerated").otherwise(0)).as("totalRevenue")

                        // ✅ Reassignment totals
                        .sum("reassignmentCount").as("totalReassignments")
                        .sum(ConditionalOperators.when(Criteria.where("reassignmentCount").gt(0))
                                .thenValueOf("itemQuantity").otherwise(0)).as("reassignmentQuantity")
                        .sum(ConditionalOperators.when(Criteria.where("reassignmentCount").gt(0))
                                .thenValueOf("totalRevenueGenerated").otherwise(0)).as("reassignmentRevenue")
        );

        Map<String, Object> result = mongoTemplate.aggregate(agg, "employee_activities", Map.class).getUniqueMappedResult();
        return result != null ? result : new HashMap<>();
    }

    private Map<String, Object> getEmployeeAnalyticsForTailor(
            String shopId, String employeeId, Date from, Date to) {

        // --- Normalize frontend yyyy-MM-dd to IST day → then UTC bounds ---
        Date fromUtc = null, toUtc = null;
        if (from != null) {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
            cal.setTime(from);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            // ✅ Start of day IST → UTC
            fromUtc = Date.from(cal.toInstant().atZone(ZoneId.of("Asia/Kolkata"))
                    .withZoneSameInstant(ZoneOffset.UTC).toInstant());
        }
        if (to != null) {
            // ✅ End of day IST → UTC
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
            cal.setTime(to);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);

            toUtc = Date.from(cal.toInstant().atZone(ZoneId.of("Asia/Kolkata"))
                    .withZoneSameInstant(ZoneOffset.UTC).toInstant());
        }

        // --- Match criteria: shop + employee (id or assigned) + strict date ---
        Criteria matchCriteria = new Criteria().andOperator(
                Criteria.where("shopId").is(shopId),
                new Criteria().orOperator(
                        Criteria.where("employeeId").is(employeeId),
                        Criteria.where("assignedTo").is(employeeId)
                ),
                Criteria.where("timestamp").gte(fromUtc).lte(toUtc) // ✅ Strict UTC bounds
        );

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.group()

                        // ✅ Assigned normally (assistant, no reassignments)
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ASSIGNED_TO_ASSISTANT),
                                        Criteria.where("reassignmentCount").is(0)
                                )
                        ).then(1).otherwise(0)).as("assigned")

                        // ✅ Normal totals
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ASSIGNED_TO_ASSISTANT),
                                        Criteria.where("reassignmentCount").is(0)
                                )
                        ).thenValueOf("itemQuantity").otherwise(0)).as("totalQuantity")

                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ASSIGNED_TO_ASSISTANT),
                                        Criteria.where("reassignmentCount").is(0)
                                )
                        ).thenValueOf("totalRevenueGenerated").otherwise(0)).as("totalRevenue")

                        // ✅ Reassignment totals
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ASSIGNED_TO_ASSISTANT),
                                        Criteria.where("reassignmentCount").gt(0)
                                )
                        ).thenValueOf("reassignmentCount").otherwise(0)).as("totalReassignments")

                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ASSIGNED_TO_ASSISTANT),
                                        Criteria.where("reassignmentCount").gt(0)
                                )
                        ).thenValueOf("itemQuantity").otherwise(0)).as("reassignmentQuantity")

                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ASSIGNED_TO_ASSISTANT),
                                        Criteria.where("reassignmentCount").gt(0)
                                )
                        ).thenValueOf("totalRevenueGenerated").otherwise(0)).as("reassignmentRevenue")

                        // ✅ Total assigned (to tailor)
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("assignedTo").is(employeeId),
                                        Criteria.where("type").is(ActivityType.ASSIGNED_TO_TAILOR)
                                )
                        ).then(1).otherwise(0)).as("totalAssigned")

                        // ✅ Total completed (assistant work by employee)
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("employeeId").is(employeeId),
                                        Criteria.where("type").is(ActivityType.ASSIGNED_TO_ASSISTANT)
                                )
                        ).then(1).otherwise(0)).as("totalCompleted")
        );

        Map<String, Object> result = mongoTemplate.aggregate(
                agg, "employee_activities", Map.class
        ).getUniqueMappedResult();

        return result != null ? result : new HashMap<>();
    }

    private Map<String, Object> getEmployeeAnalyticsForShopAssistant(
            String shopId, String employeeId, Date from, Date to) {

        // --- Normalize frontend yyyy-MM-dd to IST day → then UTC bounds ---
        Date fromUtc = null, toUtc = null;
        if (from != null) {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
            cal.setTime(from);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            // ✅ Start of day IST → UTC
            fromUtc = Date.from(cal.toInstant().atZone(ZoneId.of("Asia/Kolkata"))
                    .withZoneSameInstant(ZoneOffset.UTC).toInstant());
        }
        if (to != null) {
            // ✅ End of day IST → UTC
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
            cal.setTime(to);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);

            toUtc = Date.from(cal.toInstant().atZone(ZoneId.of("Asia/Kolkata"))
                    .withZoneSameInstant(ZoneOffset.UTC).toInstant());
        }

        // --- Match criteria: shop + employee (id or assigned) + strict date ---
        Criteria matchCriteria = new Criteria().andOperator(
                Criteria.where("shopId").is(shopId),
                new Criteria().orOperator(
                        Criteria.where("employeeId").is(employeeId),
                        Criteria.where("assignedTo").is(employeeId)
                ),
                Criteria.where("timestamp").gte(fromUtc).lte(toUtc) // ✅ Strict UTC bounds
        );

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.group()

                        // ✅ Assigned normally (assistant, no reassignments)
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ORDER_COMPLETED),
                                        Criteria.where("reassignmentCount").is(0)
                                )
                        ).then(1).otherwise(0)).as("assigned")

                        // ✅ Normal totals
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ORDER_COMPLETED),
                                        Criteria.where("reassignmentCount").is(0)
                                )
                        ).thenValueOf("itemQuantity").otherwise(0)).as("totalQuantity")

                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ORDER_COMPLETED),
                                        Criteria.where("reassignmentCount").is(0)
                                )
                        ).thenValueOf("totalRevenueGenerated").otherwise(0)).as("totalRevenue")

                        // ✅ Reassignment totals
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ORDER_COMPLETED),
                                        Criteria.where("reassignmentCount").gt(0)
                                )
                        ).thenValueOf("reassignmentCount").otherwise(0)).as("totalReassignments")

                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ORDER_COMPLETED),
                                        Criteria.where("reassignmentCount").gt(0)
                                )
                        ).thenValueOf("itemQuantity").otherwise(0)).as("reassignmentQuantity")

                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("type").is(ActivityType.ORDER_COMPLETED),
                                        Criteria.where("reassignmentCount").gt(0)
                                )
                        ).thenValueOf("totalRevenueGenerated").otherwise(0)).as("reassignmentRevenue")

                        // ✅ Total assigned (to tailor)
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("assignedTo").is(employeeId),
                                        Criteria.where("type").is(ActivityType.ASSIGNED_TO_ASSISTANT)
                                )
                        ).then(1).otherwise(0)).as("totalAssigned")

                        // ✅ Total completed (assistant work by employee)
                        .sum(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("employeeId").is(employeeId),
                                        Criteria.where("type").is(ActivityType.ORDER_COMPLETED)
                                )
                        ).then(1).otherwise(0)).as("totalCompleted")
        );

        Map<String, Object> result = mongoTemplate.aggregate(
                agg, "employee_activities", Map.class
        ).getUniqueMappedResult();

        return result != null ? result : new HashMap<>();
    }

//    private Map<String, Object> getAssistantAnalytics(String shopId, String employeeId, Date from, Date to) {
//        Criteria matchCriteria = Criteria.where("shopId").is(shopId).and("employeeId").is(employeeId);
//        if (from != null && to != null) {
//            matchCriteria = matchCriteria.and("timestamp").gte(from).lte(to);
//        }
//
//        Aggregation agg = Aggregation.newAggregation(
//                Aggregation.match(matchCriteria),
//                Aggregation.group()
//                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.ORDER_COMPLETED)).then(1).otherwise(0)).as("completed")
//        );
//
//        Map<String, Object> result = mongoTemplate.aggregate(agg, "employee_activities", Map.class).getUniqueMappedResult();
//        return result != null ? result : new HashMap<>();
//    }
//


    @Override
    public Map<String, Object> getEmployeeAnalytics(String shopId, String employeeId, Date from, Date to) {

        // Common match criteria
        Criteria matchCriteria = Criteria.where("shopId").is(shopId).and("employeeId").is(employeeId);
        if (from != null && to != null) {
            matchCriteria = matchCriteria.and("timestamp").gte(from).lte(to);
        }

        // Group by nothing (global counts)
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.group()
                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.ASSIGNED_TO_TAILOR)).then(1).otherwise(0)).as("assigned")
                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.TAILOR_STARTED)).then(1).otherwise(0)).as("cutting")
                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.TAILOR_COMPLETED)).then(1).otherwise(0)).as("completed")
                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.INVOICE_DELETED)).then(1).otherwise(0)).as("deletedInvoices")
        );

        Map<String, Object> counts = mongoTemplate.aggregate(agg, "employee_activities", Map.class).getUniqueMappedResult();
        if (counts == null) counts = new HashMap<>();

        // ✅ Revenue aggregation (can also be merged into single pipeline if needed)
        Criteria invCriteria = Criteria.where("shopId").is(shopId);
        if (from != null && to != null) {
            invCriteria = invCriteria.and("invoiceDate").gte(from).lte(to);
        }

        Aggregation revenueAgg = Aggregation.newAggregation(
                Aggregation.match(invCriteria),
                Aggregation.group()
                        .sum("totalAmount").as("totalRevenue")
                        .count().as("invoiceCount")
        );

        Map<String, Object> revenueMap = mongoTemplate.aggregate(revenueAgg, "invoice", Map.class).getUniqueMappedResult();
        if (revenueMap == null) revenueMap = new HashMap<>();

        // ✅ Merge results
        Map<String, Object> result = new HashMap<>();
        result.put("assigned", counts.getOrDefault("assigned", 0));
        result.put("cutting", counts.getOrDefault("cutting", 0));
        result.put("completed", counts.getOrDefault("completed", 0));
        result.put("deletedInvoices", counts.getOrDefault("deletedInvoices", 0));
        result.put("totalRevenue", revenueMap.getOrDefault("totalRevenue", 0));
        result.put("invoiceCount", revenueMap.getOrDefault("invoiceCount", 0));

        return result;
    }


}

