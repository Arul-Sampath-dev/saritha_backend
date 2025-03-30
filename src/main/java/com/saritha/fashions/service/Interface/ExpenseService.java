package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Expense;
import java.util.List;

public interface ExpenseService {
    Expense addExpense(Expense expense);
    List<Expense> getAllExpenses();
    List<Expense> getExpensesByShopId(String shopId);
    void deleteExpense(String id);

    Expense getExpenseById(String id);
    Expense updateExpense(String id, Expense updatedExpense);

}

