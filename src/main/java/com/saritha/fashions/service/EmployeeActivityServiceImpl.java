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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        switch (role) {
            case "SHOP_OWNER":
                return getShopOwnerAnalytics(shopId, from, to);
            case "TAILOR_MASTER":
                return getTailorMasterAnalytics(shopId, employeeId, from, to);
            case "TAILOR":
                return getTailorAnalytics(shopId, employeeId, from, to);
            case "SHOP_ASSISTANT":
                return getAssistantAnalytics(shopId, employeeId, from, to);
            default:
                return Collections.emptyMap();
        }
    }

//    private Map<String, Object> getShopOwnerAnalytics(String shopId, Date from, Date to) {
//        Criteria criteria = Criteria.where("shopId").is(shopId);
//        if (from != null && to != null) {
//            criteria = criteria.and("invoiceDate").gte(from).lte(to);
//        }
//
//        Aggregation agg = Aggregation.newAggregation(
//                Aggregation.match(criteria),
//                Aggregation.group()
//                        .sum("totalAmount").as("totalRevenue")
//                        .count().as("invoiceCount")
//                        .sum(ConditionalOperators.when(Criteria.where("paymentStatus").is("PAID")).then(1).otherwise(0)).as("paidInvoices")
//                        .sum(ConditionalOperators.when(Criteria.where("paymentStatus").is("UNPAID")).then(1).otherwise(0)).as("unpaidInvoices")
//        );
//
//        Map<String, Object> result = mongoTemplate.aggregate(agg, "invoice", Map.class).getUniqueMappedResult();
//        return result != null ? result : new HashMap<>();
//    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getShopOwnerAnalytics(String shopId, Date from, Date to) {
        // --- Build $match stage (shopId + date filter) ---
        Document matchDoc = new Document("shopId", shopId);
        if (from != null && to != null) {
            matchDoc.append("invoiceDate", new Document("$gte", from).append("$lte", to));
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
        if (from != null && to != null) {
            andExpr.add(new Document("$gte", Arrays.asList("$receiptDate", from)));
            andExpr.add(new Document("$lte", Arrays.asList("$receiptDate", to)));
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

        // --- Replace root ---
        Document replaceRoot = new Document("$replaceRoot",
                new Document("newRoot", new Document("$mergeObjects", Arrays.asList("$status",
                        new Document("invoiceCount", "$invoiceCount").append("receiptCount", "$receiptCount"))))
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
        long totalItems = ((Number) result.getOrDefault("totalItems", 0)).longValue();

        // Average revenue per invoice
        double avg = invoiceCountVal > 0 ? (totalRevenue / invoiceCountVal) : 0.0;
        avg = Math.round(avg * 100.0) / 100.0; // round to 2 decimals

        result.put("totalRevenue", totalRevenue);
        result.put("invoiceCount", invoiceCountVal);
        result.put("receiptCount", receiptCountVal);
        result.put("totalItems", totalItems);
        result.put("averageRevenuePerInvoice", avg);

        return result;
    }


//    @SuppressWarnings("unchecked")
//    private Map<String, Object> getShopOwnerAnalytics(String shopId, Date from, Date to) {
//        // --- build match doc (shopId + optional date range) ---
//        Document matchDoc = new Document("shopId", shopId);
//        if (from != null && to != null) {
//            matchDoc.append("invoiceDate", new Document("$gte", from).append("$lte", to));
//        }
//
//        // --- pipeline stages as raw Documents (faithful to shell pipeline) ---
//        AggregationOperation matchStage = context -> new Document("$match", matchDoc);
//        AggregationOperation unwindItems = context -> new Document("$unwind", "$items");
//        AggregationOperation matchValidItems = context -> new Document("$match",
//                new Document("items.itemOrderStatus", new Document("$exists", true).append("$ne", null)));
//
//        // --- group that computes statuses, totalRevenue and totalItems (sum of quantities) ---
//        Document statusGroup = new Document("$group", new Document("_id", null)
//                .append("ORDER_ACCEPTED", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "ORDER_ACCEPTED")), 1, 0))))
//                .append("CUTTING", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "CUTTING")), 1, 0))))
//                .append("TAILOR_ASSIGNED", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "TAILOR_ASSIGNED")), 1, 0))))
//                .append("STITCHING", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "STITCHING")), 1, 0))))
//                .append("SHOP_ASSISTANT_ASSIGNED", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "SHOP_ASSISTANT_ASSIGNED")), 1, 0))))
//                .append("IRONING", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "IRONING")), 1, 0))))
//                .append("HEMMING", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "HEMMING")), 1, 0))))
//                .append("ORDER_COMPLETED", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "ORDER_COMPLETED")), 1, 0))))
//                .append("ORDER_DELIVERED", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "ORDER_DELIVERED")), 1, 0))))
//                .append("UNDER_PRODUCTION", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "UNDER_PRODUCTION")), 1, 0))))
//                .append("ASSIGNED", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "ASSIGNED")), 1, 0))))
//                .append("UNASSIGNED_DUE_TO_USER_DELETION", new Document("$sum",
//                        new Document("$cond", Arrays.asList(new Document("$eq", Arrays.asList("$items.itemOrderStatus", "UNASSIGNED_DUE_TO_USER_DELETION")), 1, 0))))
//                // totalRevenue: sum( (items.itemPrice or 0) * (items.itemQuantity or 1) )
//                .append("totalRevenue",
//                        new Document("$sum", new Document("$multiply", Arrays.asList(
//                                new Document("$ifNull", Arrays.asList("$items.itemPrice", 0)),
//                                new Document("$ifNull", Arrays.asList("$items.itemQuantity", 1))
//                        ))))
//                // totalItems: sum(items.itemQuantity or 1)
//                .append("totalItems",
//                        new Document("$sum", new Document("$ifNull", Arrays.asList("$items.itemQuantity", 1))))
//                // OTHER_STATUS_COUNT: count items not in known list
//                .append("OTHER_STATUS_COUNT",
//                        new Document("$sum", new Document("$cond", Arrays.asList(
//                                new Document("$not", new Document("$in", Arrays.asList("$items.itemOrderStatus",
//                                        Arrays.asList("ORDER_ACCEPTED", "CUTTING", "TAILOR_ASSIGNED", "STITCHING",
//                                                "SHOP_ASSISTANT_ASSIGNED", "IRONING", "HEMMING",
//                                                "ORDER_COMPLETED", "ORDER_DELIVERED", "UNDER_PRODUCTION",
//                                                "ASSIGNED", "UNASSIGNED_DUE_TO_USER_DELETION")))),
//                                1,
//                                0
//                        ))))
//        );
//
//        // $addFields to compute totalStatusCount
//        Document statusAddFields = new Document("$addFields", new Document("totalStatusCount",
//                new Document("$add", Arrays.asList(
//                        "$ORDER_ACCEPTED", "$CUTTING", "$TAILOR_ASSIGNED", "$STITCHING",
//                        "$SHOP_ASSISTANT_ASSIGNED", "$IRONING", "$HEMMING",
//                        "$ORDER_COMPLETED", "$ORDER_DELIVERED", "$UNDER_PRODUCTION",
//                        "$ASSIGNED", "$UNASSIGNED_DUE_TO_USER_DELETION", "$OTHER_STATUS_COUNT"
//                ))));
//
//        // project final fields we want from status pipeline
//        Document statusProject = new Document("$project", new Document("_id", 0)
//                .append("ORDER_ACCEPTED", 1).append("CUTTING", 1).append("TAILOR_ASSIGNED", 1).append("STITCHING", 1)
//                .append("SHOP_ASSISTANT_ASSIGNED", 1).append("IRONING", 1).append("HEMMING", 1)
//                .append("ORDER_COMPLETED", 1).append("ORDER_DELIVERED", 1).append("UNDER_PRODUCTION", 1)
//                .append("ASSIGNED", 1).append("UNASSIGNED_DUE_TO_USER_DELETION", 1)
//                .append("OTHER_STATUS_COUNT", 1)
//                .append("totalStatusCount", 1)
//                .append("totalRevenue", 1)
//                .append("totalItems", 1)
//        );
//
//        // invoiceCount pipeline: group by invoiceId then count distinct invoiceId
//        Document invoiceGroupById = new Document("$group", new Document("_id", "$invoiceId"));
//        Document invoiceCountGroup = new Document("$count", "count");
//
//        // facet doc
//        Document facetDoc = new Document("$facet",
//                new Document("statusAndRevenue", Arrays.asList(statusGroup, statusAddFields, statusProject))
//                        .append("invoiceCount", Arrays.asList(invoiceGroupById, invoiceCountGroup))
//        );
//        AggregationOperation facetStage = context -> facetDoc;
//
//        // project to extract first element of statusAndRevenue and invoiceCount
//        Document projectAfterFacet = new Document("$project",
//                new Document("status", new Document("$arrayElemAt", Arrays.asList("$statusAndRevenue", 0)))
//                        .append("invoiceCount", new Document("$ifNull", Arrays.asList(new Document("$arrayElemAt", Arrays.asList("$invoiceCount.count", 0)), 0)))
//        );
//        AggregationOperation project1 = context -> projectAfterFacet;
//
//        // --- lookup receipts with pipeline (use let variables) ---
//        Document letDoc = new Document("shopId", shopId);
//        if (from != null && to != null) {
//            letDoc.append("startDate", from).append("endDate", to);
//        } else {
//            letDoc.append("startDate", null).append("endDate", null);
//        }
//
//        // build $match $expr for receipts; include date filters only if provided
//        List<Object> andExpr = new ArrayList<>();
//        andExpr.add(new Document("$eq", Arrays.asList("$shopId", "$$shopId")));
//        if (from != null && to != null) {
//            andExpr.add(new Document("$gte", Arrays.asList("$receiptDate", "$$startDate")));
//            andExpr.add(new Document("$lte", Arrays.asList("$receiptDate", "$$endDate")));
//        }
//
//        Document matchReceiptsExpr = new Document("$expr", new Document("$and", andExpr));
//        Document lookupReceiptMatch = new Document("$match", matchReceiptsExpr);
//        Document lookupReceiptCount = new Document("$count", "count");
//
//        Document lookupDoc = new Document("$lookup",
//                new Document("from", "receipt")
//                        .append("let", letDoc)
//                        .append("pipeline", Arrays.asList(lookupReceiptMatch, lookupReceiptCount))
//                        .append("as", "receiptStats")
//        );
//        AggregationOperation lookupStage = context -> lookupDoc;
//
//        // addFields to extract receiptCount
//        Document addFieldsDoc = new Document("$addFields",
//                new Document("receiptCount", new Document("$ifNull", Arrays.asList(new Document("$arrayElemAt", Arrays.asList("$receiptStats.count", 0)), 0)))
//        );
//        AggregationOperation addFieldsStage = context -> addFieldsDoc;
//
//        // drop receiptStats array
//        Document projectDropReceiptStats = new Document("$project", new Document("receiptStats", 0));
//        AggregationOperation projectDropStage = context -> projectDropReceiptStats;
//
//        // finally merge status object into root and include invoiceCount & receiptCount
//        Document replaceRootDoc = new Document("$replaceRoot",
//                new Document("newRoot",
//                        new Document("$mergeObjects", Arrays.asList("$status", new Document("invoiceCount", "$invoiceCount").append("receiptCount", "$receiptCount")))
//                )
//        );
//        AggregationOperation replaceRootStage = context -> replaceRootDoc;
//
//        // build pipeline
//        List<AggregationOperation> pipeline = Arrays.asList(
//                matchStage,
//                unwindItems,
//                matchValidItems,
//                facetStage,
//                project1,
//                lookupStage,
//                addFieldsStage,
//                projectDropStage,
//                replaceRootStage
//        );
//
//        Aggregation agg = Aggregation.newAggregation(pipeline);
//
//        // execute aggregation (single DB call)
//        Document out = mongoTemplate.aggregate(agg, "invoice", Document.class).getUniqueMappedResult();
//
//        Map<String, Object> result = new HashMap<>();
//        if (out != null) {
//            // copy all fields except _id
//            for (Map.Entry<String, Object> e : out.entrySet()) {
//                if ("_id".equals(e.getKey())) continue;
//                result.put(e.getKey(), e.getValue());
//            }
//        }
//
//        // parse numeric values safely
//        double totalRevenue = 0.0;
//        Object trObj = result.get("totalRevenue");
//        if (trObj instanceof Number) totalRevenue = ((Number) trObj).doubleValue();
//        else if (trObj instanceof String) {
//            try { totalRevenue = Double.parseDouble((String) trObj); } catch (Exception ignored) {}
//        }
//
//        long invoiceCountVal = 0L;
//        Object invObj = result.get("invoiceCount");
//        if (invObj instanceof Number) invoiceCountVal = ((Number) invObj).longValue();
//        else if (invObj instanceof String) {
//            try { invoiceCountVal = Long.parseLong((String) invObj); } catch (Exception ignored) {}
//        }
//
//        long receiptCountVal = 0L;
//        Object rObj = result.get("receiptCount");
//        if (rObj instanceof Number) receiptCountVal = ((Number) rObj).longValue();
//        else if (rObj instanceof String) {
//            try { receiptCountVal = Long.parseLong((String) rObj); } catch (Exception ignored) {}
//        }
//
//        // FALLBACK: if receiptCount from lookup is 0 but you expect receipts exist, do a direct count()
//        // (this avoids missing receipts due to subtle pipeline issues like timezone/storage differences)
//        if (receiptCountVal == 0) {
//            try {
//                org.springframework.data.mongodb.core.query.Criteria rc = org.springframework.data.mongodb.core.query.Criteria.where("shopId").is(shopId);
//                if (from != null && to != null) rc = rc.and("receiptDate").gte(from).lte(to);
//                receiptCountVal = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(rc), "receipt");
//                result.put("receiptCount", receiptCountVal);
//            } catch (Exception ex) {
//                // ignore fallback errors but keep receiptCount whatever we have from lookup
//            }
//        }
//
//        // totalItems
//        long totalItemsVal = 0L;
//        Object tiObj = result.get("totalItems");
//        if (tiObj instanceof Number) totalItemsVal = ((Number) tiObj).longValue();
//        else if (tiObj instanceof String) {
//            try { totalItemsVal = Long.parseLong((String) tiObj); } catch (Exception ignored) {}
//        }
//
//        // compute rounded averageRevenuePerInvoice (2 decimal places)
//        double avg = 0.0;
//        if (invoiceCountVal > 0) {
//            avg = totalRevenue / invoiceCountVal;
//        }
//        // round to 2 decimals (HALF_UP)
//        BigDecimal bd = BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
//        double avgRounded = bd.doubleValue();
//
//        // ensure final numbers in result
//        result.put("totalRevenue", totalRevenue);
//        result.put("invoiceCount", invoiceCountVal);
//        result.put("receiptCount", receiptCountVal);
//        result.put("totalItems", totalItemsVal);
//        result.put("averageRevenuePerInvoice", avgRounded);
//
//        return result;
//    }


    private Map<String, Object> getTailorMasterAnalytics(String shopId, String employeeId, Date from, Date to) {
        Criteria matchCriteria = Criteria.where("shopId").is(shopId).and("employeeId").is(employeeId);
        if (from != null && to != null) {
            matchCriteria = matchCriteria.and("timestamp").gte(from).lte(to);
        }

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.group()
                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.ASSIGNED_TO_TAILOR)).then(1).otherwise(0)).as("assigned")
                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.TAILOR_STARTED)).then(1).otherwise(0)).as("cutting")
                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.TAILOR_COMPLETED)).then(1).otherwise(0)).as("completed")
        );

        Map<String, Object> result = mongoTemplate.aggregate(agg, "employee_activities", Map.class).getUniqueMappedResult();
        return result != null ? result : new HashMap<>();
    }

    private Map<String, Object> getTailorAnalytics(String shopId, String employeeId, Date from, Date to) {
        Criteria matchCriteria = Criteria.where("shopId").is(shopId).and("employeeId").is(employeeId);
        if (from != null && to != null) {
            matchCriteria = matchCriteria.and("timestamp").gte(from).lte(to);
        }

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.group()
                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.ASSIGNED_TO_TAILOR)).then(1).otherwise(0)).as("assigned")
                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.TAILOR_COMPLETED)).then(1).otherwise(0)).as("completed")
        );

        Map<String, Object> result = mongoTemplate.aggregate(agg, "employee_activities", Map.class).getUniqueMappedResult();
        return result != null ? result : new HashMap<>();
    }

    private Map<String, Object> getAssistantAnalytics(String shopId, String employeeId, Date from, Date to) {
        Criteria matchCriteria = Criteria.where("shopId").is(shopId).and("employeeId").is(employeeId);
        if (from != null && to != null) {
            matchCriteria = matchCriteria.and("timestamp").gte(from).lte(to);
        }

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.group()
                        .sum(ConditionalOperators.when(Criteria.where("type").is(ActivityType.ASSISTANT_COMPLETED)).then(1).otherwise(0)).as("completed")
        );

        Map<String, Object> result = mongoTemplate.aggregate(agg, "employee_activities", Map.class).getUniqueMappedResult();
        return result != null ? result : new HashMap<>();
    }



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


//    @Override
//    public Map<String, Object> getEmployeeAnalytics(String shopId, String employeeId, Date from, Date to) {
//
//        Criteria criteria = Criteria.where("shopId").is(shopId).and("employeeId").is(employeeId);
//        if (from != null && to != null) criteria.and("timestamp").gte(from).lte(to);
//
//        // Example counts (you can refine per role)
//        long assigned = mongoTemplate.count(new Query(criteria.and("type").is(ActivityType.ASSIGNED_TO_TAILOR)), EmployeeActivity.class);
//        long cutting = mongoTemplate.count(new Query(criteria.and("type").is(ActivityType.TAILOR_STARTED)), EmployeeActivity.class);
//        long completed = mongoTemplate.count(new Query(criteria.and("type").is(ActivityType.TAILOR_COMPLETED)), EmployeeActivity.class);
//        long deletedInvoices = mongoTemplate.count(new Query(criteria.and("type").is(ActivityType.INVOICE_DELETED)), EmployeeActivity.class);
//
//        // revenue via invoice collection
//        Criteria invCriteria = Criteria.where("shopId").is(shopId);
//        if (from != null && to != null) invCriteria.and("invoiceDate").gte(from).lte(to);
//
//        Aggregation agg = Aggregation.newAggregation(
//                Aggregation.match(invCriteria),
//                Aggregation.group().sum("totalAmount").as("totalRevenue")
//                        .count().as("invoiceCount")
//        );
//        Map<String, Object> revenueMap = mongoTemplate.aggregate(agg, "invoice", Map.class).getUniqueMappedResult();
//
//        Map<String, Object> result = new HashMap<>();
//        result.put("assigned", assigned);
//        result.put("cutting", cutting);
//        result.put("completed", completed);
//        result.put("deletedInvoices", deletedInvoices);
//        result.put("totalRevenue", revenueMap != null ? revenueMap.get("totalRevenue") : 0);
//        result.put("invoiceCount", revenueMap != null ? revenueMap.get("invoiceCount") : 0);
//
//        return result;
//    }



}

