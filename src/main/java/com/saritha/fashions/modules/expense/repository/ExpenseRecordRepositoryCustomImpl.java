package com.saritha.fashions.modules.expense.repository;

import com.mongodb.DBRef;
import com.saritha.fashions.modules.expense.model.ExpenseRecord;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * MongoTemplate-based implementation of ExpenseRecordRepositoryCustom.
 * The class name must be the fragment interface name + "Impl" so Spring Data picks it up.
 *
 * Works on the raw "shops" collection (not on a Shop entity), so this module does not
 * depend on any other module's model. Because of that, ids are converted to ObjectId
 * by hand, matching how Spring stores String @Id values.
 */
public class ExpenseRecordRepositoryCustomImpl implements ExpenseRecordRepositoryCustom {

    private static final String SHOPS_COLLECTION = "shops";
    private static final String EXPENSES_COLLECTION = "expenses";

    private final MongoTemplate mongoTemplate;

    public ExpenseRecordRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<String> findShopIdByEmployeeId(String userId) {
        Query query = new Query(Criteria.where("employeeIds").is(userId)).limit(1);
        query.fields().include("_id");

        Document shop = mongoTemplate.findOne(query, Document.class, SHOPS_COLLECTION);
        return Optional.ofNullable(shop)
                .map(document -> document.get("_id"))
                .map(Object::toString);
    }

    @Override
    public boolean isEmployeeOfShop(String shopId, String userId) {
        Query query = new Query(Criteria.where("_id").is(toId(shopId)).and("employeeIds").is(userId));
        return mongoTemplate.exists(query, SHOPS_COLLECTION);
    }

    @Override
    public Optional<ShopExpenseBalances> applyExpenseToShop(String shopId, String expenseId,
                                                            double amount, boolean deductFromPettyCash) {
        Criteria criteria = Criteria.where("_id").is(toId(shopId));
        if (deductFromPettyCash) {
            // Only match if there is enough petty cash; checked and updated atomically.
            criteria = criteria.and("pettyCash").gte(amount);
        }

        Query query = new Query(criteria);
        query.fields().include("pettyCash").include("totalExpense");

        Update update = new Update()
                .inc("totalExpense", amount)
                .push("allExpense", new DBRef(EXPENSES_COLLECTION, toId(expenseId)));
        if (deductFromPettyCash) {
            update.inc("pettyCash", -amount);
        }

        Document updatedShop = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Document.class,
                SHOPS_COLLECTION);

        return Optional.ofNullable(updatedShop)
                .map(document -> new ShopExpenseBalances(
                        toDouble(document.get("pettyCash")),
                        toDouble(document.get("totalExpense"))));
    }

    @Override
    public Optional<ExpenseRecord> removeExpenseOfShop(String expenseId, String shopId) {
        // Mapped through the ExpenseRecord entity, so the String _id is converted for us.
        Query query = new Query(Criteria.where("id").is(expenseId).and("shopId").is(shopId));
        return Optional.ofNullable(mongoTemplate.findAndRemove(query, ExpenseRecord.class));
    }

    @Override
    public Optional<ShopExpenseBalances> revertExpenseFromShop(String shopId, String expenseId,
                                                               double amount, boolean refundToPettyCash) {
        Object shopObjectId = toId(shopId);

        Query query = new Query(Criteria.where("_id").is(shopObjectId));
        query.fields().include("pettyCash").include("totalExpense");

        Update update = new Update()
                .inc("totalExpense", -amount)
                .pull("allExpense", new DBRef(EXPENSES_COLLECTION, toId(expenseId)));
        if (refundToPettyCash) {
            update.inc("pettyCash", amount);
        }

        Document updatedShop = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Document.class,
                SHOPS_COLLECTION);

        if (updatedShop == null) {
            return Optional.empty();
        }

        // Same as the old Math.max(0, ...): never leave totalExpense negative.
        return Optional.of(new ShopExpenseBalances(
                toDouble(updatedShop.get("pettyCash")),
                clampTotalExpense(shopObjectId, toDouble(updatedShop.get("totalExpense")))));
    }

    @Override
    public Optional<ExpenseRecord> updateExpenseIfAmountUnchanged(String expenseId, String shopId,
                                                                  double expectedAmount,
                                                                  Double newAmount, String newNotes) {
        Query query = new Query(Criteria.where("id").is(expenseId)
                .and("shopId").is(shopId)
                .and("expenseAmount").is(expectedAmount));

        Update update = new Update();
        if (newAmount != null) {
            update.set("expenseAmount", newAmount);
        }
        if (newNotes != null) {
            update.set("notes", newNotes);
        }

        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), ExpenseRecord.class));
    }

    @Override
    public Optional<ShopExpenseBalances> adjustShopForAmountChange(String shopId, double delta,
                                                                   boolean affectsPettyCash,
                                                                   boolean requireSufficientPettyCash) {
        Object shopObjectId = toId(shopId);

        Criteria criteria = Criteria.where("_id").is(shopObjectId);
        if (requireSufficientPettyCash && affectsPettyCash && delta > 0) {
            // Spending more cash: only if the balance covers the extra amount.
            criteria = criteria.and("pettyCash").gte(delta);
        }

        Query query = new Query(criteria);
        query.fields().include("pettyCash").include("totalExpense");

        Update update = new Update().inc("totalExpense", delta);
        if (affectsPettyCash) {
            update.inc("pettyCash", -delta);
        }

        Document updatedShop = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true),
                Document.class, SHOPS_COLLECTION);

        if (updatedShop == null) {
            return Optional.empty();
        }
        return Optional.of(new ShopExpenseBalances(
                toDouble(updatedShop.get("pettyCash")),
                clampTotalExpense(shopObjectId, toDouble(updatedShop.get("totalExpense")))));
    }

    @Override
    public Optional<ShopExpenseBalances> findShopBalances(String shopId) {
        Query query = new Query(Criteria.where("_id").is(toId(shopId)));
        query.fields().include("pettyCash").include("totalExpense");

        Document shop = mongoTemplate.findOne(query, Document.class, SHOPS_COLLECTION);
        return Optional.ofNullable(shop)
                .map(document -> new ShopExpenseBalances(
                        toDouble(document.get("pettyCash")),
                        toDouble(document.get("totalExpense"))));
    }

    /**
     * Same as the old Math.max(0, ...): if totalExpense went negative (e.g. because of
     * old inconsistent data), reset it to 0. Returns the value to report.
     */
    private double clampTotalExpense(Object shopObjectId, double totalExpense) {
        if (totalExpense >= 0) {
            return totalExpense;
        }
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(shopObjectId).and("totalExpense").lt(0)),
                new Update().set("totalExpense", 0.0),
                SHOPS_COLLECTION);
        return 0.0;
    }

    @Override
    public ExpensePage findExpenses(String shopId, Date from, Date before, String search, String paymentMethod,
                                    Boolean employeeOnly, String employeeId, boolean oldestFirst, int page, int size) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("shopId").is(shopId));
        if (from != null) {
            filters.add(Criteria.where("expenseDate").gte(from));
        }
        if (before != null) {
            filters.add(Criteria.where("expenseDate").lt(before));
        }
        if (search != null) {
            // Pattern.quote: the text is matched literally (the legacy search compiled it as a regex).
            Pattern pattern = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
            filters.add(new Criteria().orOperator(
                    Criteria.where("expenseType").regex(pattern), Criteria.where("notes").regex(pattern)));
        }
        if (paymentMethod != null) {
            filters.add(Criteria.where("paymentMethod")
                    .regex(Pattern.compile("^" + Pattern.quote(paymentMethod) + "$", Pattern.CASE_INSENSITIVE)));
        }
        if (Boolean.TRUE.equals(employeeOnly)) {
            filters.add(Criteria.where("userId").ne(null));
        } else if (Boolean.FALSE.equals(employeeOnly)) {
            filters.add(Criteria.where("userId").is(null));
        }
        if (employeeId != null) {
            filters.add(Criteria.where("userId").is(employeeId));
        }

        Query query = new Query(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        long total = mongoTemplate.count(query, ExpenseRecord.class);
        query.with(Sort.by(oldestFirst ? Sort.Direction.ASC : Sort.Direction.DESC, "expenseDate"))
                .skip((long) page * size).limit(size);
        return new ExpensePage(mongoTemplate.find(query, ExpenseRecord.class), total);
    }

    @Override
    public Map<String, String> findUserNames(Collection<String> userIds) {
        Map<String, String> names = new HashMap<>();
        List<Object> ids = userIds.stream().filter(Objects::nonNull).distinct()
                .map(ExpenseRecordRepositoryCustomImpl::toId).toList();
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
        return ObjectId.isValid(id) ? new ObjectId(id) : id;
    }

    private static double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
