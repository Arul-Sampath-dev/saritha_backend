package com.saritha.fashions.modules.expense.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/** A deleted expense (from the deleted_expenses archive) with who deleted it, when and why. */
@Schema(name = "DeletedExpense", description = "A deleted expense and the deletion note")
public record DeletedExpenseResponse(
        @Schema(description = "Archive entry id", example = "6674d3e2f3a4b5c6d7e8f9a0") String archiveId,
        @Schema(description = "Id the expense had", example = "6674d3e2f3a4b5c6d7e8f9a1") String expenseId,
        @Schema(example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
        @Schema(description = "Employee the expense was for (employee expenses), else null") String userId,
        @Schema(example = "Lakshmi") String employeeName,
        @Schema(example = "Electricity Bill") String expenseType,
        @Schema(example = "1850.0") double expenseAmount,
        @Schema(example = "Cash") String paymentMethod,
        @Schema(example = "EB bill for October") String notes,
        Date expenseDate,
        Date deletedAt,
        @Schema(description = "User id of who deleted it") String deletedBy,
        @Schema(example = "Saritha R") String deletedByName,
        @Schema(description = "Deletion note given by the shop owner", example = "Entered twice") String reason) {
}
