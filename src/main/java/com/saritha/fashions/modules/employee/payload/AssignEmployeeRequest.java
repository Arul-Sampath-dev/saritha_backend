package com.saritha.fashions.modules.employee.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The shop a user should work in.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AssignEmployeeRequest", description = "Target shop")
public class AssignEmployeeRequest {

    @NotBlank(message = "shopId is required")
    @Schema(example = "665f1c2e9b1e8a3d4c7f0a12", requiredMode = Schema.RequiredMode.REQUIRED)
    private String shopId;
}
