package com.saritha.fashions.modules.invoice.repository;

import com.mongodb.DBRef;
import com.saritha.fashions.modules.core.date.DeliveryDates;
import com.saritha.fashions.modules.invoice.model.InvoiceCustomer;
import com.saritha.fashions.modules.invoice.model.InvoiceRecord;
import com.saritha.fashions.modules.invoice.model.InvoiceStage;
import com.saritha.fashions.util.DateTimeUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * MongoTemplate-based implementation of InvoiceRecordRepositoryCustom.
 * The class name must be the fragment interface name + "Impl" so Spring Data picks it up.
 */
public class InvoiceRecordRepositoryCustomImpl implements InvoiceRecordRepositoryCustom {

    private static final String SHOPS_COLLECTION = "shops";

    private final MongoTemplate mongoTemplate;

    public InvoiceRecordRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<ShopInvoiceProfile> findShopProfileByEmployeeId(String userId) {
        Query query = new Query(Criteria.where("employeeIds").is(userId)).limit(1);
        query.fields().include("_id").include("shopName").include("shopState").include("gstNumber");

        Document shop = mongoTemplate.findOne(query, Document.class, SHOPS_COLLECTION);
        return Optional.ofNullable(shop).map(document -> new ShopInvoiceProfile(
                document.get("_id").toString(),
                document.getString("shopName"),
                document.getString("shopState"),
                document.getString("gstNumber")));
    }

    @Override
    public CustomerLookupResult findOrCreateCustomerByPhone(InvoiceCustomer newCustomer) {
        Query query = new Query(Criteria.where("phone").is(newCustomer.getPhone()));

        Update update = new Update()
                .setOnInsert("customerId", newCustomer.getCustomerId())
                .setOnInsert("name", newCustomer.getName())
                .setOnInsert("email", newCustomer.getEmail())
                .setOnInsert("alternatePhone", newCustomer.getAlternatePhone())
                .setOnInsert("address", newCustomer.getAddress())
                .setOnInsert("allInvoiceIds", new ArrayList<String>())
                .setOnInsert("allReceiptIds", new ArrayList<String>());

        InvoiceCustomer customer = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                InvoiceCustomer.class);

        // The new customerId is only written on insert, so seeing it back means we created it.
        boolean created = customer != null
                && newCustomer.getCustomerId().equals(customer.getCustomerId());
        return new CustomerLookupResult(customer, created);
    }

    @Override
    public boolean customerIdExists(String customerId) {
        return mongoTemplate.exists(new Query(Criteria.where("customerId").is(customerId)), InvoiceCustomer.class);
    }

    @Override
    public void addInvoiceToCustomer(String customerDbId, String invoiceId) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(customerDbId)),
                new Update().addToSet("allInvoiceIds", invoiceId),
                InvoiceCustomer.class);
    }

    // ------------------------------------------------------------------ reads

    private static final String INVOICES = "invoice";
    private static final String RECEIPTS = "receipt";
    private static final String DELIVERED = "ORDER_DELIVERED";
    private static final int MAX_CUSTOMER_MATCHES = 1000;

    /** Same special-category list as invoice creation and the legacy special-category screen. */
    private static final List<String> SPECIAL_ITEMS =
            List.of("Embroidery", "Lining Material", "Saree Fall", "Saree Pico", "Designing");

    @Override
    public boolean shopExists(String shopId) {
        return mongoTemplate.exists(new Query(Criteria.where("_id").is(toId(shopId))), SHOPS_COLLECTION);
    }

    @Override
    public InvoicePage findInvoices(String shopId, InvoiceFilter filter, int page, int size) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("shopId").is(shopId));
        if (filter.from() != null) {
            criteria.add(Criteria.where("invoiceDate").gte(filter.from()));
        }
        if (filter.before() != null) {
            criteria.add(Criteria.where("invoiceDate").lt(filter.before()));
        }
        if (Boolean.TRUE.equals(filter.delivered())) {
            criteria.add(Criteria.where("orderStatus").is(DELIVERED));
        } else if (Boolean.FALSE.equals(filter.delivered())) {
            criteria.add(Criteria.where("orderStatus").ne(DELIVERED));
        }
        if (filter.paymentStatus() != null) {
            criteria.add(Criteria.where("paymentStatus").regex(
                    Pattern.compile("^" + Pattern.quote(filter.paymentStatus()) + "$", Pattern.CASE_INSENSITIVE)));
        }
        if (filter.specialOnly()) {
            criteria.add(Criteria.where("items.itemName").in(SPECIAL_ITEMS));
        }
        if (filter.stage() != null) {
            criteria.addAll(stageCriteria(filter.stage()));
        }
        if (filter.itemStatus() != null) {
            criteria.add(Criteria.where("items.itemOrderStatus").is(filter.itemStatus()));
        }
        if (filter.search() != null) {
            // Literal, case-insensitive match on the invoice number, or on the customer's name / phone.
            Pattern pattern = Pattern.compile(Pattern.quote(filter.search()), Pattern.CASE_INSENSITIVE);
            Query customerQuery = new Query(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern), Criteria.where("phone").regex(pattern)))
                    .limit(MAX_CUSTOMER_MATCHES);
            customerQuery.fields().include("_id");
            List<Object> customerIds = mongoTemplate.find(customerQuery, Document.class, "customers").stream()
                    .map(customer -> customer.get("_id")).toList();
            criteria.add(customerIds.isEmpty()
                    ? Criteria.where("invoiceId").regex(pattern)
                    : new Criteria().orOperator(Criteria.where("invoiceId").regex(pattern),
                    Criteria.where("customer.$id").in(customerIds)));
        }

        Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        long total = mongoTemplate.count(query, INVOICES);

        List<Document> rows;
        if (filter.sortField() == SortField.DUE_DATE) {
            rows = findSortedByDueDate(criteria, filter.ascending(), page, size);
        } else {
            query.with(Sort.by(filter.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, "invoiceDate"))
                    .skip((long) page * size).limit(size);
            rows = mongoTemplate.find(query, Document.class, INVOICES);
        }

        List<InvoiceRecord> invoices = new ArrayList<>();
        Map<String, Object> customerIdByInvoice = new HashMap<>();
        for (Document raw : rows) {
            // Take the customer DBRef out before mapping, so the converter does not resolve it
            // with one query per invoice; customers are loaded below in one batch.
            Object customerRef = raw.get("customer");
            InvoiceRecord invoice = toInvoiceRecord(raw);
            invoices.add(invoice);
            if (customerRef instanceof DBRef ref) {
                customerIdByInvoice.put(invoice.getId(), ref.getId());
            }
        }

        Map<String, InvoiceCustomer> customersById = new HashMap<>();
        List<Object> customerIds = customerIdByInvoice.values().stream().distinct().toList();
        if (!customerIds.isEmpty()) {
            mongoTemplate.find(new Query(Criteria.where("_id").in(customerIds)), InvoiceCustomer.class)
                    .forEach(customer -> customersById.put(customer.getId(), customer));
        }
        Map<String, InvoiceCustomer> customersByInvoice = new HashMap<>();
        customerIdByInvoice.forEach((invoiceId, customerId) -> {
            InvoiceCustomer customer = customersById.get(customerId.toString());
            if (customer != null) {
                customersByInvoice.put(invoiceId, customer);
            }
        });
        return new InvoicePage(invoices, customersByInvoice, total, receipts(invoices));
    }

    /**
     * The production-stage filter; must match InvoiceStage.of exactly:
     * NEW = not delivered and no started item; IN_PRODUCTION = not delivered, a started item and an item that
     * is not completed; READY = not delivered, has items and none that is not completed; DELIVERED.
     * "Started" = a status other than ORDER_ACCEPTED (a missing status counts as ORDER_ACCEPTED).
     */
    static List<Criteria> stageCriteria(InvoiceStage stage) {
        Criteria notDelivered = Criteria.where("orderStatus").ne(DELIVERED);
        Criteria started = Criteria.where("itemOrderStatus").nin(InvoiceStage.ACCEPTED, null);
        Criteria notCompleted = Criteria.where("itemOrderStatus").ne(InvoiceStage.COMPLETED);
        return switch (stage) {
            case DELIVERED -> List.of(Criteria.where("orderStatus").is(DELIVERED));
            case NEW -> List.of(notDelivered, Criteria.where("items").not().elemMatch(started));
            case IN_PRODUCTION -> List.of(notDelivered, Criteria.where("items").elemMatch(started),
                    new Criteria().andOperator(Criteria.where("items").elemMatch(notCompleted)));
            case READY -> List.of(notDelivered, Criteria.where("items.0").exists(true),
                    Criteria.where("items").not().elemMatch(notCompleted));
        };
    }

    /**
     * One page sorted by expected delivery date. The dates are free text in old data, so they are read with
     * {@link DeliveryDates#parse} (the parser the delivery dashboard uses): only the sort keys of the matching
     * invoices are loaded, sorted here, and then the page's invoices are fetched by id.
     * Order: invoices with a readable date first (ascending = soonest first), then by invoice date (newest first);
     * invoices without a readable date always last.
     */
    private List<Document> findSortedByDueDate(List<Criteria> criteria, boolean ascending, int page, int size) {
        Query keysQuery = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        keysQuery.fields().include("_id").include("expectedDeliveryDate").include("invoiceDate");
        List<Document> keys = new ArrayList<>(mongoTemplate.find(keysQuery, Document.class, INVOICES));
        keys.sort(dueDateOrder(ascending));

        int fromIndex = (int) Math.min((long) page * size, keys.size());
        int toIndex = Math.min(fromIndex + size, keys.size());
        List<Object> ids = keys.subList(fromIndex, toIndex).stream().map(key -> key.get("_id")).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, Document> byId = new HashMap<>();
        mongoTemplate.find(new Query(Criteria.where("_id").in(ids)), Document.class, INVOICES)
                .forEach(invoice -> byId.put(String.valueOf(invoice.get("_id")), invoice));
        List<Document> rows = new ArrayList<>();
        ids.forEach(id -> {
            Document invoice = byId.get(String.valueOf(id));
            if (invoice != null) {
                rows.add(invoice);
            }
        });
        return rows;
    }

    /** Readable due date first (asc / desc), then newest invoice first, then id; no due date last. */
    static Comparator<Document> dueDateOrder(boolean ascending) {
        Comparator<LocalDate> byDate = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        Comparator<Date> newestFirst = Comparator.nullsLast(Comparator.reverseOrder());
        return Comparator.<Document, LocalDate>comparing(InvoiceRecordRepositoryCustomImpl::dueDateOf,
                        Comparator.nullsLast(byDate))
                .thenComparing(key -> key.get("invoiceDate") instanceof Date date ? date : null, newestFirst)
                .thenComparing(key -> String.valueOf(key.get("_id")));
    }

    /** expectedDeliveryDate as a date (text in any old format, or a stored date), or null. */
    static LocalDate dueDateOf(Document invoice) {
        Object value = invoice.get("expectedDeliveryDate");
        if (value instanceof Date date) {
            return date.toInstant().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
        }
        return value == null ? null : DeliveryDates.parse(value.toString()).orElse(null);
    }

    /** Receipts (number + delivered date) of the delivered invoices on the page, in one query. */
    private Map<String, ReceiptInfo> receipts(List<InvoiceRecord> invoices) {
        Map<String, String> receiptDbIdByInvoice = new HashMap<>();
        invoices.stream().filter(invoice -> invoice.getReceiptId() != null)
                .forEach(invoice -> receiptDbIdByInvoice.put(invoice.getId(), invoice.getReceiptId()));
        Map<String, ReceiptInfo> result = new HashMap<>();
        if (receiptDbIdByInvoice.isEmpty()) {
            return result;
        }
        Query receiptQuery = new Query(Criteria.where("_id").in(
                receiptDbIdByInvoice.values().stream().map(InvoiceRecordRepositoryCustomImpl::toId).toList()));
        receiptQuery.fields().include("_id").include("receiptNo").include("receiptDate");
        Map<String, ReceiptInfo> byReceiptDbId = new HashMap<>();
        mongoTemplate.find(receiptQuery, Document.class, RECEIPTS).forEach(receipt ->
                byReceiptDbId.put(receipt.get("_id").toString(), receiptInfo(receipt)));
        receiptDbIdByInvoice.forEach((invoiceDbId, receiptDbId) -> {
            ReceiptInfo receipt = byReceiptDbId.get(receiptDbId);
            if (receipt != null && receipt.receiptNo() != null) {
                result.put(invoiceDbId, receipt);
            }
        });
        return result;
    }

    @Override
    public Optional<ReceiptInfo> findReceipt(String receiptDbId) {
        if (receiptDbId == null) {
            return Optional.empty();
        }
        Query query = new Query(Criteria.where("_id").is(toId(receiptDbId)));
        query.fields().include("receiptNo").include("receiptDate");
        Document receipt = mongoTemplate.findOne(query, Document.class, RECEIPTS);
        return Optional.ofNullable(receipt == null || receipt.getString("receiptNo") == null ? null : receiptInfo(receipt));
    }

    private static ReceiptInfo receiptInfo(Document receipt) {
        Object date = receipt.get("receiptDate");
        return new ReceiptInfo(receipt.getString("receiptNo"), date instanceof Date d ? d : null);
    }

    @Override
    public Optional<Document> findInvoiceDocument(String invoiceId, String shopId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                new Query(Criteria.where("invoiceId").is(invoiceId).and("shopId").is(shopId)), Document.class, INVOICES));
    }

    // ------------------------------------------------------------------ edit / delete

    @Override
    public boolean updateInvoiceIfUnchanged(Object invoiceDbId, Map<String, Object> expectedFields, Update update) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("_id").is(invoiceDbId));
        criteria.add(Criteria.where("receiptId").is(null));
        criteria.add(Criteria.where("orderStatus").ne(DELIVERED));
        // Equality on an array of sub-documents is exact (values and field order), so passing the
        // raw items list read before the edit makes any concurrent item change fail the update.
        expectedFields.forEach((field, value) -> criteria.add(Criteria.where(field).is(value)));
        Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        return mongoTemplate.updateFirst(query, update, INVOICES).getMatchedCount() > 0;
    }

    @Override
    public Object archiveDeletedInvoice(Document invoice, String deletedBy, String reason) {
        Document archive = new Document("_id", new ObjectId())
                .append("invoice", invoice)
                .append("deletedBy", deletedBy)
                .append("deletedAt", DateTimeUtils.getNowInIST())
                .append("reason", reason);
        mongoTemplate.insert(archive, "deleted_invoices");
        return archive.get("_id");
    }

    @Override
    public void removeArchivedInvoice(Object archiveId) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(archiveId)), "deleted_invoices");
    }

    @Override
    public boolean deleteInvoiceIfOpen(Object invoiceDbId, Map<String, Object> expectedFields) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("_id").is(invoiceDbId));
        criteria.add(Criteria.where("receiptId").is(null));
        criteria.add(Criteria.where("orderStatus").ne(DELIVERED));
        expectedFields.forEach((field, value) -> criteria.add(Criteria.where(field).is(value)));
        Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        return mongoTemplate.remove(query, INVOICES).getDeletedCount() > 0;
    }

    @Override
    public InvoiceRecord toInvoiceRecord(Document raw) {
        Document copy = new Document(raw);
        copy.remove("customer");
        return mongoTemplate.getConverter().read(InvoiceRecord.class, copy);
    }

    @Override
    public Optional<InvoiceCustomer> findCustomer(Object customerDbId) {
        return Optional.ofNullable(customerDbId == null ? null
                : mongoTemplate.findOne(new Query(Criteria.where("_id").is(customerDbId)), InvoiceCustomer.class));
    }

    @Override
    public void removeInvoiceFromCustomer(Object customerDbId, String invoiceId) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(customerDbId)),
                new Update().pull("allInvoiceIds", invoiceId), "customers");
    }

    @Override
    public boolean customerPhoneUsedByOther(String phone, Object customerDbId) {
        return mongoTemplate.exists(new Query(Criteria.where("phone").is(phone).and("_id").ne(customerDbId)), "customers");
    }

    @Override
    public boolean updateCustomer(Object customerDbId, Update update) {
        return mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(customerDbId)), update, "customers")
                .getMatchedCount() == 1;
    }

    /** Spring stores String ids that are valid ObjectId hex strings as ObjectId. */
    private static Object toId(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id) : id;
    }
}
