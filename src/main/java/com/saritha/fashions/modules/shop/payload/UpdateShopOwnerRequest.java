package com.saritha.fashions.modules.shop.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Changes to a shop owner's account (platform owner only). Fields left out (or blank) are not
 * changed; send at least one of name, phone, email, password, salary.
 *
 * Replaces ShopOwnerUpdateRequest of the legacy PUT /v1/api/owner/shop/update-owner-details
 * (newOwnerName → name, newOwnerPhone → phone, newOwnerEmail → email, newOwnerPassword → password,
 * newOwnerSalary → salary, oldOwnerUserId → ownerUserId; shopId moved to the path).
 */
@Data
@Schema(name = "UpdateShopOwnerRequest", description = "New details for the shop owner; fields left out are unchanged")
public class UpdateShopOwnerRequest {

    @Size(max = 100, message = "ownerUserId must be at most 100 characters")
    @Schema(description = "The shop owner's user id. Optional: if left out, the SHOP_OWNER of the shop is used "
            + "(must be exactly one).", example = "665f0a1b2c3d4e5f6a7b8c9d")
    private String ownerUserId;

    @Size(max = 100, message = "name must be at most 100 characters")
    @Schema(description = "New name; also saved as the shop's ownerName", example = "Saritha R")
    private String name;

    @Pattern(regexp = "^$|^\\d{10}$", message = "phone must be a 10-digit number")
    @Schema(description = "New phone; also saved as the shop's contact number", example = "9876543210")
    private String phone;

    @Email(message = "email must be a valid email address")
    @Size(max = 150, message = "email must be at most 150 characters")
    @Schema(description = "New login email (stored lower-case, must be unused). Signs the owner out.",
            example = "owner.new@example.com")
    private String email;

    @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*\\d).{8,100}$",
            message = "password must be 8-100 characters with at least one letter and one digit")
    @Schema(description = "New password (8-100 characters, a letter and a digit). Signs the owner out.",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @DecimalMin(value = "0", message = "salary must not be negative")
    @DecimalMax(value = "10000000", message = "salary is too large")
    @Schema(example = "27000")
    private Double salary;

    @Size(max = 200, message = "reason must be at most 200 characters")
    @Schema(description = "Why the details change; stored with the backup of the old details",
            example = "Owner changed phone number")
    private String reason;

    @JsonIgnore
    @AssertTrue(message = "Provide at least one of name, phone, email, password, salary")
    @Schema(hidden = true)
    public boolean isAnyFieldProvided() {
        return notBlank(name) || notBlank(phone) || notBlank(email) || notBlank(password) || salary != null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
