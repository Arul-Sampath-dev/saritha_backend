package com.saritha.fashions.modules.production.repository;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.FacetOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Data access for the item workflow, on the raw "invoice", "shops", "users" and
 * "customers" collections (no dependency on other modules' models).
 *
 * All writes are single atomic updates of ONE item, guarded by the item's expected
 * current state ("compare-and-set"). This replaces the old pattern of loading and
 * re-saving the whole invoice, which could overwrite a concurrent update of another
 * item of the same invoice.
 */
@Repository
public class ItemWorkflowRepository {

    private static final String INVOICES = "invoice";
    private static final String SHOPS = "shops";
    private static final String USERS = "users";
    private static final String CUSTOMERS = "customers";
    private static final String DELIVERED = "ORDER_DELIVERED";

    private final MongoTemplate mongoTemplate;

    public ItemWorkflowRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * An item plus the invoice fields the workflow needs.
     *
     * @param customerRef the invoice's customer DBRef value (resolved separately)
     */
    public record ItemSnapshot(String invoiceDbId, String invoiceId, String shopId, String invoiceOrderStatus,
                               Object customerRef, Date invoiceDate, String expectedDeliveryDate, Document item) {

        public boolean invoiceDelivered() {
            return DELIVERED.equals(invoiceOrderStatus);
        }
    }

    // ------------------------------------------------------------------ shop / users

    public Optional<String> findShopIdByEmployeeId(String userId) {
        Query query = new Query(Criteria.where("employeeIds").is(userId)).limit(1);
        query.fields().include("_id");
        Document shop = mongoTemplate.findOne(query, Document.class, SHOPS);
        return Optional.ofNullable(shop).map(document -> document.get("_id").toString());
    }

    /** True if the user works in the shop (shops.employeeIds) AND has the given role. */
    public boolean isShopMemberWithRole(String shopId, String userId, String roleName) {
        boolean inShop = mongoTemplate.exists(
                new Query(Criteria.where("_id").is(toId(shopId)).and("employeeIds").is(userId)), SHOPS);
        return inShop && mongoTemplate.exists(
                new Query(Criteria.where("_id").is(toId(userId)).and("roles.roleName").is(roleName)), USERS);
    }

    /** Users of the shop with the given role, as [userId, name], sorted by name. */
    public List<String[]> findShopMembersWithRole(String shopId, String roleName) {
        Query shopQuery = new Query(Criteria.where("_id").is(toId(shopId)));
        shopQuery.fields().include("employeeIds");
        Document shop = mongoTemplate.findOne(shopQuery, Document.class, SHOPS);
        if (shop == null || !(shop.get("employeeIds") instanceof List<?> employeeIds) || employeeIds.isEmpty()) {
            return List.of();
        }

        List<Object> ids = employeeIds.stream().filter(Objects::nonNull).map(id -> toId(id.toString())).toList();
        Query userQuery = new Query(Criteria.where("_id").in(ids).and("roles.roleName").is(roleName))
                .with(Sort.by("name"));
        userQuery.fields().include("_id").include("name");

        return mongoTemplate.find(userQuery, Document.class, USERS).stream()
                .map(user -> new String[]{user.get("_id").toString(), user.getString("name")})
                .toList();
    }

    /** userId → name for the given ids (unknown ids are left out). */
    public Map<String, String> findUserNames(Collection<String> userIds) {
        List<Object> ids = userIds.stream().filter(Objects::nonNull).distinct().map(ItemWorkflowRepository::toId).toList();
        Map<String, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        Query query = new Query(Criteria.where("_id").in(ids));
        query.fields().include("_id").include("name");
        mongoTemplate.find(query, Document.class, USERS)
                .forEach(user -> names.put(user.get("_id").toString(), user.getString("name")));
        return names;
    }

    /** customer _id → name for the given ids. */
    public Map<String, String> findCustomerNames(Collection<Object> customerIds) {
        List<Object> ids = customerIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<String, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        Query query = new Query(Criteria.where("_id").in(ids));
        query.fields().include("_id").include("name");
        mongoTemplate.find(query, Document.class, CUSTOMERS)
                .forEach(customer -> names.put(customer.get("_id").toString(), customer.getString("name")));
        return names;
    }

    // ------------------------------------------------------------------ items

    /** Loads one item of an invoice of the shop (only that item is read). */
    public Optional<ItemSnapshot> findItem(String shopId, String invoiceId, String itemUniqueId) {
        Query query = new Query(Criteria.where("invoiceId").is(invoiceId)
                .and("shopId").is(shopId)
                .and("items.itemUniqueId").is(itemUniqueId));
        query.fields()
                .include("invoiceId").include("shopId").include("orderStatus").include("customer")
                .include("invoiceDate").include("expectedDeliveryDate")
                .elemMatch("items", Criteria.where("itemUniqueId").is(itemUniqueId));

        Document invoice = mongoTemplate.findOne(query, Document.class, INVOICES);
        if (invoice == null || !(invoice.get("items") instanceof List<?> items) || items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(invoice, (Document) items.get(0)));
    }

    /** Every item of one invoice of the shop (empty when the invoice is not found). */
    public List<ItemSnapshot> findInvoiceItems(String shopId, String invoiceId) {
        Query query = new Query(Criteria.where("invoiceId").is(invoiceId).and("shopId").is(shopId));
        query.fields()
                .include("invoiceId").include("shopId").include("orderStatus").include("customer")
                .include("invoiceDate").include("expectedDeliveryDate").include("items");
        Document invoice = mongoTemplate.findOne(query, Document.class, INVOICES);
        if (invoice == null || !(invoice.get("items") instanceof List<?> items)) {
            return List.of();
        }
        List<ItemSnapshot> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Document document) {
                result.add(toSnapshot(invoice, document));
            }
        }
        return result;
    }

    /**
     * Atomically updates ONE item, only if it still matches the expected state.
     *
     * @param expectedStatus   item must still have this itemOrderStatus
     * @param expectedFields   item fields that must still have these values (null = missing/null)
     * @param setFields        item fields to set
     * @param historyEntry     appended to the item's assignmentHistory
     * @param historyIsArray   whether assignmentHistory is currently an array (if not, it is replaced)
     * @return true if the item was updated; false if it changed in the meantime
     */
    public boolean updateItemIfUnchanged(String shopId, String invoiceId, String itemUniqueId,
                                         String expectedStatus, Map<String, Object> expectedFields,
                                         Map<String, Object> setFields, Document historyEntry,
                                         boolean historyIsArray) {
        Criteria itemMatch = Criteria.where("itemUniqueId").is(itemUniqueId).and("itemOrderStatus").is(expectedStatus);
        Criteria arrayFilter = Criteria.where("elem.itemUniqueId").is(itemUniqueId)
                .and("elem.itemOrderStatus").is(expectedStatus);
        for (Map.Entry<String, Object> expected : expectedFields.entrySet()) {
            itemMatch = itemMatch.and(expected.getKey()).is(expected.getValue());
            arrayFilter = arrayFilter.and("elem." + expected.getKey()).is(expected.getValue());
        }

        Query query = new Query(Criteria.where("invoiceId").is(invoiceId)
                .and("shopId").is(shopId)
                .and("orderStatus").ne(DELIVERED)
                .and("items").elemMatch(itemMatch));

        Update update = new Update();
        setFields.forEach((field, value) -> update.set("items.$[elem]." + field, value));
        if (historyIsArray) {
            update.push("items.$[elem].assignmentHistory", historyEntry);
        } else {
            update.set("items.$[elem].assignmentHistory", new ArrayList<>(List.of(historyEntry)));
        }
        update.filterArray(arrayFilter);

        return mongoTemplate.updateFirst(query, update, INVOICES).getModifiedCount() > 0;
    }

    /**
     * Items of the shop's undelivered invoices matching the item criteria, oldest invoice
     * first, one page at a time.
     *
     * @param statuses         item statuses to include
     * @param assigneeField    item field that must equal assigneeId (null = any)
     * @return the page of snapshots and the total count
     */
    public PageResult findItems(String shopId, Collection<String> statuses, String assigneeField, String assigneeId,
                                int page, int size) {
        Criteria itemCriteria = Criteria.where("itemOrderStatus").in(statuses);
        Criteria unwoundCriteria = Criteria.where("items.itemOrderStatus").in(statuses);
        if (assigneeField != null) {
            itemCriteria = itemCriteria.and(assigneeField).is(assigneeId);
            unwoundCriteria = unwoundCriteria.and("items." + assigneeField).is(assigneeId);
        }

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(Criteria.where("shopId").is(shopId)
                .and("orderStatus").ne(DELIVERED)
                .and("items").elemMatch(itemCriteria)));
        pipeline.add(Aggregation.project("invoiceId", "shopId", "orderStatus", "customer",
                "invoiceDate", "expectedDeliveryDate", "items"));
        pipeline.add(Aggregation.unwind("items"));
        pipeline.add(Aggregation.match(unwoundCriteria));
        pipeline.add(Aggregation.sort(Sort.by(Sort.Direction.ASC, "invoiceDate", "items.itemUniqueId")));

        FacetOperation facet = Aggregation.facet(Aggregation.count().as("total")).as("total")
                .and(Aggregation.skip((long) page * size), Aggregation.limit(size)).as("items");
        pipeline.add(facet);

        Document result = mongoTemplate.aggregate(Aggregation.newAggregation(pipeline), INVOICES, Document.class)
                .getUniqueMappedResult();

        long total = 0;
        List<ItemSnapshot> snapshots = new ArrayList<>();
        if (result != null) {
            List<?> totals = result.getList("total", Document.class);
            if (!totals.isEmpty()) {
                total = ((Number) ((Document) totals.get(0)).get("total")).longValue();
            }
            for (Document row : result.getList("items", Document.class)) {
                snapshots.add(toSnapshot(row, (Document) row.get("items")));
            }
        }
        return new PageResult(snapshots, total);
    }

    public record PageResult(List<ItemSnapshot> items, long total) {
    }

    /**
     * Every item of the shop's undelivered invoices matching the criteria (no paging), oldest invoice first,
     * at most {@code limit} items. Used where the whole queue is needed: search, due-date sort / filters,
     * grouping by invoice, counts and workloads (the delivery date is a free-text field, so it is read in Java).
     */
    public List<ItemSnapshot> findAllItems(String shopId, Collection<String> statuses, String assigneeField,
                                           String assigneeId, int limit) {
        Criteria itemCriteria = Criteria.where("itemOrderStatus").in(statuses);
        Criteria unwoundCriteria = Criteria.where("items.itemOrderStatus").in(statuses);
        if (assigneeField != null) {
            itemCriteria = itemCriteria.and(assigneeField).is(assigneeId);
            unwoundCriteria = unwoundCriteria.and("items." + assigneeField).is(assigneeId);
        }
        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(Criteria.where("shopId").is(shopId)
                .and("orderStatus").ne(DELIVERED)
                .and("items").elemMatch(itemCriteria)));
        pipeline.add(Aggregation.project("invoiceId", "shopId", "orderStatus", "customer",
                "invoiceDate", "expectedDeliveryDate", "items"));
        pipeline.add(Aggregation.unwind("items"));
        pipeline.add(Aggregation.match(unwoundCriteria));
        pipeline.add(Aggregation.sort(Sort.by(Sort.Direction.ASC, "invoiceDate", "items.itemUniqueId")));
        pipeline.add(Aggregation.limit(limit));

        List<ItemSnapshot> snapshots = new ArrayList<>();
        for (Document row : mongoTemplate.aggregate(Aggregation.newAggregation(pipeline), INVOICES, Document.class)
                .getMappedResults()) {
            snapshots.add(toSnapshot(row, (Document) row.get("items")));
        }
        return snapshots;
    }

    // ------------------------------------------------------------------ helpers

    private static ItemSnapshot toSnapshot(Document invoice, Document item) {
        Object invoiceDate = invoice.get("invoiceDate");
        return new ItemSnapshot(
                invoice.get("_id").toString(),
                asString(invoice.get("invoiceId")),
                asString(invoice.get("shopId")),
                asString(invoice.get("orderStatus")),
                invoice.get("customer"),
                invoiceDate instanceof Date date ? date : null,
                asString(invoice.get("expectedDeliveryDate")),
                item);
    }

    /** Tolerant read: old documents are not always consistently typed. */
    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** Spring stores String ids that are valid ObjectId hex strings as ObjectId. */
    public static Object toId(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id) : id;
    }
}
