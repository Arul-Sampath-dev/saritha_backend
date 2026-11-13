package com.saritha.fashions.modules.shopmanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

/**
 * Summary of a shop for the platform owner's "all shops" listing: basic details plus
 * the running petty cash and expense totals.
 *
 * Does not include related records (expenses, invoices, receipts, employees, petty
 * cash history). Mapped straight from the "shops" collection; shopId maps to _id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ShopSummary",
        description = "Basic details of a shop with its current petty cash balance and total expense")
public class ShopSummaryResponse {

    @Id
    @Schema(description = "Shop id (MongoDB ObjectId)", example = "665f1c2e9b1e8a3d4c7f0a12")
    private String shopId;

    @Schema(description = "Display name of the shop", example = "Saritha Fashions - Anna Nagar")
    private String shopName;

    @Schema(description = "Type / category of the shop", example = "Boutique")
    private String shopType;

    @Schema(description = "GST registration number", example = "33ABCDE1234F1Z5")
    private String gstNumber;

    @Schema(description = "Shop owner's name", example = "Saritha R")
    private String ownerName;

    @Schema(description = "Shop contact number", example = "9876543210")
    private String shopContactNumber;

    @Schema(description = "Shop address", example = "12, 2nd Avenue, Anna Nagar, Chennai")
    private String shopAddress;

    @Schema(description = "State the shop is in", example = "Tamil Nadu")
    private String shopState;

    /**
     * Current petty cash balance: all top-ups minus expenses paid in cash.
     * Maintained by setPettyCashToShop and ExpenseServiceImpl.
     */
    @Schema(description = "Current petty cash balance: all top-ups minus expenses paid in cash",
            example = "4500.0")
    private double pettyCash;

    /**
     * Running total of all expenses for the shop (every payment method).
     * Maintained by ExpenseServiceImpl on add / update / delete.
     */
    @Schema(description = "Total of all expenses for the shop, across every payment method",
            example = "12800.0")
    private double totalExpense;
}
