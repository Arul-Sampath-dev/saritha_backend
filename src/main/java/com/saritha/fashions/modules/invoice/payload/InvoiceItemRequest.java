package com.saritha.fashions.modules.invoice.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.saritha.fashions.modules.invoice.model.ItemMeasurement;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    @Schema(description = "Item name / category. Embroidery, Lining Material, Saree Fall, Saree Pico and "
            + "Designing start in CUTTING; everything else starts in ORDER_ACCEPTED.",
            example = "Blouse", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemName;

    @NotNull(message = "itemQuantity is required")
    @Min(value = 1, message = "itemQuantity must be at least 1")
    @Max(value = 1000, message = "itemQuantity must be at most 1000")
    @Schema(description = "Quantity", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer itemQuantity;

    @NotNull(message = "itemPrice is required")
    @DecimalMin(value = "0", message = "itemPrice must not be negative")
    @DecimalMax(value = "10000000", message = "itemPrice is too large")
    @Schema(description = "Unit price (before GST)", example = "850", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double itemPrice;

    @Schema(description = "Measurements as name → value", example = "{\"Chest\": \"36\", \"Length\": \"14\"}")
    private Map<String, String> measurements;

    @Schema(description = "Structured measurements")
    private List<ItemMeasurement> measurementList;
}
