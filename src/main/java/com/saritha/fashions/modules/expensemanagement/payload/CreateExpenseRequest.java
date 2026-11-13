package com.saritha.fashions.modules.expensemanagement.payload;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Request body for recording a shop expense (rent, electricity, materials, ...),
 * entered by the shop owner. The shop is taken from the logged-in shop owner, not
 * from the body. A shop expense is stored without a userId.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateExpenseRequest", description = "A shop expense to record")
public class CreateExpenseRequest {

    @NotBlank(message = "expenseType is required")
    @Size(max = 100, message = "expenseType must be at most 100 characters")
    @Schema(description = "Category of the expense", example = "Electricity",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String expenseType;

    @NotNull(message = "expenseAmount is required")
    @DecimalMin(value = "0.01", message = "expenseAmount must be greater than 0")
    @DecimalMax(value = "10000000", message = "expenseAmount must not exceed 1,00,00,000")
    @Schema(description = "Total amount spent", example = "1850",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Double expenseAmount;

    @Size(max = 30, message = "paymentMethod must be at most 30 characters")
    @Schema(description = "How it was paid. Defaults to \"Cash\". Cash expenses are deducted "
            + "from the shop's petty cash and need enough balance.",
            example = "Cash", defaultValue = "Cash")
    private String paymentMethod;

    @Size(max = 500, message = "notes must be at most 500 characters")
    @Schema(description = "Optional notes", example = "EB bill for October")
    private String notes;

    /**
     * Optional. If null or missing, the service uses the current date-time (IST).
     * Values without a timezone ("2026-11-12" or "2026-11-12T22:00:00") are read as IST,
     * so the day never shifts; values with an offset or "Z" keep their own timezone.
     */
    @JsonFormat(timezone = "Asia/Kolkata")
    @Schema(description = "When the expense happened. Optional: if null or missing, the current "
            + "date-time (IST) is used. Accepts \"yyyy-MM-dd\", ISO date-time with an offset, or "
            + "ISO date-time without one (read as IST).",
            example = "2026-11-12T10:30:00.000+05:30", nullable = true)
    private Date expenseDate;

    @Valid
    @Schema(description = "Optional refreshment line items (tea, snacks, food, ...)")
    private List<ExpenseItemRequest> refreshmentDetails = new ArrayList<>();

    @Valid
    @Schema(description = "Optional miscellaneous line items")
    private List<ExpenseItemRequest> miscellaneousDetails = new ArrayList<>();

    @Valid
    @Schema(description = "Optional travel line items")
    private List<TravelItemRequest> travelDetails = new ArrayList<>();
}
