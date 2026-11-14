package com.saritha.fashions.modules.expense.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Request body for an employee expense, entered by the shop owner on behalf of one of
 * the shop's employees.
 *
 * Identical to a shop expense except for one extra field: the employee's user id.
 * That id is stored as the expense's userId; a shop expense has no userId.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateEmployeeExpenseRequest",
        description = "A shop expense plus the user id of the employee it belongs to")
public class CreateEmployeeExpenseRequest extends CreateExpenseRequest {

    @NotBlank(message = "employeeUserId is required")
    @Schema(description = "User id of the employee this expense is for. "
            + "Must be an employee of the shop owner's shop.",
            example = "665f0a1b2c3d4e5f6a7b8c9d", requiredMode = Schema.RequiredMode.REQUIRED)
    private String employeeUserId;
}
