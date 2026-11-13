package com.saritha.fashions.modules.expensemanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A travel line item in an employee expense request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "TravelItemRequest", description = "A travel line item")
public class TravelItemRequest {

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "date must be in yyyy-MM-dd format")
    @Schema(description = "Travel date (yyyy-MM-dd)", example = "2026-11-12")
    private String date;

    @Size(max = 100, message = "source must be at most 100 characters")
    @Schema(description = "Starting point", example = "Anna Nagar")
    private String source;

    @Size(max = 100, message = "destination must be at most 100 characters")
    @Schema(description = "Destination", example = "T Nagar")
    private String destination;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Schema(description = "Fare / cost of this trip", example = "250",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Double amount;

    @Size(max = 200, message = "description must be at most 200 characters")
    @Schema(description = "Purpose of the trip", example = "Fabric pickup from supplier")
    private String description;
}
