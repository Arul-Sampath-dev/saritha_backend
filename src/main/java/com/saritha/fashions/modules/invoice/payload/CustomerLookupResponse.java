package com.saritha.fashions.modules.invoice.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of looking up a customer by WhatsApp number while creating an invoice.
 * found = false: no customer has this number yet (every other field is null / 0).
 */
@Schema(name = "CustomerLookup", description = "Existing customer with this WhatsApp number, used to fill the invoice form")
public record CustomerLookupResponse(
        @Schema(description = "True if a customer with this number exists", example = "true") boolean found,
        @Schema(example = "CUS-8F3K2Q1ZXA") String customerId,
        @Schema(example = "Priya K") String name,
        @Schema(example = "9876543210") String phone,
        @Schema(example = "priya@example.com") String email,
        @Schema(example = "9123456780") String alternatePhone,
        @Schema(example = "12, Gandhi Street, Navalur") String address,
        @Schema(description = "State used on the customer's latest invoice", example = "Tamil Nadu") String customerState,
        @Schema(description = "Number of invoices of this customer", example = "3") int invoiceCount) {

    public static CustomerLookupResponse notFound(String phone) {
        return new CustomerLookupResponse(false, null, null, phone, null, null, null, null, 0);
    }
}
