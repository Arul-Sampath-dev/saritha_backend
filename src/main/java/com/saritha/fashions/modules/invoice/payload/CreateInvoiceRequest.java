package com.saritha.fashions.modules.invoice.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * JSON part ("invoiceJson") of the create-invoice request.
 *
 * Field names match the JSON the frontend already sends to the old
 * /v1/api/invoices/generate-invoice endpoint; any other fields in that JSON (ids,
 * statuses, GST values, paymentHistories, ...) are ignored because the server
 * calculates them.
 *
 * Amounts: the server calculates totalAmount from the items. If the client also sends
 * totalAmount, it must match, so a frontend calculation bug is reported instead of
 * silently producing a wrong invoice.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "CreateInvoiceRequest", description = "Invoice details (sent as the 'invoiceJson' multipart part)")
public class CreateInvoiceRequest {

    @NotNull(message = "customer is required")
    @Valid
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private CustomerDetailsRequest customer;

    @NotBlank(message = "customerState is required")
    @Size(max = 50, message = "customerState must be at most 50 characters")
    @Schema(description = "Customer's state. Same state as the shop → CGST 2.5% + SGST 2.5%; "
            + "other state → IGST 5%.", example = "Tamil Nadu", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerState;

    @NotEmpty(message = "items must contain at least one item")
    @Size(max = 50, message = "an invoice can have at most 50 items")
    @Valid
    @Schema(description = "Items. Images are matched by index: the image file name must be "
            + "item_{index}_{anything}.{ext}, where index is the item's position in this list (0-based).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<InvoiceItemRequest> items;

    @DecimalMin(value = "0", message = "totalAmount must not be negative")
    @Schema(description = "Optional. Sum of itemPrice × itemQuantity. Calculated by the server; if sent, it must match.",
            example = "1700")
    private Double totalAmount;

    @DecimalMin(value = "0", message = "discount must not be negative")
    @Schema(description = "Discount on the total, before GST. Must not exceed the total.", example = "100")
    private Double discount;

    @DecimalMin(value = "0", message = "advancePaid must not be negative")
    @DecimalMax(value = "100000000", message = "advancePaid is too large")
    @Schema(description = "Amount paid now. Must not exceed the total with GST.", example = "1000")
    private Double advancePaid;

    @Size(max = 30, message = "paymentMethod must be at most 30 characters")
    @Schema(description = "How the advance was paid. Defaults to \"Cash\" when an advance is paid.", example = "UPI")
    private String paymentMethod;

    @Size(max = 100, message = "transactionId must be at most 100 characters")
    @Schema(description = "Optional payment reference (UPI / card transaction id)")
    private String transactionId;

    @Size(max = 30, message = "expectedDeliveryDate must be at most 30 characters")
    @Schema(description = "Expected delivery date (yyyy-MM-dd; 25-11-2026 and 25/11/2026 are also accepted and stored as yyyy-MM-dd). Used by the delivery dashboard.", example = "2026-11-25")
    private String expectedDeliveryDate;

    @Schema(description = "Optional order date. Defaults to the invoice date.")
    private Date orderDate;

    @Size(max = 500, message = "notes must be at most 500 characters")
    @Schema(description = "Notes on the invoice. Defaults to \"Thank you for your order\".")
    private String notes;

    @Schema(description = "True to create the invoice with a past date given in backDate", example = "false")
    private Boolean isBackDate;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "backDate must be in yyyy-MM-dd format")
    @Schema(description = "Invoice date (yyyy-MM-dd) when isBackDate is true", example = "2026-11-10")
    private String backDate;

    /** backDate is required when isBackDate is true. Not part of the JSON. */
    @AssertTrue(message = "backDate is required when isBackDate is true")
    @Schema(hidden = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isBackDateProvidedWhenRequired() {
        return !Boolean.TRUE.equals(isBackDate) || (backDate != null && !backDate.isBlank());
    }
}
