package com.saritha.fashions.modules.shopmanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Result of a petty cash top-up: what was added and the shop's new balance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PettyCashTopUpResponse", description = "Result of adding petty cash to a shop")
public class PettyCashTopUpResponse {

    @Schema(description = "Shop id", example = "665f1c2e9b1e8a3d4c7f0a12")
    private String shopId;

    @Schema(description = "Shop name", example = "Saritha Fashions - Anna Nagar")
    private String shopName;

    @Schema(description = "Amount added in this request", example = "5000.0")
    private double amountAdded;

    @Schema(description = "Petty cash balance after this top-up", example = "9500.0")
    private double pettyCashBalance;

    @Schema(description = "User id of the person who added the cash", example = "665f0a1b2c3d4e5f6a7b8c9d")
    private String addedBy;

    @Schema(description = "When the cash was added", example = "2026-11-12T05:30:00.000+00:00")
    private Date addedAt;
}
