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

        // userId_1
        ensureSafeIndex(collection, new Index()
                .on("userId", Sort.Direction.ASC)
                .named("userId_1")
                .background());
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
