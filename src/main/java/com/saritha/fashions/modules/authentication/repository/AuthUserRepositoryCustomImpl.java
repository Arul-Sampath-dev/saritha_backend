package com.saritha.fashions.modules.authentication.repository;

import com.mongodb.DBRef;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.modules.authentication.model.AuthRole;
import com.saritha.fashions.modules.authentication.model.AuthUser;
import com.saritha.fashions.util.DateTimeUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * MongoTemplate-based implementation of AuthUserRepositoryCustom.
 * The class name must be the fragment interface name + "Impl" so Spring Data picks it up.
 *
 * Works on raw documents for the parts that must match the old User / Shop layout exactly
 * (assignedShops / employees DBRefs, embedded roles).
 */
public class AuthUserRepositoryCustomImpl implements AuthUserRepositoryCustom {

    private static final String USERS = "users";
    private static final String SHOPS = "shops";
    private static final String ROLES = "roles";

    private final MongoTemplate mongoTemplate;

    public AuthUserRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<ShopInfo> findShopOfUser(String userId) {
        Query userQuery = new Query(Criteria.where("_id").is(toId(userId)));
        userQuery.fields().include("assignedShops");
        Document user = mongoTemplate.findOne(userQuery, Document.class, USERS);

        Object shopId = null;
        if (user != null && user.get("assignedShops") instanceof List<?> shops && !shops.isEmpty()
                && shops.get(0) instanceof DBRef ref) {
            shopId = ref.getId();
        }

        Query shopQuery = shopId != null
                ? new Query(Criteria.where("_id").is(shopId))
                : new Query(Criteria.where("employeeIds").is(userId)).limit(1);
        shopQuery.fields().include("_id").include("shopName");
        Document shop = mongoTemplate.findOne(shopQuery, Document.class, SHOPS);

        return Optional.ofNullable(shop).map(found ->
                new ShopInfo(found.get("_id").toString(), found.getString("shopName")));
    }

    @Override
    public boolean shopExists(String shopId) {
        return mongoTemplate.exists(new Query(Criteria.where("_id").is(toId(shopId))), SHOPS);
    }

    @Override
    public List<AuthRole> findRoles(Collection<AppRole> roleNames) {
        List<String> names = roleNames.stream().map(Enum::name).toList();
        List<AuthRole> roles = new ArrayList<>();
        mongoTemplate.find(new Query(Criteria.where("roleName").in(names)), Document.class, ROLES)
                .forEach(role -> roles.add(new AuthRole(
                        role.get("_id").toString(), AppRole.valueOf(role.getString("roleName")))));
        return roles;
    }

    @Override
    public AuthUser insertUser(AuthUser user, String shopId) {
        ObjectId id = new ObjectId();

        List<Document> roles = user.getRoles().stream()
                .map(role -> new Document("_id", toId(role.getRoleId())).append("roleName", role.getRoleName().name()))
                .toList();

        Document document = new Document("_id", id)
                .append("username", user.getUsername())
                .append("name", user.getName())
                .append("email", user.getEmail())
                .append("password", user.getPassword())
                .append("tokenVersion", 0)
                .append("phone", user.getPhone())
                .append("salary", user.getSalary())
                .append("address", user.getAddress())
                .append("roles", roles)
                .append("allExpenses", new ArrayList<>())
                .append("accountCreationDate", user.getAccountCreationDate())
                .append("createdAt", user.getAccountCreationDate())
                .append("updatedAt", user.getAccountCreationDate());
        if (shopId != null) {
            document.append("assignedShops", List.of(new DBRef(SHOPS, toId(shopId))));
        }

        mongoTemplate.insert(document, USERS); // DuplicateKeyException if email taken (unique index)
        user.setUserId(id.toHexString());
        return user;
    }

    @Override
    public void addEmployeeToShop(String shopId, String userId) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(toId(shopId))),
                new Update()
                        .addToSet("employeeIds", userId)
                        .addToSet("employees", new DBRef(USERS, toId(userId))),
                SHOPS);
    }

    @Override
    public boolean updatePasswordAndRevokeTokens(String userId, String passwordHash) {
        return mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(toId(userId))),
                new Update().set("password", passwordHash).inc("tokenVersion", 1)
                        .set("updatedAt", DateTimeUtils.getNowInIST()),
                USERS).getMatchedCount() == 1;
    }

    @Override
    public boolean revokeTokens(String userId) {
        return mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(toId(userId))),
                new Update().inc("tokenVersion", 1),
                USERS).getMatchedCount() == 1;
    }

    @Override
    public void recordLogin(String userId) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(toId(userId))),
                new Update().set("lastLoginDate", DateTimeUtils.getNowInIST())
                        .set("lastOnlineDate", DateTimeUtils.getNowInIST()),
                USERS);
    }

    /** Spring stores String ids that are valid ObjectId hex strings as ObjectId. */
    private static Object toId(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id) : id;
    }
}
