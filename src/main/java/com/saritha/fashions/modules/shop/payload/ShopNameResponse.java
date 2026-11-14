package com.saritha.fashions.modules.shop.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A shop's id and name (for dropdowns).
 */
@Schema(name = "ShopName", description = "Shop id and name")
public record ShopNameResponse(
        @Schema(example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
        @Schema(example = "Saritha Fashions - Anna Nagar") String shopName) {
}
