package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Expense;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.expense.ExpenseDTO;
import com.saritha.fashions.payload.invoice.InvoiceDTO;

import java.util.Date;
import java.util.List;

public interface ExpenseService {
    Expense addExpense(Expense expense);
    List<Expense> getAllExpenses();
    List<Expense> getExpensesByShopId(String shopId);
    void deleteExpense(String id);

    Expense getExpenseById(String id);
    Expense updateExpense(String id, Expense updatedExpense);

    Expense addEmployeeExpense(Expense expense);

    PaginatedResponse<ExpenseDTO> getAllExpenseFromParticularShop(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId, Date startDate, Date endDate, String term);
}

