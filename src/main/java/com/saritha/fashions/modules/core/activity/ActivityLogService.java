package com.saritha.fashions.modules.core.activity;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;

/**
 * Records employee activities in "employee_activities". Replaces the legacy EmployeeActivityService
 * for the modules (the reports module reads the same collection).
 *
 * The stored document has exactly the shape the legacy service wrote (model.EmployeeActivity), so
 * old and new rows are read the same way:
 * {shopId, invoiceId, invoiceDbId, employeeId, employeeName, type (enum name), timestamp, notes,
 *  itemQuantity, itemPrice, totalRevenueGenerated (= quantity × price), reassignmentCount, assignedTo}.
 * Null values are left out, as before. The legacy "_class" type hint is not written; nothing
 * reads it (all readers query the collection with an explicit type or as raw documents).
 */
@Service
public class ActivityLogService {

    static final String COLLECTION = "employee_activities";

    private final MongoTemplate mongoTemplate;

    public ActivityLogService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Inserts the activity, time-stamped now. */
    public void record(ActivityEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(entry.type(), "type");
        mongoTemplate.insert(toDocument(entry, new Date()), COLLECTION);
    }

    static Document toDocument(ActivityEntry entry, Date timestamp) {
        Document document = new Document();
        putIfNotNull(document, "shopId", entry.shopId());
        putIfNotNull(document, "invoiceId", entry.invoiceId());
        putIfNotNull(document, "invoiceDbId", entry.invoiceDbId());
        putIfNotNull(document, "employeeId", entry.employeeId());
        putIfNotNull(document, "employeeName", entry.employeeName());
        document.put("type", entry.type().name());
        document.put("timestamp", timestamp);
        putIfNotNull(document, "notes", entry.notes());
        document.put("itemQuantity", entry.itemQuantity());
        document.put("itemPrice", entry.itemPrice());
        document.put("totalRevenueGenerated", entry.itemQuantity() * entry.itemPrice());
        document.put("reassignmentCount", entry.reassignmentCount());
        putIfNotNull(document, "assignedTo", entry.assignedTo());
        return document;
    }

    private static void putIfNotNull(Document document, String key, Object value) {
        if (value != null) {
            document.put(key, value);
        }
    }
}
