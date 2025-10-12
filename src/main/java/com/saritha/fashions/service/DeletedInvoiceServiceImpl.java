package com.saritha.fashions.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.service.Interface.DeletedInvoiceService;
import com.saritha.fashions.util.AuthUtil;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class DeletedInvoiceServiceImpl implements DeletedInvoiceService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AuthUtil authUtil;

    @Override
    public Map<String, Object> getDeletedInvoices(
            int pageNumber, int pageSize,
            String invoiceId, String customerName, String paymentStatus,
            String fromDate, String toDate
    ) {

        List<Criteria> criteriaList = new ArrayList<>();

        String userId = authUtil.loggedInUser().getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        // Always filter by shop
        criteriaList.add(Criteria.where("invoice.shopId").is(shopId));

        // Optional filters
        if (invoiceId != null && !invoiceId.isBlank()) {
            criteriaList.add(Criteria.where("invoice.invoiceId").regex(invoiceId, "i"));
        }

        if (customerName != null && !customerName.isBlank()) {
            criteriaList.add(Criteria.where("invoice.customer.name").regex(customerName, "i"));
        }

        if (paymentStatus != null && !paymentStatus.isBlank()) {
            criteriaList.add(Criteria.where("invoice.paymentStatus").is(paymentStatus));
        }

        if (fromDate != null && toDate != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date from = sdf.parse(fromDate);
                Date to = sdf.parse(toDate);

                criteriaList.add(
                        Criteria.where("invoice.invoiceDate")
                                .gte(from)
                                .lte(to)
                );
            } catch (Exception e) {
                throw new RuntimeException("Invalid date format. Use yyyy-MM-dd");
            }
        }

        Criteria finalCriteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));

        // Aggregation stages
        MatchOperation matchStage = Aggregation.match(finalCriteria);
        SortOperation sortStage = Aggregation.sort(Sort.Direction.DESC, "deletedAt");
        SkipOperation skipStage = Aggregation.skip((long) pageNumber * pageSize);
        LimitOperation limitStage = Aggregation.limit(pageSize);

        ProjectionOperation projectStage = Aggregation.project()
                .and("invoice.id").as("id")
                .and("invoice.invoiceId").as("invoiceId")
                .and("invoice.invoiceDate").as("invoiceDate")
                .and("invoice.totalAmount").as("totalAmount")
                .and("invoice.advancePaid").as("advancePaid")
                .and("invoice.balanceDue").as("balanceDue")
                .and("invoice.paymentMethod").as("paymentMethod")
                .and("invoice.paymentStatus").as("paymentStatus")
                .and("invoice.customer").as("customer")
                .and("deletedAt").as("deletedAt")
                .and("deletedBy").as("deletedBy");

        Aggregation aggregation = Aggregation.newAggregation(
                matchStage, sortStage, skipStage, limitStage, projectStage
        );

        List<Document> results = mongoTemplate.aggregate(
                aggregation, "deleted_invoices", Document.class
        ).getMappedResults();

        // Count query for pagination
        long totalElements = mongoTemplate.count(new Query(finalCriteria), "deleted_invoices");

        Map<String, Object> response = new HashMap<>();
        response.put("content", results);
        response.put("totalElements", totalElements);
        response.put("pageNumber", pageNumber);
        response.put("pageSize", pageSize);

        return response;
    }
}

