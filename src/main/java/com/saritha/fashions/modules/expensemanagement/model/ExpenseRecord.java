package com.saritha.fashions.modules.expensemanagement.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Expense entity used by the ExpenseManagement module.
 *
 * Maps to the same "expenses" collection as com.saritha.fashions.model.Expense and uses
 * the same field names, so expenses written here are readable by the old code and
 * vice versa. Keep the fields in sync with the original Expense model.
 */
@Document(collection = "expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "shopId_expenseDate_idx", def = "{shopId: 1, expenseDate: -1}")
@CompoundIndex(name = "shopId_search_idx", def = "{shopId: 1, expenseType: 1, notes: 1}")
@Schema(name = "ExpenseRecord", description = "An expense recorded against a shop")
public class ExpenseRecord {

    @Id
    private String id;
    private String shopId;
    private String expenseType;
    private double expenseAmount;

    /**
     * Employee the expense belongs to. Null for a shop expense; set to the employee's
     * user id for an employee expense. (Not the shop owner who entered it.)
     */
    private String userId;
    private String notes;

    private Date expenseDate;

    /** Stored as "Cash" / "Bank" etc. Cash expenses are deducted from petty cash. */
    private String paymentMethod;

    @Builder.Default
    private List<ExpenseItem> refreshmentDetails = new ArrayList<>();

    @Builder.Default
    private List<ExpenseItem> miscellaneousDetails = new ArrayList<>();

    @Builder.Default
    private List<TravelExpenseItem> travelDetails = new ArrayList<>();
}
