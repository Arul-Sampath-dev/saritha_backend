package com.saritha.fashions.modules.employee.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update of an employee's details. Fields left out (or null) keep their value.
 * Roles, password and shop are NOT changed here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateEmployeeRequest", description = "Fields to change; send at least one")
public class UpdateEmployeeRequest {

    @Size(max = 100, message = "name must be at most 100 characters")
    @Pattern(regexp = ".*\\S.*", message = "name must not be blank")
    @Schema(example = "Lakshmi R")
    private String name;

    @Email(message = "email must be a valid email address")
    @Size(max = 150, message = "email must be at most 150 characters")
    @Schema(description = "Sign-in email; stored lower-case, must be unique. Changing it signs the employee out.",
            example = "lakshmi.r@example.com")
    private String email;

    @Pattern(regexp = "^$|^\\d{10}$", message = "phone must be a 10-digit number")
    @Schema(description = "10 digits, or \"\" to clear", example = "9876543210")
    private String phone;

    @DecimalMin(value = "0", message = "salary must not be negative")
    @DecimalMax(value = "10000000", message = "salary must not exceed 1,00,00,000")
    @Schema(example = "18000")
    private Double salary;

    @Size(max = 300, message = "address must be at most 300 characters")
    @Schema(example = "45, Usman Road, T Nagar, Chennai")
    private String address;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Provide at least one of name, email, phone, salary, address")
    public boolean isAnyFieldProvided() {
        return name != null || email != null || phone != null || salary != null || address != null;
    }
}
