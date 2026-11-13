package com.saritha.fashions.modules.employeemanagement;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.employeemanagement.payload.AssignEmployeeRequest;
import com.saritha.fashions.modules.employeemanagement.payload.EmployeeResponse;
import com.saritha.fashions.modules.employeemanagement.payload.UpdateEmployeeRequest;
import com.saritha.fashions.modules.employeemanagement.service.EmployeeManagementReadService;
import com.saritha.fashions.modules.employeemanagement.service.EmployeeManagementWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Employee accounts and their shop assignment.
 *
 *  GET    /shops/{shopId}/employees                       employees of a shop
 *  GET    /employees/unassigned                           staff who work in no shop (can be assigned)
 *  PUT    /employees/{userId}/assignment   {shopId}       assign (platform owner: also move)
 *  DELETE /shops/{shopId}/employees/{userId}/assignment   remove from the shop (account stays)
 *  PATCH  /shops/{shopId}/employees/{userId}              update details
 *  DELETE /shops/{shopId}/employees/{userId}              delete the account
 *
 * Access (checked in the services): the platform OWNER for every shop; a SHOP_OWNER for their
 * own shop and staff accounts only. New accounts are created with
 * POST /v1/api/authentication/users.
 */
@RestController
@RequestMapping("/v1/api/employee-management")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Employee Management",
        description = "Employees of a shop: list, assign, remove, update, delete. The platform OWNER manages every "
                + "shop; a SHOP_OWNER manages their own shop's staff. A user works in exactly one shop. Create new "
                + "accounts with POST /v1/api/authentication/users.")
public class EmployeeManagementController {

    private static final String FORBIDDEN = "{\"message\": \"You are not authorized to access this resource\", \"status\": false}";

    private final EmployeeManagementReadService readService;
    private final EmployeeManagementWriteService writeService;

    public EmployeeManagementController(EmployeeManagementReadService readService,
                                        EmployeeManagementWriteService writeService) {
        this.readService = readService;
        this.writeService = writeService;
    }

    @Operation(summary = "List employees of a shop",
            description = "Everyone in the shop's employee list (including the shop owner), sorted by name. "
                    + "Optional `search` keeps only employees whose name, email, phone or user id contains the text "
                    + "(case-insensitive).",
            parameters = {
                    @Parameter(name = "shopId", in = ParameterIn.PATH, required = true, example = "665f1c2e9b1e8a3d4c7f0a12"),
                    @Parameter(name = "search", in = ParameterIn.QUERY, description = "Optional text filter", example = "laks")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Employees (empty array when none)",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = EmployeeResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "Shop not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"Shop Not Found\", \"status\": false}"))),
                    @ApiResponse(responseCode = "403", description = "No token, wrong role, or not your shop",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN)))
            })
    @GetMapping("/shops/{shopId}/employees")
    public ResponseEntity<List<EmployeeResponse>> listShopEmployees(
            @PathVariable String shopId,
            @RequestParam(name = "search", required = false) String search) {
        return ResponseEntity.ok(readService.listShopEmployees(shopId, search));
    }

    @Operation(summary = "List staff not assigned to any shop",
            description = "Staff accounts (tailor master, tailor, shop assistant, accountant) that work in no shop, e.g. "
                    + "after being removed from one. These can be assigned with PUT /employees/{userId}/assignment.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Unassigned staff (shopId / shopName null)",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = EmployeeResponse.class)))),
                    @ApiResponse(responseCode = "403", description = "No token or wrong role",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN)))
            })
    @GetMapping("/employees/unassigned")
    public ResponseEntity<List<EmployeeResponse>> listUnassignedStaff() {
        return ResponseEntity.ok(readService.listUnassignedStaff());
    }

    @Operation(summary = "Assign a user to a shop",
            description = "A user works in exactly one shop.\n"
                    + "- **Shop owner:** can assign a **staff** user who currently works in **no shop** to their own shop.\n"
                    + "- **Platform owner:** can assign anyone (except another platform owner) to any shop; if the user "
                    + "already works in another shop they are **moved** (their in-progress items there go back one step).\n\n"
                    + "Assigning a user to the shop they already work in changes nothing.",
            parameters = {
                    @Parameter(name = "userId", in = ParameterIn.PATH, required = true, example = "665f0a1b2c3d4e5f6a7b8c9d"),
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AssignEmployeeRequest.class),
                            examples = @ExampleObject(value = "{ \"shopId\": \"665f1c2e9b1e8a3d4c7f0a12\" }"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Assigned (the employee with their new shop)",
                            content = @Content(schema = @Schema(implementation = EmployeeResponse.class))),
                    @ApiResponse(responseCode = "400", description = "User / shop not found, platform owner, or the user "
                            + "already works in another shop (shop owners cannot move people)",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"This user already works in another shop. "
                                            + "Only the platform owner can move them.\", \"status\": false}"))),
                    @ApiResponse(responseCode = "403", description = "Not your shop, or a shop owner assigning a non-staff account",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN)))
            })
    @PutMapping("/employees/{userId}/assignment")
    public ResponseEntity<EmployeeResponse> assign(@PathVariable String userId,
                                                   @Valid @RequestBody AssignEmployeeRequest request) {
        return ResponseEntity.ok(writeService.assign(userId, request.getShopId()));
    }

    @Operation(summary = "Remove an employee from the shop",
            description = "Takes the employee out of the shop: their in-progress items go back one step (tailor â†’ CUTTING, "
                    + "shop assistant â†’ STITCHING), they are unlinked from the shop and signed out. The account is kept "
                    + "and appears in GET /employees/unassigned. The shop owner cannot be removed.",
            parameters = {
                    @Parameter(name = "shopId", in = ParameterIn.PATH, required = true, example = "665f1c2e9b1e8a3d4c7f0a12"),
                    @Parameter(name = "userId", in = ParameterIn.PATH, required = true, example = "665f0a1b2c3d4e5f6a7b8c9d"),
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Removed (the employee, now without a shop)",
                            content = @Content(schema = @Schema(implementation = EmployeeResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Not an employee of this shop, or the shop owner",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"User is not an employee of this shop\", \"status\": false}"))),
                    @ApiResponse(responseCode = "403", description = "No token, wrong role, or not your shop",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN)))
            })
    @DeleteMapping("/shops/{shopId}/employees/{userId}/assignment")
    public ResponseEntity<EmployeeResponse> removeAssignment(@PathVariable String shopId, @PathVariable String userId) {
        return ResponseEntity.ok(writeService.removeAssignment(shopId, userId));
    }

    @Operation(summary = "Update an employee",
            description = "Changes only the fields sent (name, email, phone, salary, address); send at least one. Email is "
                    + "stored lower-case and must be unique; changing it signs the employee out (they sign in with the "
                    + "new email). Send \"\" for phone or address to clear it. Roles and passwords are not changed here. "
                    + "Shop owners can edit staff accounts and their own account.",
            parameters = {
                    @Parameter(name = "shopId", in = ParameterIn.PATH, required = true, example = "665f1c2e9b1e8a3d4c7f0a12"),
                    @Parameter(name = "userId", in = ParameterIn.PATH, required = true, example = "665f0a1b2c3d4e5f6a7b8c9d"),
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = UpdateEmployeeRequest.class),
                            examples = @ExampleObject(value = "{ \"name\": \"Lakshmi R\", \"phone\": \"9876543210\", \"salary\": 19000 }"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Updated employee",
                            content = @Content(schema = @Schema(implementation = EmployeeResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation failed, nothing to update, or not an employee of this shop",
                            content = @Content(examples = {
                                    @ExampleObject(name = "Nothing to update", value = "{\"anyFieldProvided\": \"Provide at least one of name, email, phone, salary, address\"}"),
                                    @ExampleObject(name = "Invalid field", value = "{\"phone\": \"phone must be a 10-digit number\"}")
                            })),
                    @ApiResponse(responseCode = "403", description = "Not your shop, or editing another shop owner",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN))),
                    @ApiResponse(responseCode = "409", description = "Email already used by another account",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"An account with email lakshmi@example.com already exists\", \"status\": false}")))
            })
    @PatchMapping("/shops/{shopId}/employees/{userId}")
    public ResponseEntity<EmployeeResponse> update(@PathVariable String shopId, @PathVariable String userId,
                                                   @Valid @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(writeService.update(shopId, userId, request));
    }

    @Operation(summary = "Delete an employee account",
            description = "Permanently deletes the account: in-progress items go back one step, the user is removed from "
                    + "every shop, and they can no longer sign in. Recorded as USER_DELETED in the activity log. Shop "
                    + "owner and platform owner accounts, and your own account, cannot be deleted. To keep the account, "
                    + "use DELETE .../assignment instead.",
            parameters = {
                    @Parameter(name = "shopId", in = ParameterIn.PATH, required = true, example = "665f1c2e9b1e8a3d4c7f0a12"),
                    @Parameter(name = "userId", in = ParameterIn.PATH, required = true, example = "665f0a1b2c3d4e5f6a7b8c9d"),
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deleted"),
                    @ApiResponse(responseCode = "400", description = "Not an employee of this shop, or a protected account",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"Shop owner and platform owner accounts cannot be deleted here\", \"status\": false}"))),
                    @ApiResponse(responseCode = "403", description = "No token, wrong role, or not your shop",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN)))
            })
    @DeleteMapping("/shops/{shopId}/employees/{userId}")
    public ResponseEntity<Void> delete(@PathVariable String shopId, @PathVariable String userId) {
        writeService.delete(shopId, userId);
        return ResponseEntity.noContent().build();
    }
}
