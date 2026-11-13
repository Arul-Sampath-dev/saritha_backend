package com.saritha.fashions.modules.authentication.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ask for a password-reset code to be emailed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PasswordResetCodeRequest", description = "Account email")
public class PasswordResetCodeRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    @Size(max = 150, message = "email must be at most 150 characters")
    @Schema(example = "asha@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;
}
