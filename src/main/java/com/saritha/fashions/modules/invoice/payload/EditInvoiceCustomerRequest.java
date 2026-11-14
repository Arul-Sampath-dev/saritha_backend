package com.saritha.fashions.modules.invoice.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Corrections to the invoice's customer (e.g. a misspelled name after the bill was made).
 *
 * The change is made on the CUSTOMER RECORD, so it shows on every invoice, receipt and PDF of that
 * customer (same as the legacy "Edit Order"). Fields left out (null) are not changed; name and
 * phone cannot be cleared; email, alternatePhone and address are cleared with "".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "EditInvoiceCustomer", description = "Corrections to the customer's details")
public class EditInvoiceCustomerRequest {

    @Size(max = 100, message = "name must be at most 100 characters")
    @Pattern(regexp = "^(?!\\s*$)[\\s\\S]+$", message = "name must not be blank")
    @Schema(example = "Priya Krishnan")
    private String name;

    @Pattern(regexp = "^\\d{10}$", message = "phone must be a 10-digit mobile number")
    @Schema(description = "Must not belong to another customer (customers are matched by phone)", example = "9876543210")
    private String phone;

    @Email(message = "email must be a valid email address")
    @Size(max = 150, message = "email must be at most 150 characters")
    @Schema(description = "\"\" removes the email", example = "priya.k@example.com")
    private String email;

    @Pattern(regexp = "^$|^\\d{10}$", message = "alternatePhone must be a 10-digit number")
    @Schema(description = "\"\" removes it", example = "9123456780")
    private String alternatePhone;

    @Size(max = 300, message = "address must be at most 300 characters")
    @Schema(description = "\"\" removes it", example = "12, 2nd Avenue, Anna Nagar, Chennai")
    private String address;

    /** True if at least one field was sent. */
    public boolean hasChanges() {
        return name != null || phone != null || email != null || alternatePhone != null || address != null;
    }
}
