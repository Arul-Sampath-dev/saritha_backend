package com.saritha.fashions.modules.authentication.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Objects;

/**
 * Set a new password using the emailed code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"code", "newPassword", "confirmPassword"})
@Schema(name = "ResetPasswordRequest", description = "Email, the 6-digit code from the email, and the new password")
public class ResetPasswordRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    @Schema(example = "asha@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "code is required")
    @Pattern(regexp = "^\\d{6}$", message = "code must be the 6-digit code from the email")
    @Schema(description = "6-digit code from the email", example = "482913", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank(message = "newPassword is required")
    @Size(min = 8, max = 100, message = "newPassword must be 8-100 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "newPassword must contain at least one letter and one digit")
    @Schema(description = "8-100 characters, at least one letter and one digit", example = "NewStrong2026",
            requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.WRITE_ONLY)
    private String newPassword;

    @NotBlank(message = "confirmPassword is required")
    @Schema(description = "Must equal newPassword", example = "NewStrong2026",
            requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.WRITE_ONLY)
    private String confirmPassword;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "confirmPassword must match newPassword")
    public boolean isPasswordConfirmed() {
        return Objects.equals(newPassword, confirmPassword);
    }
}
