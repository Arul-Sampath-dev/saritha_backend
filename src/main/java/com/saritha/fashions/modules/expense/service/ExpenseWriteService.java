package com.saritha.fashions.modules.expense.service;

import com.saritha.fashions.modules.expense.payload.CreateEmployeeExpenseRequest;
import com.saritha.fashions.modules.expense.payload.CreateExpenseRequest;
import com.saritha.fashions.modules.expense.payload.ExpenseResponse;
import com.saritha.fashions.modules.expense.payload.UpdateExpenseRequest;

/**
 * Write side of expense management.
 */
public interface ExpenseWriteService {

    /**
     * Records a shop expense for the logged-in user's shop.
     */
    ExpenseResponse createExpense(CreateExpenseRequest request);

    /**
     * Records an employee expense (with itemised refreshment / miscellaneous / travel
     * details) for the logged-in user's shop.
     */
    ExpenseResponse createEmployeeExpense(CreateEmployeeExpenseRequest request);

    /**
     * Updates only the amount and/or notes of a shop or employee expense of the
     * logged-in shop owner's shop, and applies any amount change to the shop's totals
     * (and petty cash for cash expenses).
     */
    ExpenseResponse updateExpense(String expenseId, UpdateExpenseRequest request);

    /**
     * Deletes a shop or employee expense of the logged-in shop owner's shop and
     * reverses it on the shop (total expense, and petty cash for cash expenses).
     */
    void deleteExpense(String expenseId);
}
