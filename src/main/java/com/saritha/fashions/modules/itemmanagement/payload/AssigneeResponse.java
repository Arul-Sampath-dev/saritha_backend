package com.saritha.fashions.modules.itemmanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A person an item can be assigned to (for assignment dropdowns).
 */
@Schema(name = "ItemAssignee", description = "A tailor or shop assistant of the shop")
public record AssigneeResponse(
        @Schema(description = "User id", example = "665f0a1b2c3d4e5f6a7b8c9d") String userId,
        @Schema(description = "Name", example = "Lakshmi") String name) {
}
