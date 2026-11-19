package com.saritha.fashions.modules.expense.repository;

import com.saritha.fashions.modules.expense.model.ExpenseRecord;

import java.util.Optional;

/**
 * Custom operations on the "shops" collection that an expense needs.
 * Implemented by ExpenseRecordRepositoryCustomImpl with MongoTemplate.
 */
public interface ExpenseRecordRepositoryCustom {

    /**
     * Returns the id of the (first) shop whose employeeIds contains the given user id.
     */
    Optional<String> findShopIdByEmployeeId(String userId);

    /**
     * True if the given user id is in the employeeIds of the given shop.
     */
    boolean isEmployeeOfShop(String shopId, String userId);

    /**
     * Applies a saved expense to its shop in ONE atomic update:
     * links the expense in shops.allExpense, adds the amount to totalExpense and,
     * for cash expenses, subtracts it from pettyCash.
     *
     * For cash expenses the update only matches when pettyCash >= amount, so the
     * balance can never go negative, even with concurrent requests.
     *
     * @return the shop's balances after the update, or empty if nothing was updated
     *         (shop not found, or not enough petty cash for a cash expense)
     */
    Optional<ShopExpenseBalances> applyExpenseToShop(String shopId, String expenseId,
                                                     double amount, boolean deductFromPettyCash);

    /**
     * Atomically deletes the expense only if it belongs to the given shop.
     * Only one of several concurrent deletes of the same expense can succeed.
     *
     * @return the deleted expense, or empty if it does not exist in that shop
     */
    Optional<ExpenseRecord> removeExpenseOfShop(String expenseId, String shopId);

    /**
     * Reverses a deleted expense on its shop in ONE atomic update: unlinks it from
     * shops.allExpense, subtracts the amount from totalExpense (never below 0) and,
     * for cash expenses, adds the amount back to pettyCash.
     *
     * @return the shop's balances after the update, or empty if the shop was not found
     */
    Optional<ShopExpenseBalances> revertExpenseFromShop(String shopId, String expenseId,
                                                        double amount, boolean refundToPettyCash);

    /**
     * Atomically updates an expense's amount and/or notes, but only if its amount is
     * still expectedAmount (optimistic check). If another request changed the amount
     * in between, nothing is updated.
     *
     * @param newAmount new amount, or null to keep it
     * @param newNotes  new notes, or null to keep them
     * @return the updated expense, or empty if it no longer matches (changed / deleted)
     */
    Optional<ExpenseRecord> updateExpenseIfAmountUnchanged(String expenseId, String shopId,
                                                           double expectedAmount,
                                                           Double newAmount, String newNotes);

    /**
     * Applies an amount change of an existing expense to its shop in ONE atomic update:
     * totalExpense += delta (never below 0) and, for cash expenses, pettyCash -= delta.
     *
     * @param requireSufficientPettyCash if true and this is a cash increase (delta > 0),
     *                                   the update only matches when pettyCash >= delta.
     *                                   Pass false when undoing an earlier adjustment.
     * @return the shop's balances after the update, or empty if nothing was updated
     *         (shop not found, or not enough petty cash for a cash increase)
     */
    Optional<ShopExpenseBalances> adjustShopForAmountChange(String shopId, double delta,
                                                            boolean affectsPettyCash,
                                                            boolean requireSufficientPettyCash);

    /**
     * Returns the shop's current balances without changing anything.
     */
    Optional<ShopExpenseBalances> findShopBalances(String shopId);

    /**
     * One page of the shop's expenses matching the filters (null = no filter), sorted by
     * expenseDate (newest first unless oldestFirst).
     *
     * @param from          inclusive start of expenseDate
     * @param before        exclusive end of expenseDate
     * @param search        case-insensitive text in expenseType or notes (matched literally)
     * @param paymentMethod case-insensitive exact payment method
     * @param employeeOnly  true = employee expenses only, false = shop expenses only, null = both
     * @param employeeId    expenses of this employee only
     */
    ExpensePage findExpenses(String shopId, java.util.Date from, java.util.Date before, String search,
                             String paymentMethod, Boolean employeeOnly, String employeeId,
                             boolean oldestFirst, int page, int size);

    /** The same, with an exact (case-insensitive) expense category filter. */
    ExpensePage findExpenses(ExpenseFilter filter, boolean oldestFirst, int page, int size);

    /** Totals of all expenses matching the filter: count, amount, per category, per payment method, shop/employee. */
    ExpenseTotals sumExpenses(ExpenseFilter filter);

    /**
     * Expense list filter (null = no filter).
     *
     * @param shopId        the shop (null = all shops, platform owner)
     * @param paymentMethod any spelling; matches every stored spelling of the same canonical method
     * @param employeeOnly  true = employee expenses, false = shop expenses, null = both
     * @param expenseType   exact category, case-insensitive (e.g. "Rent")
     */
    record ExpenseFilter(String shopId, java.util.Date from, java.util.Date before, String search,
                         String paymentMethod, Boolean employeeOnly, String employeeId, String expenseType) {
    }

    record ExpenseTotals(long count, double amount, double shopExpenses, double employeeExpenses,
                         java.util.List<GroupTotal> byType, java.util.List<GroupTotal> byPaymentMethod) {
    }

    record GroupTotal(String key, double amount, long count) {
    }

    // ------------------------------------------------------------------ deleted expenses (archive)

    /**
     * Stores a copy of the expense in deleted_expenses (before it is removed), with who deleted it, when and why.
     *
     * @return the archive id
     */
    String archiveExpense(ExpenseRecord expense, String deletedBy, String deletedByName, String reason,
                          java.util.Date deletedAt);

    /** Removes an archive entry again (used when the delete did not go through). */
    void removeArchivedExpense(String archiveId);

    /**
     * One page of archived (deleted) expenses, newest deletion first.
     *
     * @param from   inclusive start of deletedAt
     * @param before exclusive end of deletedAt
     * @param search text in the category, notes or deletion reason
     */
    DeletedExpensePage findDeletedExpenses(String shopId, java.util.Date from, java.util.Date before, String search,
                                           Boolean employeeOnly, int page, int size);

    record DeletedExpensePage(java.util.List<org.bson.Document> archives, long total) {
    }

    // ------------------------------------------------------------------ edits

    /**
     * Applies an expense change to the shop in ONE atomic update: totalExpense += totalDelta (never below 0) and
     * pettyCash -= pettyCashOut. When requireSufficientPettyCash and pettyCashOut &gt; 0, it only matches if the
     * balance covers pettyCashOut.
     */
    Optional<ShopExpenseBalances> adjustShopBalances(String shopId, double totalDelta, double pettyCashOut,
                                                     boolean requireSufficientPettyCash);

    /**
     * Sets the given fields on the expense, only if its amount and payment method are still the ones read before
     * (optimistic check).
     *
     * @return the updated expense, or empty if it changed or was deleted meanwhile
     */
    Optional<ExpenseRecord> updateExpenseIfUnchanged(String expenseId, String shopId, double expectedAmount,
                                                     String expectedPaymentMethod, java.util.Map<String, Object> changes);

    /** userId → name. */
    java.util.Map<String, String> findUserNames(java.util.Collection<String> userIds);

    record ExpensePage(java.util.List<ExpenseRecord> expenses, long total) {
    }

    /**
     * Shop balances after an expense has been applied or reverted.
     */
    record ShopExpenseBalances(double pettyCash, double totalExpense) {
    }
}
