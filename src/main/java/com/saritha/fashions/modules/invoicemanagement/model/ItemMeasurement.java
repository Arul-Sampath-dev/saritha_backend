package com.saritha.fashions.modules.invoicemanagement.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A structured measurement of an item (same stored shape as Items.Measurement).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ItemMeasurement", description = "A single measurement")
public class ItemMeasurement {

    @Schema(description = "Measurement name", example = "Chest")
    private String name;

    @Schema(description = "Measurement value", example = "36")
    private String value;

    @Schema(description = "Unit", example = "inch")
    private String unit;
}
