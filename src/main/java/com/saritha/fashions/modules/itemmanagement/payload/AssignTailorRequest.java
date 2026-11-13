package com.saritha.fashions.modules.itemmanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Assign (or re-assign) a tailor to an item.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AssignTailorRequest", description = "Tailor to assign")
public class AssignTailorRequest {

    @NotBlank(message = "tailorId is required")
    @Schema(description = "User id of a TAILOR who works in the same shop "
            + "(see GET /v1/api/item-management/assignees/tailors)",
            example = "665f0a1b2c3d4e5f6a7b8c9d", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tailorId;
}
