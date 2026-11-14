package com.saritha.fashions.modules.production.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Move an item to its next production status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateItemStatusRequest", description = "The next status of the item")
public class UpdateItemStatusRequest {

    @NotBlank(message = "status is required")
    @Schema(description = "New status. Must be the next step for the item's current status: "
            + "ORDER_ACCEPTED→CUTTING, TAILOR_ASSIGNED→STITCHING, SHOP_ASSISTANT_ASSIGNED→HEMMING, "
            + "HEMMING→IRONING, IRONING→ORDER_COMPLETED. Use the assignment endpoints for "
            + "TAILOR_ASSIGNED and SHOP_ASSISTANT_ASSIGNED.",
            example = "STITCHING", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"CUTTING", "STITCHING", "HEMMING", "IRONING", "ORDER_COMPLETED"})
    private String status;
}
