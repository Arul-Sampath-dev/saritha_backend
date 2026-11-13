package com.saritha.fashions.modules.shopmanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating a shop together with its owner account.
 *
 * The owner fields follow the same rules as account registration in the authentication module
 * (RegisterUserRequest), so an invalid owner is rejected before anything is saved.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "CreateShopRequest",
        description = "New shop details plus the login details for the shop owner account")
public class CreateShopRequest {

    @NotBlank(message = "shopName is required")
    @Size(max = 100, message = "shopName must be at most 100 characters")
    @Schema(description = "Display name of the shop", example = "Saritha Fashions - Anna Nagar",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String shopName;

    @NotBlank(message = "ownerName is required")
    @Size(max = 100, message = "ownerName must be at most 100 characters")
    @Schema(description = "Full name of the shop owner", example = "Saritha R", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ownerName;

    @NotBlank(message = "ownerEmailId is required")
    @Email(message = "ownerEmailId must be a valid email address")
    @Size(max = 150, message = "ownerEmailId must be at most 150 characters")
    @Schema(description = "Owner's email; used as the owner's login", example = "owner@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String ownerEmailId;

    @Size(max = 300, message = "shopAddress must be at most 300 characters")
    @Schema(description = "Shop address; also saved as the owner's address",
            example = "12, 2nd Avenue, Anna Nagar, Chennai")
    private String shopAddress;

    @Pattern(regexp = "^$|^\\d{10}$", message = "ownerNumber must be a 10-digit number")
    @Schema(description = "Owner's phone number; also saved as the shop contact number",
            example = "9876543210")
    private String ownerNumber;

    @Size(max = 30, message = "gstNumber must be at most 30 characters")
    @Schema(description = "GST registration number of the shop", example = "33ABCDE1234F1Z5")
    private String gstNumber;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 100, message = "password must be 8-100 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "password must contain at least one letter and one digit")
    @Schema(description = "Password for the owner account (8-100 characters, at least one letter and one digit)",
            example = "StrongPass2026", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "Not used; the owner is always created with the SHOP_OWNER role", hidden = true)
    private String role;

    @DecimalMin(value = "0", message = "salary must not be negative")
    @Schema(description = "Owner's monthly salary", example = "25000")
    private double salary;
}
