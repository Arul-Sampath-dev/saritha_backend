package com.saritha.fashions.service;

import com.mongodb.BasicDBObject;
import com.saritha.fashions.model.ActivityType;
import com.saritha.fashions.model.EmployeeActivity;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.activity.DailyCountDTO;
import com.saritha.fashions.payload.activity.EmployeeActivitySummaryDTO;
import com.saritha.fashions.service.Interface.EmployeeActivityService;
import com.saritha.fashions.util.DateTimeUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class EmployeeActivityServiceImpl implements EmployeeActivityService {

    @Autowired
    private MongoTemplate mongoTemplate;

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


}

