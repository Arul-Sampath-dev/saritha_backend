package com.saritha.fashions.modules.invoicemanagement.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Customer details on an invoice. The customer is found by phone number; a new
 * customer is created only if no customer with that phone exists.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "InvoiceCustomerRequest",
        description = "Customer details. Matched by phone; a new customer is created only if the phone is new.")
public class CustomerDetailsRequest {

    @NotBlank(message = "customer.name is required")
    @Size(max = 100, message = "customer.name must be at most 100 characters")
    @Schema(description = "Customer name", example = "Priya K", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "customer.phone is required")
    @Pattern(regexp = "^\\d{10}$", message = "customer.phone must be a 10-digit mobile number")
    @Schema(description = "10-digit mobile number (used for WhatsApp, +91 is added)", example = "9876543210",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @Email(message = "customer.email must be a valid email address")
    @Size(max = 150, message = "customer.email must be at most 150 characters")
    @Schema(description = "Optional email; if present the invoice link is also emailed", example = "priya@example.com")
    private String email;

    @Pattern(regexp = "^$|^\\d{10}$", message = "customer.alternatePhone must be a 10-digit number")
    @Schema(description = "Optional alternate phone", example = "9123456780")
    private String alternatePhone;

    @Size(max = 300, message = "customer.address must be at most 300 characters")
    @Schema(description = "Optional address", example = "12, 2nd Avenue, Anna Nagar, Chennai")
    private String address;
}
