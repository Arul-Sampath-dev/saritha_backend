package com.saritha.fashions.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.query.Collation;

import java.util.List;

@Configuration
public class MongoIndexInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);
    private final MongoTemplate mongoTemplate;

    @Value("${app.indexes.enabled:true}")
    private boolean indexCreationEnabled;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        if (!indexCreationEnabled) {
            log.info("🚫 Skipping MongoDB index initialization (app.indexes.enabled=false).");
            return;
        }

        try {
            createInvoiceIndexes();
            createEmployeeActivitiesIndexes();
            createExpensesIndexes();
            createUsersIndexes();
            createShopsIndexes();
            createCustomersIndexes();
            createReceiptsIndexes();
            createPaymentReceiptsIndexes();

            log.info("✅ MongoDB index initialization completed successfully.");
        } catch (Exception e) {
            log.error("❌ Error while creating MongoDB indexes: ", e);
        }
    }

    // ------------------------------------------------------------------------
    // 🧩 SAFE INDEX CREATION (avoids duplicate _id errors)
    // ------------------------------------------------------------------------
    private void ensureSafeIndex(String collection, Index index) {
        try {
            // Skip creating _id index — MongoDB always has it
            String firstKey = index.getIndexKeys().keySet().iterator().next();
            if ("_id".equals(firstKey)) {
                log.debug("⏭️ Skipping _id index creation for '{}'", collection);
                return;
            }

            mongoTemplate.indexOps(collection).ensureIndex(index);
            log.info("✅ Ensured index {} for '{}'", index.getIndexKeys(), collection);
        } catch (Exception e) {
            log.error("❌ Failed to create index {} for '{}': {}", index.getIndexKeys(), collection, e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // 📦 INVOICE
    // ------------------------------------------------------------------------
    private void createInvoiceIndexes() {
        String collection = "invoice";
        logExistingIndexes(collection);

        ensureSafeIndex(collection, new Index()
                .on("shopId", Sort.Direction.ASC)
                .on("invoiceDate", Sort.Direction.DESC)
                .background());

        // Every invoice page, PDF, payment and item action looks the invoice up by its number.
        ensureUniqueOrPlainIndex(collection, "invoiceId");
        // A customer's invoices (customer page, lookup count, deleting an invoice).
        ensureSafeIndex(collection, new Index().on("customer.$id", Sort.Direction.ASC).background());
        // Open (not delivered) orders of a shop: deliveries, production queues and production report.
        ensureSafeIndex(collection, new Index()
                .on("shopId", Sort.Direction.ASC)
                .on("orderStatus", Sort.Direction.ASC)
                .background());
        // Item actions (/invoices/{id}/items/{itemUniqueId}) and production lookups by item.
        ensureSafeIndex(collection, new Index().on("items.itemUniqueId", Sort.Direction.ASC).background());
    }

    /**
     * Unique index on one field; if existing documents already share a value (the unique index cannot be built),
     * a plain index instead, so the lookups are still fast. The duplicates are logged by the failed attempt.
     */
    private void ensureUniqueOrPlainIndex(String collection, String field) {
        try {
            mongoTemplate.indexOps(collection).ensureIndex(new Index().on(field, Sort.Direction.ASC).unique().background());
            log.info("✅ Ensured unique index {} for '{}'", field, collection);
        } catch (Exception e) {
            log.warn("⚠️ Unique index on {}.{} not possible ({}); creating a plain index", collection, field, e.getMessage());
            ensureSafeIndex(collection, new Index().on(field, Sort.Direction.ASC).background());
        }
    }

    // ------------------------------------------------------------------------
    // 📦 CUSTOMERS / RECEIPTS / PAYMENT RECEIPTS
    // ------------------------------------------------------------------------
    private void createCustomersIndexes() {
        String collection = "customers";
        logExistingIndexes(collection);
        // Invoice creation finds the customer by phone; so does the WhatsApp number lookup. Not unique: old records
        // may share a number or have none.
        ensureSafeIndex(collection, new Index().on("phone", Sort.Direction.ASC).background());
        // Invoice search by customer name scans this small index instead of every customer document.
        ensureSafeIndex(collection, new Index().on("name", Sort.Direction.ASC).background());
    }

    private void createReceiptsIndexes() {
        String collection = "receipt";
        logExistingIndexes(collection);
        ensureUniqueOrPlainIndex(collection, "receiptNo");
        ensureSafeIndex(collection, new Index().on("invoiceId", Sort.Direction.ASC).background());
        // Receipts of a shop in a period (deliveries, reports).
        ensureSafeIndex(collection, new Index()
                .on("shopId", Sort.Direction.ASC)
                .on("receiptDate", Sort.Direction.ASC)
                .background());
    }

    private void createPaymentReceiptsIndexes() {
        String collection = "payment_receipts";
        logExistingIndexes(collection);
        ensureUniqueOrPlainIndex(collection, "paymentReceiptNo");
        ensureSafeIndex(collection, new Index().on("invoiceId", Sort.Direction.ASC).background());
        ensureSafeIndex(collection, new Index()
                .on("shopId", Sort.Direction.ASC)
                .on("paidAt", Sort.Direction.ASC)
                .background());
    }

    // ------------------------------------------------------------------------
    // 📦 EMPLOYEE_ACTIVITIES
    // ------------------------------------------------------------------------
    private void createEmployeeActivitiesIndexes() {
        String collection = "employee_activities";
        logExistingIndexes(collection);

        ensureSafeIndex(collection, new Index()
                .on("shopId", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.ASC)
                .background());

        ensureSafeIndex(collection, new Index()
                .on("shopId", Sort.Direction.ASC)
                .on("employeeId", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.ASC)
                .background());
    }

    // ------------------------------------------------------------------------
    // 📦 EXPENSES
    // ------------------------------------------------------------------------
    private void createExpensesIndexes() {
        String collection = "expenses";
        logExistingIndexes(collection);

        ensureSafeIndex(collection, new Index().on("shopId", Sort.Direction.ASC).background());
        ensureSafeIndex(collection, new Index().on("expenseDate", Sort.Direction.ASC).background());
        ensureSafeIndex(collection, new Index()
                .on("shopId", Sort.Direction.ASC)
                .on("expenseDate", Sort.Direction.ASC)
                .background());

        // Text index on invoiceId
        TextIndexDefinition textIndex = new TextIndexDefinition.TextIndexDefinitionBuilder()
                .onField("invoiceId")
                .build();
        mongoTemplate.indexOps(collection).ensureIndex(textIndex);
    }

    // ------------------------------------------------------------------------
    // 📦 USERS
    // ------------------------------------------------------------------------
    private void createUsersIndexes() {
        String collection = "users";
        logExistingIndexes(collection);
        IndexOperations indexOps = mongoTemplate.indexOps(collection);

        // username_1
        ensureSafeIndex(collection, new Index()
                .on("username", Sort.Direction.ASC)
                .background());

        // email_case_insensitive
        Collation caseInsensitive = Collation.of("en")
                .strength(2) // equivalent to SECONDARY
                .caseLevel(false);
        ensureSafeIndex(collection, new Index()
                .on("email", Sort.Direction.ASC)
                .unique()
                .collation(caseInsensitive)
                .named("email_case_insensitive")
                .background());

        // email_1
        ensureSafeIndex(collection, new Index()
                .on("email", Sort.Direction.ASC)
                .unique()
                .named("email_1")
                .background());

        // roles.roleName_1__id_1
        ensureSafeIndex(collection, new Index()
                .on("roles.roleName", Sort.Direction.ASC)
                .on("_id", Sort.Direction.ASC)
                .named("roles.roleName_1__id_1")
                .background());

        // (No "userId" index: userId is the document _id, which MongoDB always indexes. Creating it failed with
        // "The field 'background' is not valid for an _id index" at every start.)
    }

    // ------------------------------------------------------------------------
    // 📦 SHOPS
    // ------------------------------------------------------------------------
    private void createShopsIndexes() {
        String collection = "shops";
        logExistingIndexes(collection);

        ensureSafeIndex(collection, new Index().on("employeeIds", Sort.Direction.ASC).background());
        ensureSafeIndex(collection, new Index().on("employees.$id", Sort.Direction.ASC).background());
    }

    // ------------------------------------------------------------------------
    // 🧾 Log existing indexes
    // ------------------------------------------------------------------------
    private void logExistingIndexes(String collection) {
        List<IndexInfo> existing = mongoTemplate.indexOps(collection).getIndexInfo();
        log.info("🔍 Existing indexes for '{}': {}", collection,
                existing.stream().map(IndexInfo::getName).toList());
    }
}
