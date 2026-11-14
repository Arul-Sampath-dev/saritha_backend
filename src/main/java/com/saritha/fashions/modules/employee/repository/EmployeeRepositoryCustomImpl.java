package com.saritha.fashions.modules.employee.repository;

import com.mongodb.DBRef;
import com.saritha.fashions.modules.employee.model.ManagedEmployee;
import com.saritha.fashions.util.DateTimeUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MongoTemplate-based implementation of EmployeeRepositoryCustom.
 * The class name must be the fragment interface name + "Impl" so Spring Data picks it up.
 */
public class EmployeeRepositoryCustomImpl implements EmployeeRepositoryCustom {

    private static final String USERS = "users";
    private static final String SHOPS = "shops";
    private static final String INVOICES = "invoice";
    private static final String DELIVERED = "ORDER_DELIVERED";

    private static final List<String> TAILOR_STAGES = List.of("TAILOR_ASSIGNED", "STITCHING");
    private static final List<String> ASSISTANT_STAGES = List.of("SHOP_ASSISTANT_ASSIGNED", "HEMMING", "IRONING");

    private final MongoTemplate mongoTemplate;

    public EmployeeRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // ------------------------------------------------------------------ shops

    @Override
    public Optional<String> findShopIdOfUser(String userId) {
        Query query = new Query(Criteria.where("employeeIds").is(userId)).limit(1);
        query.fields().include("_id");
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, SHOPS))
                .map(shop -> shop.get("_id").toString());
    }

    @Override
    public Optional<ShopInfo> findShop(String shopId) {
        Query query = new Query(Criteria.where("_id").is(toId(shopId)));
        query.fields().include("_id").include("shopName").include("employeeIds");
        Document shop = mongoTemplate.findOne(query, Document.class, SHOPS);
        if (shop == null) {
            return Optional.empty();
        }
        List<String> employeeIds = new ArrayList<>();
        if (shop.get("employeeIds") instanceof List<?> ids) {
            ids.stream().filter(Objects::nonNull).map(Object::toString).forEach(employeeIds::add);
        }
        return Optional.of(new ShopInfo(shop.get("_id").toString(), asString(shop.get("shopName")), employeeIds));
    }

    @Override
    public Map<String, String> findShopNames(Collection<String> shopIds) {
        Map<String, String> names = new HashMap<>();
        List<Object> ids = shopIds.stream().filter(Objects::nonNull).distinct().map(EmployeeRepositoryCustomImpl::toId).toList();
        if (ids.isEmpty()) {
            return names;
        }
        Query query = new Query(Criteria.where("_id").in(ids));
        query.fields().include("_id").include("shopName");
        mongoTemplate.find(query, Document.class, SHOPS)
                .forEach(shop -> names.put(shop.get("_id").toString(), asString(shop.get("shopName"))));
        return names;
    }

    // ------------------------------------------------------------------ users

    @Override
    public List<ManagedEmployee> findEmployees(Collection<String> userIds) {
        List<Object> ids = userIds.stream().filter(Objects::nonNull).distinct().map(EmployeeRepositoryCustomImpl::toId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return mongoTemplate.find(new Query(Criteria.where("_id").in(ids)).with(Sort.by("name")), ManagedEmployee.class);
    }

    @Override
    public List<ManagedEmployee> findUnassignedStaff(Collection<String> staffRoleNames) {
        List<Object> assigned = mongoTemplate.findDistinct(new Query(), "employeeIds", SHOPS, Object.class).stream()
                .filter(Objects::nonNull).map(id -> toId(id.toString())).toList();
        Query query = new Query(Criteria.where("roles.roleName").in(staffRoleNames).and("_id").nin(assigned))
                .with(Sort.by("name"));
        return mongoTemplate.find(query, ManagedEmployee.class);
    }

    @Override
    public void addToShop(String userId, String shopId) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(toId(userId))),
                new Update().set("assignedShops", new ArrayList<>(List.of(new DBRef(SHOPS, toId(shopId))))), USERS);
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(toId(shopId))),
                new Update().addToSet("employeeIds", userId).addToSet("employees", new DBRef(USERS, toId(userId))), SHOPS);
    }

    @Override
    public void removeFromShop(String userId, String shopId) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(toId(userId))),
                new Update().pull("assignedShops", new DBRef(SHOPS, toId(shopId))), USERS);
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(toId(shopId))),
                new Update().pull("employeeIds", userId).pull("employees", new DBRef(USERS, toId(userId))), SHOPS);
    }

    @Override
    public void removeFromAllShops(String userId) {
        mongoTemplate.updateMulti(new Query(Criteria.where("employeeIds").is(userId)),
                new Update().pull("employeeIds", userId).pull("employees", new DBRef(USERS, toId(userId))), SHOPS);
    }

    @Override
    public boolean updateUser(String userId, Update update) {
        return mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(toId(userId))), update, USERS)
                .getMatchedCount() == 1;
    }

    @Override
    public void revokeTokens(String userId) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(toId(userId))),
                new Update().inc("tokenVersion", 1), USERS);
    }

    @Override
    public void deleteUser(String userId) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(toId(userId))), USERS);
    }

    // ------------------------------------------------------------------ items

    @Override
    public long unassignFromActiveItems(String shopId, String userId, String historyAction) {
        long changed = 0;
        changed += unassignRole(shopId, userId, "assignedTo", "assignmentStatus", TAILOR_STAGES, "CUTTING", historyAction);
        changed += unassignRole(shopId, userId, "assignedToWhichShopAssistant", "ShopAssistantStatus",
                ASSISTANT_STAGES, "STITCHING", historyAction);
        return changed;
    }

    /**
     * One atomic multi-invoice update per role, touching only the matching items
     * (arrayFilters). Items whose assignmentHistory is null (old data) get an empty array
     * first, because $push fails on null.
     */
    private long unassignRole(String shopId, String userId, String assigneeField, String assignmentStatusField,
                              List<String> stages, String backTo, String historyAction) {
        Query invoices = new Query(Criteria.where("shopId").is(shopId).and("orderStatus").ne(DELIVERED)
                .and("items").elemMatch(Criteria.where(assigneeField).is(userId).and("itemOrderStatus").in(stages)));

        mongoTemplate.updateMulti(invoices, new Update()
                        .set("items.$[n].assignmentHistory", new ArrayList<>())
                        .filterArray(Criteria.where("n." + assigneeField).is(userId)
                                .and("n.itemOrderStatus").in(stages).and("n.assignmentHistory").is(null)),
                INVOICES);

        Document history = new Document("tailorId", userId)
                .append("action", historyAction)
                .append("timestamp", DateTimeUtils.getNowInIST());
        return mongoTemplate.updateMulti(invoices, new Update()
                        .set("items.$[e]." + assigneeField, null)
                        .set("items.$[e]." + assignmentStatusField, "UNASSIGNED")
                        .set("items.$[e].itemOrderStatus", backTo)
                        .push("items.$[e].assignmentHistory", history)
                        .filterArray(Criteria.where("e." + assigneeField).is(userId).and("e.itemOrderStatus").in(stages)),
                INVOICES).getModifiedCount();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** Spring stores String ids that are valid ObjectId hex strings as ObjectId. */
    private static Object toId(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id) : id;
    }
}
