package com.saritha.fashions.modules.receipt.repository;

import com.mongodb.DBRef;
import com.saritha.fashions.modules.receipt.model.ReceiptRecord;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MongoTemplate-based implementation of ReceiptRecordRepositoryCustom.
 * The class name must be the fragment interface name + "Impl" so Spring Data picks it up.
 */
public class ReceiptRecordRepositoryCustomImpl implements ReceiptRecordRepositoryCustom {

    private static final String INVOICES = "invoice";
    private static final String RECEIPTS = "receipt";
    private static final String SHOPS = "shops";
    private static final String PAYMENT_RECEIPTS = "payment_receipts";
    private static final String CUSTOMERS = "customers";
    private static final String DELIVERED = "ORDER_DELIVERED";

    private final MongoTemplate mongoTemplate;

    public ReceiptRecordRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<String> findShopIdByEmployeeId(String userId) {
        Query query = new Query(Criteria.where("employeeIds").is(userId)).limit(1);
        query.fields().include("_id");
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, SHOPS))
                .map(shop -> shop.get("_id").toString());
    }

    @Override
    public Object insertPaymentReceipt(Document paymentReceipt) {
        mongoTemplate.insert(paymentReceipt, PAYMENT_RECEIPTS);
        return paymentReceipt.get("_id");
    }

    @Override
    public void deletePaymentReceipt(Object paymentReceiptDbId) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(paymentReceiptDbId)), PAYMENT_RECEIPTS);
    }

    @Override
    public Optional<Document> findPaymentReceipt(String paymentReceiptNo, String shopId) {
        Criteria criteria = Criteria.where("paymentReceiptNo").is(paymentReceiptNo);
        if (shopId != null) {
            criteria = criteria.and("shopId").is(shopId);
        }
        return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), Document.class, PAYMENT_RECEIPTS));
    }

    @Override
    public List<Document> findPaymentReceiptsOfInvoice(String invoiceId, String shopId) {
        Criteria criteria = Criteria.where("invoiceId").is(invoiceId);
        if (shopId != null) {
            criteria = criteria.and("shopId").is(shopId);
        }
        return mongoTemplate.find(new Query(criteria).with(Sort.by("paidAt")), Document.class, PAYMENT_RECEIPTS);
    }

    @Override
    public Optional<CustomerSummary> findCustomer(Object customerDbId) {
        if (customerDbId == null) {
            return Optional.empty();
        }
        Query query = new Query(Criteria.where("_id").is(customerDbId));
        query.fields().include("name").include("phone").include("email");
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, "customers"))
                .map(c -> new CustomerSummary(asString(c.get("name")), asString(c.get("phone")), asString(c.get("email"))));
    }

    @Override
    public Optional<ShopDetails> findShop(String shopId) {
        Query query = new Query(Criteria.where("_id").is(toId(shopId)));
        query.fields().include("_id").include("shopName").include("gstNumber");
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, SHOPS))
                .map(shop -> new ShopDetails(shop.get("_id").toString(),
                        asString(shop.get("shopName")), asString(shop.get("gstNumber"))));
    }

    @Override
    public Optional<Document> findInvoice(String invoiceId, String shopId) {
        Query query = new Query(Criteria.where("invoiceId").is(invoiceId).and("shopId").is(shopId));
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, INVOICES));
    }

    @Override
    public boolean updateInvoiceIfUnchanged(Object invoiceDbId, Map<String, Object> expectedFields, Update update) {
        Criteria criteria = Criteria.where("_id").is(invoiceDbId)
                .and("receiptId").is(null)          // no receipt yet (missing or null)
                .and("orderStatus").ne(DELIVERED);
        for (Map.Entry<String, Object> expected : expectedFields.entrySet()) {
            criteria = criteria.and(expected.getKey()).is(expected.getValue());
        }
        return mongoTemplate.updateFirst(new Query(criteria), update, INVOICES).getModifiedCount() > 0;
    }

    @Override
    public Object insertReceipt(Document receipt) {
        mongoTemplate.insert(receipt, RECEIPTS);
        return receipt.get("_id");
    }

    @Override
    public void deleteReceipt(Object receiptDbId) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(receiptDbId)), RECEIPTS);
    }

    @Override
    public void addReceiptToCustomer(Object customerDbId, String receiptNo) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(customerDbId)),
                new Update().addToSet("allReceiptIds", receiptNo), CUSTOMERS);
    }

    @Override
    public Optional<ReceiptRecord> findReceipt(String receiptNo, String shopId) {
        Criteria criteria = Criteria.where("receiptNo").is(receiptNo);
        if (shopId != null) {
            criteria = criteria.and("shopId").is(shopId);
        }
        return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), ReceiptRecord.class));
    }

    @Override
    public Optional<ReceiptRecord> findReceiptByInvoiceDbId(String invoiceDbId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                new Query(Criteria.where("invoiceId").is(invoiceDbId)), ReceiptRecord.class));
    }

    @Override
    public PageResult findReceipts(String shopId, int page, int size) {
        Query query = shopId == null ? new Query() : new Query(Criteria.where("shopId").is(shopId));
        long total = mongoTemplate.count(query, ReceiptRecord.class);
        query.with(Sort.by(Sort.Direction.DESC, "receiptDate")).skip((long) page * size).limit(size);
        return new PageResult(mongoTemplate.find(query, ReceiptRecord.class), total);
    }

    @Override
    public Map<String, String> findInvoiceNumbers(Collection<String> invoiceDbIds) {
        Map<String, String> numbers = new HashMap<>();
        List<Object> ids = invoiceDbIds.stream().filter(Objects::nonNull).distinct()
                .map(ReceiptRecordRepositoryCustomImpl::toId).toList();
        if (ids.isEmpty()) {
            return numbers;
        }
        Query query = new Query(Criteria.where("_id").in(ids));
        query.fields().include("_id").include("invoiceId");
        mongoTemplate.find(query, Document.class, INVOICES)
                .forEach(invoice -> numbers.put(invoice.get("_id").toString(), asString(invoice.get("invoiceId"))));
        return numbers;
    }

    @Override
    public Map<String, CustomerSummary> findCustomersOfReceipts(Collection<String> receiptIds) {
        Map<String, CustomerSummary> result = new HashMap<>();
        if (receiptIds.isEmpty()) {
            return result;
        }
        Query receiptQuery = new Query(Criteria.where("_id").in(receiptIds.stream().map(ReceiptRecordRepositoryCustomImpl::toId).toList()));
        receiptQuery.fields().include("_id").include("customer");
        Map<String, Object> customerIdByReceipt = new HashMap<>();
        for (Document receipt : mongoTemplate.find(receiptQuery, Document.class, RECEIPTS)) {
            if (receipt.get("customer") instanceof DBRef ref) {
                customerIdByReceipt.put(receipt.get("_id").toString(), ref.getId());
            }
        }

        List<Object> customerIds = customerIdByReceipt.values().stream().filter(Objects::nonNull).distinct().toList();
        if (customerIds.isEmpty()) {
            return result;
        }
        Query customerQuery = new Query(Criteria.where("_id").in(customerIds));
        customerQuery.fields().include("_id").include("name").include("phone").include("email");
        Map<String, CustomerSummary> byCustomerId = new HashMap<>();
        for (Document customer : mongoTemplate.find(customerQuery, Document.class, CUSTOMERS)) {
            byCustomerId.put(customer.get("_id").toString(), new CustomerSummary(
                    asString(customer.get("name")), asString(customer.get("phone")), asString(customer.get("email"))));
        }
        customerIdByReceipt.forEach((receiptId, customerId) -> {
            CustomerSummary summary = byCustomerId.get(customerId.toString());
            if (summary != null) {
                result.put(receiptId, summary);
            }
        });
        return result;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** Spring stores String ids that are valid ObjectId hex strings as ObjectId. */
    public static Object toId(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id) : id;
    }
}
