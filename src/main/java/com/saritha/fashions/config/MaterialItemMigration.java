package com.saritha.fashions.config;

import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import com.saritha.fashions.modules.invoice.model.ItemKind;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * One-off, idempotent data fix run at startup: Lining Material became a material item (no production steps, see
 * {@link ItemKind}). On orders that are not delivered yet, Lining Material items still waiting in CUTTING with nobody
 * assigned are marked {@code material = true} and ORDER_COMPLETED. Items already given to a tailor or shop assistant
 * are left alone and finish the old workflow. Running it again changes nothing.
 */
@Component
public class MaterialItemMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MaterialItemMigration.class);
    private static final String INVOICES = "invoice";

    private final MongoTemplate mongoTemplate;

    public MaterialItemMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            UpdateResult result = mongoTemplate.getCollection(INVOICES).updateMany(filter(), update(),
                    new UpdateOptions().arrayFilters(List.of(waitingLiningMaterial("m."))));
            if (result.getModifiedCount() > 0) {
                log.info("Material items: Lining Material on {} open invoice(s) marked as material (completed)",
                        result.getModifiedCount());
            }
        } catch (Exception exception) {
            log.error("Material items: could not mark open Lining Material items: {}", exception.getMessage(), exception);
        }
    }

    /** Open invoices (no receipt, not delivered) with at least one Lining Material item waiting in CUTTING. */
    public static Document filter() {
        return new Document("orderStatus", new Document("$ne", OrderStatus.ORDER_DELIVERED))
                .append("receiptId", null)
                .append("items", new Document("$elemMatch", waitingLiningMaterial("")));
    }

    public static Document update() {
        return new Document("$set", new Document("items.$[m].material", true)
                .append("items.$[m].itemOrderStatus", OrderStatus.ORDER_COMPLETED));
    }

    /** Lining Material, still CUTTING, no tailor or shop assistant, not yet flagged (prefix "m." for arrayFilters). */
    public static Document waitingLiningMaterial(String prefix) {
        List<Object> unassigned = Arrays.asList(null, "");
        return new Document(prefix + "itemName", ItemKind.LINING_MATERIAL)
                .append(prefix + "itemOrderStatus", OrderStatus.CUTTING)
                .append(prefix + "assignedTo", new Document("$in", unassigned))
                .append(prefix + "assignedToWhichShopAssistant", new Document("$in", unassigned))
                .append(prefix + "material", new Document("$ne", true));
    }
}
