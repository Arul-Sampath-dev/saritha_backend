package com.saritha.fashions.modules.invoicemanagement.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * JSON of the edit-invoice request. Every field is optional; a field that is not sent
 * (null) stays as it is.
 *
 * Not editable here: the customer, customer state, invoice date, advance / payments
 * (use receipt management) and production statuses (use item management).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "EditInvoiceRequest",
        description = "Changes to an invoice that has no receipt yet. Fields left out are not changed.")
public class EditInvoiceRequest {

    @Size(max = 30, message = "expectedDeliveryDate must be at most 30 characters")
    @Schema(description = "New expected delivery date", example = "2026-11-28")
    private String expectedDeliveryDate;

    @Size(max = 500, message = "notes must be at most 500 characters")
    @Schema(description = "New notes; an empty string resets them to \"Thank you for your order\"",
            example = "Customer will collect on Saturday")
    private String notes;

    @DecimalMin(value = "0", message = "discount must not be negative")
    @Schema(description = "New discount (before GST). Totals, GST, balance and payment status are recalculated.",
            example = "150")
    private Double discount;

    @Size(min = 1, max = 50, message = "an invoice must have between 1 and 50 items")
    @Valid
    @Schema(description = "The COMPLETE item list after the edit. Existing items are referenced by "
            + "itemUniqueId (keep them in the list, changed or not); items without itemUniqueId are added; "
            + "existing items missing from the list are removed. Items already in production can stay "
            + "but cannot be changed or removed. Images are matched by position in this list "
            + "(item_{index}_{name}.{ext}).")
    private List<EditInvoiceItemRequest> items;
}
