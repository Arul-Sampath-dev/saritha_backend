package com.saritha.fashions.modules.itemmanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Assign (or re-assign) a shop assistant to an item for finishing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AssignShopAssistantRequest", description = "Shop assistant to assign")
public class AssignShopAssistantRequest {

    @NotBlank(message = "shopAssistantId is required")
    @Schema(description = "User id of a SHOP_ASSISTANT who works in the same shop "
            + "(see GET /v1/api/item-management/assignees/shop-assistants)",
            example = "665f0a1b2c3d4e5f6a7b8c9e", requiredMode = Schema.RequiredMode.REQUIRED)
    private String shopAssistantId;
}
