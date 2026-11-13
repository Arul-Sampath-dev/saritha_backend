package com.saritha.fashions.modules.expensemanagement.service;

import com.saritha.fashions.modules.expensemanagement.payload.ExpenseResponse;
import com.saritha.fashions.payload.PaginatedResponse;

import java.time.LocalDate;

/**
 * Read side of expense management.
 */
public interface ExpenseManagementReadService {

    /**
     * Expenses of a shop, filtered and paginated. Shop owner: own shop (shopId ignored);
     * platform owner: the shop given by shopId (required).
     *
     * @param type "shop", "employee" or null for both
     */
    PaginatedResponse<ExpenseResponse> listExpenses(String shopId, LocalDate from, LocalDate to, String search,
                                                    String paymentMethod, String type, String employeeUserId,
                                                    String sort, int pageNumber, int pageSize);

    /** One expense of the shop (shop owner: own shop; platform owner: any shop). */
    ExpenseResponse getExpense(String expenseId, String shopId);
}
