package com.saritha.fashions.modules.core.payment;

import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites stored payment methods to their canonical names (PaymentMethods), e.g. "cash" -> Cash,
 * "BANK" -> Bank Transfer, "Debit/Credit Cards" -> Card. Unknown spellings are reported, never guessed.
 * Idempotent: running it again changes nothing. Reports already map old spellings on read, so running it is
 * a clean-up, not a prerequisite.
 */
@Service
public class PaymentMethodMigrationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodMigrationService.class);

    /** collection, field path, and for array entries the array name + element field. */
    private record Target(String collection, String field, String array, String elementField) {
        boolean isArray() {
            return array != null;
        }
    }

    private static final List<Target> TARGETS = List.of(
            new Target("invoice", "paymentMethod", null, null),
            new Target("invoice", "paymentHistories.paidVia", "paymentHistories", "paidVia"),
            new Target("receipt", "paymentMethod", null, null),
            new Target("payment_receipts", "paidVia", null, null),
            new Target("expenses", "paymentMethod", null, null));

    public record Change(String collection, String field, String from, String to, long documents) {
    }

    public record Unmapped(String collection, String field, String value, long documents) {
    }

    public record Result(boolean dryRun, List<Change> changes, List<Unmapped> unmapped, List<String> canonicalValues) {
    }

    private final MongoTemplate mongoTemplate;

    public PaymentMethodMigrationService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Result migrate(boolean dryRun) {
        List<Change> changes = new ArrayList<>();
        List<Unmapped> unmapped = new ArrayList<>();
        for (Target target : TARGETS) {
            List<Object> values = mongoTemplate.findDistinct(new Query(), target.field(), target.collection(), Object.class);
            for (Object value : values) {
                if (!(value instanceof String raw)) {
                    continue;
                }
                long documents = mongoTemplate.count(new Query(Criteria.where(target.field()).is(raw)), target.collection());
                String canonical = PaymentMethods.canonical(raw).orElse(null);
                if (canonical == null) {
                    unmapped.add(new Unmapped(target.collection(), target.field(), raw, documents));
                    continue;
                }
                if (canonical.equals(raw)) {
                    continue;
                }
                if (!dryRun) {
                    rewrite(target, raw, canonical);
                }
                changes.add(new Change(target.collection(), target.field(), raw, canonical, documents));
            }
        }
        log.info("Payment-method migration ({}): {} change groups, {} unknown values",
                dryRun ? "dry run" : "applied", changes.size(), unmapped.size());
        return new Result(dryRun, changes, unmapped, PaymentMethods.ALL);
    }

    private void rewrite(Target target, String raw, String canonical) {
        var collection = mongoTemplate.getCollection(target.collection());
        if (target.isArray()) {
            collection.updateMany(new Document(target.field(), raw),
                    new Document("$set", new Document(target.array() + ".$[p]." + target.elementField(), canonical)),
                    new UpdateOptions().arrayFilters(List.of(new Document("p." + target.elementField(), raw))));
        } else {
            collection.updateMany(new Document(target.field(), raw),
                    new Document("$set", new Document(target.field(), canonical)));
        }
    }
}
