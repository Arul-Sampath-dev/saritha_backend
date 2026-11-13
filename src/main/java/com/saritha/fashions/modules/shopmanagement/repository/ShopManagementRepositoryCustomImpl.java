package com.saritha.fashions.modules.shopmanagement.repository;

import com.saritha.fashions.modules.shopmanagement.model.ManagedShop;
import com.saritha.fashions.modules.shopmanagement.model.PettyCashEntry;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MongoTemplate-based implementation of ShopManagementRepositoryCustom.
 * The class name must be the fragment interface name + "Impl" so Spring Data picks it up.
 */
public class ShopManagementRepositoryCustomImpl implements ShopManagementRepositoryCustom {

    private static final String SHOPS = "shops";

    private final MongoTemplate mongoTemplate;

    public ShopManagementRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Uses findAndModify with $inc + $push, so the balance change and the history entry
     * are applied together, and two top-ups at the same time cannot overwrite each other.
     * Only id, shopName and pettyCash are returned, so the large arrays on the shop
     * are not loaded.
     */
    @Override
    public ManagedShop addPettyCash(String shopId, PettyCashEntry entry) {
        Query query = new Query(Criteria.where("_id").is(shopId));
        query.fields().include("_id").include("shopName").include("pettyCash");

        Update update = new Update()
                .inc("pettyCash", entry.getAmount())
                .push("allPettyCashDetails", entry);

        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                ManagedShop.class);
    }

    @Override
    public List<Document> findShopNames() {
        Query query = new Query().with(Sort.by("shopName"));
        query.fields().include("_id").include("shopName");
        return mongoTemplate.find(query, Document.class, SHOPS);
    }

    @Override
    public Optional<String> findShopIdOfEmployee(String userId) {
        Query query = new Query(Criteria.where("employeeIds").is(userId)).limit(1);
        query.fields().include("_id");
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, SHOPS))
                .map(shop -> shop.get("_id").toString());
    }

    /**
     * Aggregation: match the shop, unwind its history, filter, newest first, then one page and
     * the total in a single round trip. (The legacy endpoints loaded the whole shop document.)
     */
    @Override
    public Optional<PettyCashPage> findPettyCashHistory(String shopId, Date from, Date before, Double amount,
                                                        int page, int size) {
        Object id = toId(shopId);
        if (!mongoTemplate.exists(new Query(Criteria.where("_id").is(id)), SHOPS)) {
            return Optional.empty();
        }

        List<Criteria> filters = new ArrayList<>();
        if (from != null) {
            filters.add(Criteria.where("allPettyCashDetails.addingDate").gte(from));
        }
        if (before != null) {
            filters.add(Criteria.where("allPettyCashDetails.addingDate").lt(before));
        }
        if (amount != null) {
            filters.add(Criteria.where("allPettyCashDetails.amount").is(amount));
        }
        Criteria entryFilter = filters.isEmpty() ? new Criteria() : new Criteria().andOperator(filters.toArray(new Criteria[0]));

        List<AggregationOperation> pipeline = List.of(
                Aggregation.match(Criteria.where("_id").is(id)),
                Aggregation.project("allPettyCashDetails"),
                Aggregation.unwind("allPettyCashDetails"),
                Aggregation.match(entryFilter),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "allPettyCashDetails.addingDate")),
                Aggregation.facet(Aggregation.count().as("total")).as("total")
                        .and(Aggregation.skip((long) page * size), Aggregation.limit(size)).as("entries"));

        Document result = mongoTemplate.aggregate(Aggregation.newAggregation(pipeline), SHOPS, Document.class)
                .getUniqueMappedResult();

        long total = 0;
        List<PettyCashEntry> entries = new ArrayList<>();
        if (result != null) {
            List<Document> totals = result.getList("total", Document.class);
            if (!totals.isEmpty()) {
                total = ((Number) totals.get(0).get("total")).longValue();
            }
            for (Document row : result.getList("entries", Document.class)) {
                if (row.get("allPettyCashDetails") instanceof Document detail) {
                    Object added = detail.get("addingDate");
                    Object value = detail.get("amount");
                    Object addedBy = detail.get("addedBy");
                    entries.add(PettyCashEntry.builder()
                            .addingDate(added instanceof Date date ? date : null)
                            .amount(value instanceof Number number ? number.doubleValue() : 0.0)
                            .addedBy(addedBy == null ? null : addedBy.toString())
                            .build());
                }
            }
        }
        return Optional.of(new PettyCashPage(entries, total));
    }

    @Override
    public Map<String, String> findUserNames(List<String> userIds) {
        Map<String, String> names = new HashMap<>();
        List<Object> ids = userIds.stream().filter(Objects::nonNull).distinct()
                .map(ShopManagementRepositoryCustomImpl::toId).toList();
        if (ids.isEmpty()) {
            return names;
        }
        Query query = new Query(Criteria.where("_id").in(ids));
        query.fields().include("_id").include("name");
        mongoTemplate.find(query, Document.class, "users").forEach(user ->
                names.put(user.get("_id").toString(), user.get("name") == null ? null : user.get("name").toString()));
        return names;
    }

    /** Spring stores String ids that are valid ObjectId hex strings as ObjectId. */
    private static Object toId(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id) : id;
    }
}
