package com.saritha.fashions.modules.production.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A person an item can be assigned to (for assignment dropdowns). The load fields are only filled with
 * {@code withLoad=true} (otherwise they are left out of the JSON).
 */
@Schema(name = "ItemAssignee", description = "A tailor or shop assistant of the shop")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssigneeResponse(
        @Schema(description = "User id", example = "665f0a1b2c3d4e5f6a7b8c9d") String userId,
        @Schema(description = "Name", example = "Lakshmi") String name,
        @Schema(description = "withLoad: items assigned, not started yet (TAILOR_ASSIGNED / SHOP_ASSISTANT_ASSIGNED)",
                example = "3") Long assigned,
        @Schema(description = "withLoad: items being worked on (STITCHING / HEMMING + IRONING)", example = "2")
        Long inProgress,
        @Schema(description = "withLoad: of those, items whose delivery date has passed", example = "1") Long late) {

    public AssigneeResponse(String userId, String name) {
        this(userId, name, null, null, null);
    }
}
