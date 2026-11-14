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
