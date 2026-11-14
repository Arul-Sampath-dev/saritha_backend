package com.saritha.fashions.modules.shop.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Result of updating a shop owner's details.
 *
 * @param changedFields which of name / phone / email / password / salary actually changed
 * @param signedOut     true when the email or password changed: all the owner's tokens were revoked
 */
@Schema(name = "ShopOwnerUpdate", description = "The shop owner after the update")
public record ShopOwnerUpdateResponse(
        @Schema(example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
        @Schema(example = "665f0a1b2c3d4e5f6a7b8c9d") String ownerUserId,
        @Schema(example = "Saritha R") String name,
        @Schema(example = "owner.new@example.com") String email,
        @Schema(example = "9876543210") String phone,
        @Schema(example = "27000.0") Double salary,
        @Schema(example = "[\"phone\", \"salary\"]") List<String> changedFields,
        @Schema(example = "false") boolean signedOut,
        @Schema(example = "Shop owner details updated. The previous details were backed up.") String message) {
}
