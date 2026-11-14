package com.saritha.fashions.modules.expense.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One line item of a refreshment or miscellaneous expense, embedded in an expense.
 *
 * Stored with the same field names (amount, description) as the old
 * payload.expense.RefreshmentDetails. That old class also had pettyCash and
 * totalExpense fields, which were never meaningful per item, so they are left out;
 * old documents that contain them still load because unknown fields are ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "ExpenseItem", description = "A refreshment or miscellaneous line item")
public class ExpenseItem {

    @Schema(description = "Amount spent on this item", example = "120.0")
    private double amount;

    @Schema(description = "What the money was spent on", example = "Tea and snacks for staff")
    private String description;
}
