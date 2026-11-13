package com.saritha.fashions.modules.authentication.payload;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Set;

/**
 * Create a user account (platform owner only).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
@Schema(name = "RegisterUserRequest", description = "A new user account")
public class RegisterUserRequest {

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    @Schema(example = "Lakshmi", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    @Size(max = 150, message = "email must be at most 150 characters")
    @Schema(description = "Used to sign in; stored lower-case", example = "lakshmi@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 100, message = "password must be 8-100 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "password must contain at least one letter and one digit")
    @Schema(description = "8-100 characters, at least one letter and one digit", example = "Welcome2026",
            requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @NotEmpty(message = "roles must contain at least one role")
    @ArraySchema(arraySchema = @Schema(description = "Roles. AppRole names or the short names used by the old "
            + "signup (owner, shopOwner, tailorMaster, tailor, shopAssistant, accountant)."),
            schema = @Schema(example = "TAILOR"))
    private Set<String> roles;

    @Schema(description = "Shop the user works in. Required for every role except OWNER.",
            example = "665f1c2e9b1e8a3d4c7f0a12")
    private String shopId;

    @Pattern(regexp = "^$|^\\d{10}$", message = "phone must be a 10-digit number")
    @Schema(example = "9876543210")
    private String phone;

    @DecimalMin(value = "0", message = "salary must not be negative")
    @DecimalMax(value = "10000000", message = "salary is too large")
    @Schema(example = "18000")
    private Double salary;

    @Size(max = 300, message = "address must be at most 300 characters")
    @Schema(example = "12, 2nd Avenue, Anna Nagar, Chennai")
    private String address;
}
