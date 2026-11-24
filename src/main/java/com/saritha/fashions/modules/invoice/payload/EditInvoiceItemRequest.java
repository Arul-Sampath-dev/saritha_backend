package com.saritha.fashions.modules.invoice.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.saritha.fashions.modules.invoice.model.ItemMeasurement;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * One item in an edit-invoice request: an existing item (with itemUniqueId) or a new one.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "EditInvoiceItemRequest", description = "An item after the edit (existing or new)")
public class EditInvoiceItemRequest {

    @Size(max = 200, message = "itemUniqueId must be at most 200 characters")
    @Schema(description = "Id of an existing item; leave out for a new item", example = "Blouse_1762935000000_0")
    private String itemUniqueId;

    @NotBlank(message = "itemName is required")
    @Size(max = 100, message = "itemName must be at most 100 characters")
    @Schema(example = "Blouse", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemName;

    @NotNull(message = "itemQuantity is required")
    @DecimalMin(value = "0.01", message = "itemQuantity must be greater than 0")
    @DecimalMax(value = "1000", message = "itemQuantity must be at most 1000")
    @Digits(integer = 4, fraction = 2, message = "itemQuantity can have at most 2 decimal places")
    @Schema(description = "Quantity, whole or decimal (max 2 decimals); itemPrice is per 1 unit", example = "2.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double itemQuantity;

    @NotNull(message = "itemPrice is required")
    @DecimalMin(value = "0", message = "itemPrice must not be negative")
    @DecimalMax(value = "10000000", message = "itemPrice is too large")
    @Schema(description = "Unit price (before GST)", example = "850", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double itemPrice;

    @Schema(description = "Measurements as name → value (replaces the old ones; null keeps them)",
            example = "{\"Chest\": \"36\", \"Length\": \"14\"}")
    private Map<String, String> measurements;

    @Schema(description = "Structured measurements (replaces the old ones; null keeps them)")
    private List<ItemMeasurement> measurementList;

    @Schema(description = "True to remove the item's current image (ignored if a new image is uploaded for it)",
            example = "false")
    private Boolean removeImage;

    @Schema(description = "True when the item is only a material charge: no production steps, never assigned, "
            + "ORDER_COMPLETED. Lining Material is always a material. Changing it is only allowed before production.",
            example = "false")
    private Boolean material;
}
