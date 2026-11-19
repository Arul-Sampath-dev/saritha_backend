package com.saritha.fashions.modules.expense.service;

import com.saritha.fashions.modules.expense.payload.DeletedExpenseResponse;
import com.saritha.fashions.modules.expense.payload.ExpenseResponse;
import com.saritha.fashions.modules.expense.payload.ExpenseSummaryResponse;
import com.saritha.fashions.payload.PaginatedResponse;

import java.time.LocalDate;

/**
 * Read side of expense management.
 */
public interface ExpenseReadService {

    /**
     * Expenses of a shop, filtered and paginated. Shop owner: own shop (shopId ignored);
     * platform owner: the shop given by shopId (required).
     *
     * @param type "shop", "employee" or null for both
     */
    default PaginatedResponse<ExpenseResponse> listExpenses(String shopId, LocalDate from, LocalDate to, String search,
                                                            String paymentMethod, String type, String employeeUserId,
                                                            String sort, int pageNumber, int pageSize) {
        return listExpenses(shopId, from, to, search, paymentMethod, type, employeeUserId, null, sort, pageNumber, pageSize);
    }

    /** @param expenseType exact category (case-insensitive), or null */
    PaginatedResponse<ExpenseResponse> listExpenses(String shopId, LocalDate from, LocalDate to, String search,
                                                    String paymentMethod, String type, String employeeUserId,
                                                    String expenseType, String sort, int pageNumber, int pageSize);

    /** Totals (count, amount, per category, per payment method, shop vs employee) for the same filters as the list. */
    ExpenseSummaryResponse getExpenseSummary(String shopId, LocalDate from, LocalDate to, String search,
                                             String paymentMethod, String type, String employeeUserId, String expenseType);

    /** Deleted expenses with the deletion note, newest deletion first; from/to filter the deletion date. */
    PaginatedResponse<DeletedExpenseResponse> listDeletedExpenses(String shopId, LocalDate from, LocalDate to,
                                                                  String search, String type, int pageNumber, int pageSize);

    /** One expense of the shop (shop owner: own shop; platform owner: any shop). */
    ExpenseResponse getExpense(String expenseId, String shopId);
}
