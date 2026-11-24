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
 * One item on a new invoice. Ids, statuses and image URLs are set by the server.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "InvoiceItemRequest", description = "An item on a new invoice")
public class InvoiceItemRequest {

    @NotBlank(message = "itemName is required")
    @Size(max = 100, message = "itemName must be at most 100 characters")
    @Schema(description = "Item name / category. Lining Material is a material (created ORDER_COMPLETED, no "
            + "production steps). Embroidery, Saree Fall, Saree Pico and Designing start in CUTTING; everything else "
            + "starts in ORDER_ACCEPTED.",
            example = "Blouse", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemName;

    @NotNull(message = "itemQuantity is required")
    @DecimalMin(value = "0.01", message = "itemQuantity must be greater than 0")
    @DecimalMax(value = "1000", message = "itemQuantity must be at most 1000")
    @Digits(integer = 4, fraction = 2, message = "itemQuantity can have at most 2 decimal places")
    @Schema(description = "Quantity, whole or decimal (max 2 decimals, e.g. 2 or 2.5 metres); itemPrice is per 1 unit", example = "2.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double itemQuantity;

    @NotNull(message = "itemPrice is required")
    @DecimalMin(value = "0", message = "itemPrice must not be negative")
    @DecimalMax(value = "10000000", message = "itemPrice is too large")
    @Schema(description = "Unit price (before GST)", example = "850", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double itemPrice;

    @Schema(description = "Measurements as name → value", example = "{\"Chest\": \"36\", \"Length\": \"14\"}")
    private Map<String, String> measurements;

    @Schema(description = "Structured measurements")
    private List<ItemMeasurement> measurementList;

    @Schema(description = "True when the item is only a material charge (e.g. an \"Other\" item that is cloth): "
            + "no production steps, never assigned, created ORDER_COMPLETED. Lining Material is always a material.",
            example = "false")
    private Boolean material;
}
