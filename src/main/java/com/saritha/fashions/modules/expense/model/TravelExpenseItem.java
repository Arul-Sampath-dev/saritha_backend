package com.saritha.fashions.modules.expense.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One travel line item embedded in an expense. Same stored field names as the old
 * payload.expense.TravelDetails, so existing data stays compatible.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "TravelExpenseItem", description = "A travel line item")
public class TravelExpenseItem {

    /** Kept as a String (e.g. "2026-11-12") to stay compatible with existing data. */
    @Schema(description = "Travel date (yyyy-MM-dd)", example = "2026-11-12")
    private String date;

    @Schema(description = "Starting point", example = "Anna Nagar")
    private String source;

    @Schema(description = "Destination", example = "T Nagar")
    private String destination;

    @Schema(description = "Fare / cost of this trip", example = "250.0")
    private double amount;

    @Schema(description = "Purpose of the trip", example = "Fabric pickup from supplier")
    private String description;
}
