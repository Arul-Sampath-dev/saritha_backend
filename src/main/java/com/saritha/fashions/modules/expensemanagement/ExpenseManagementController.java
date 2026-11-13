package com.saritha.fashions.modules.expensemanagement;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.expensemanagement.payload.CreateEmployeeExpenseRequest;
import com.saritha.fashions.modules.expensemanagement.payload.CreateExpenseRequest;
import com.saritha.fashions.modules.expensemanagement.payload.ExpenseResponse;
import com.saritha.fashions.modules.expensemanagement.payload.UpdateExpenseRequest;
import com.saritha.fashions.modules.expensemanagement.service.ExpenseManagementReadService;
import com.saritha.fashions.modules.expensemanagement.service.ExpenseManagementWriteService;
import com.saritha.fashions.payload.PaginatedResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for expense management (write side).
 *
 * Reads (shop owner: own shop; platform owner: with shopId):
 * - GET    /expenses              filtered, paginated list
 * - GET    /expenses/{expenseId}  one expense
 *
 * Writes, SHOP OWNER only (checked in the service):
 * - POST   /expenses              shop expense, stored without a userId
 * - POST   /employee-expenses     employee expense, stored with userId = the employee
 * - PATCH  /expenses/{expenseId}  change only the amount and/or notes of an expense
 * - DELETE /expenses/{expenseId}  delete a shop or employee expense of the owner's shop
 *
 * Access: WebSecurityConfig allows OWNER and SHOP_OWNER to read (GET) and only SHOP_OWNER to
 * write; the services also check the shop and the employee's shop.
 */
@RestController
@RequestMapping("/v1/api/expense-management")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Expense Management",
        description = "Shop owners record shop expenses and expenses for their employees. Cash "
                + "expenses are deducted from the shop's petty cash. Requires a SHOP_OWNER token.")
public class ExpenseManagementController {

    private static final String AUTH_HEADER_DESCRIPTION =
            "Bearer token of a SHOP_OWNER; the expense is recorded against the owner's shop";

    private static final String INSUFFICIENT_PETTY_CASH_EXAMPLE =
            "{\"message\": \"You do not have sufficient petty cash to spend, please contact owner to provide petty cash\", \"status\": false}";

    private static final String NOT_ASSIGNED_EXAMPLE =
            "{\"message\": \"User is not assigned to any shop\", \"status\": false}";

    private static final String NOT_EMPLOYEE_EXAMPLE =
            "{\"message\": \"Employee does not belong to your shop\", \"status\": false}";

    private static final String FORBIDDEN_EXAMPLE =
            "{\"message\": \"You are not authorized to access this resource\", \"status\": false}";

    private static final String FORBIDDEN_DESCRIPTION = "No JWT sent, or the user is not a SHOP_OWNER";

    private final ExpenseManagementWriteService expenseWriteService;
    private final ExpenseManagementReadService expenseReadService;

    public ExpenseManagementController(ExpenseManagementWriteService expenseWriteService,
                                       ExpenseManagementReadService expenseReadService) {
        this.expenseWriteService = expenseWriteService;
        this.expenseReadService = expenseReadService;
    }

    /**
     * Lists the shop's expenses.
     * GET /v1/api/expense-management/expenses
     */
    @Operation(
            summary = "List expenses",
            description = "The shop's expenses, newest first (or `sort=oldest`), with optional filters. A SHOP_OWNER "
                    + "always sees their own shop; the platform OWNER must pass `shopId`. The balances fields are not "
                    + "included in lists. Replaces /v1/api/shops/view-expenses (+ /{shopId}, /byPaymentMethod), "
                    + "/v1/api/expenses/shop/{shopId} and /v1/api/expenses/all.",
            parameters = {
                    @Parameter(name = "shopId", in = ParameterIn.QUERY, description = "Platform owner only: the shop"),
                    @Parameter(name = "from", in = ParameterIn.QUERY, description = "First day of expenseDate (yyyy-MM-dd, IST)", example = "2026-11-01"),
                    @Parameter(name = "to", in = ParameterIn.QUERY, description = "Last day of expenseDate (yyyy-MM-dd, IST, inclusive)", example = "2026-11-30"),
                    @Parameter(name = "search", in = ParameterIn.QUERY, description = "Text in expense type or notes (case-insensitive)", example = "electric"),
                    @Parameter(name = "paymentMethod", in = ParameterIn.QUERY, description = "Exact payment method, any case", example = "Cash"),
                    @Parameter(name = "type", in = ParameterIn.QUERY, description = "shop or employee (default: both)", example = "employee"),
                    @Parameter(name = "employeeUserId", in = ParameterIn.QUERY, description = "Only this employee's expenses"),
                    @Parameter(name = "sort", in = ParameterIn.QUERY, description = "newest (default) or oldest", example = "newest"),
                    @Parameter(name = "pageNumber", in = ParameterIn.QUERY, example = "0"),
                    @Parameter(name = "pageSize", in = ParameterIn.QUERY, description = "1-100", example = "20")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "A page of expenses (content = ExpenseResponse[])"),
                    @ApiResponse(responseCode = "400", description = "Bad filter or paging, missing shopId (platform owner), or shop not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"type must be shop or employee\", \"status\": false}"))),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner or the platform owner",
                            content = @Content(schema = @Schema(implementation = APIResponse.class)))
            })
    @GetMapping("/expenses")
    public ResponseEntity<PaginatedResponse<ExpenseResponse>> listExpenses(
            @RequestParam(name = "shopId", required = false) String shopId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "paymentMethod", required = false) String paymentMethod,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "employeeUserId", required = false) String employeeUserId,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(expenseReadService.listExpenses(shopId, from, to, search, paymentMethod, type,
                employeeUserId, sort, pageNumber, pageSize));
    }

    /**
     * One expense.
     * GET /v1/api/expense-management/expenses/{expenseId}
     */
    @Operation(
            summary = "Get an expense",
            description = "One expense of the shop. The platform OWNER must pass `shopId`.",
            parameters = {
                    @Parameter(name = "expenseId", in = ParameterIn.PATH, required = true, example = "6672a9f0c1d2e3f4a5b6c7d8"),
                    @Parameter(name = "shopId", in = ParameterIn.QUERY, description = "Platform owner only: the shop")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "The expense",
                            content = @Content(schema = @Schema(implementation = ExpenseResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Expense not found (also another shop's)",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"Expense not found\", \"status\": false}"))),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner or the platform owner",
                            content = @Content(schema = @Schema(implementation = APIResponse.class)))
            })
    @GetMapping("/expenses/{expenseId}")
    public ResponseEntity<ExpenseResponse> getExpense(@PathVariable String expenseId,
                                                      @RequestParam(name = "shopId", required = false) String shopId) {
        return ResponseEntity.ok(expenseReadService.getExpense(expenseId, shopId));
    }

    /**
     * Records a shop expense (no userId stored).
     * POST /v1/api/expense-management/expenses
     */
    @Operation(
            summary = "Record a shop expense",
            description = "The shop owner records an expense that belongs to the shop itself (rent, "
                    + "electricity, materials, ...). It is stored **without a userId**, which marks it "
                    + "as a shop expense, and added to the shop's total expense.\n\n"
                    + "If the payment method is Cash (the default), the amount is deducted from the "
                    + "shop's petty cash. The request is rejected if the petty cash balance is lower "
                    + "than the amount. The check and the deduction happen in one atomic update, so the "
                    + "balance can never go negative.\n\n"
                    + "**expenseDate** is optional: send the date of the expense from the frontend, or "
                    + "leave it out / send null to use the current date-time (IST). Accepts "
                    + "\"yyyy-MM-dd\" or an ISO date-time; values without a timezone are read as IST.",
            parameters = @Parameter(hidden = true),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateExpenseRequest.class),
                            examples = @ExampleObject(value = "{\n"
                                    + "  \"expenseType\": \"Electricity\",\n"
                                    + "  \"expenseAmount\": 1850,\n"
                                    + "  \"paymentMethod\": \"Cash\",\n"
                                    + "  \"notes\": \"EB bill for October\",\n"
                                    + "  \"expenseDate\": \"2026-11-12T10:30:00.000+05:30\"\n"
                                    + "}"))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Shop expense recorded (userId is null)",
                            content = @Content(schema = @Schema(implementation = ExpenseResponse.class))),
                    @ApiResponse(responseCode = "400",
                            description = "Validation failed, not enough petty cash, or the shop owner is not assigned to a shop",
                            content = @Content(examples = {
                                    @ExampleObject(name = "Validation failed",
                                            value = "{\"expenseAmount\": \"expenseAmount must be greater than 0\"}"),
                                    @ExampleObject(name = "Not enough petty cash", value = INSUFFICIENT_PETTY_CASH_EXAMPLE),
                                    @ExampleObject(name = "Not assigned to a shop", value = NOT_ASSIGNED_EXAMPLE)
                            })),
                    @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @PostMapping("/expenses")
    public ResponseEntity<ExpenseResponse> createExpense(@Valid @RequestBody CreateExpenseRequest request) {
        return new ResponseEntity<>(expenseWriteService.createExpense(request), HttpStatus.CREATED);
    }

    /**
     * Records an expense for one of the shop's employees (userId = employee).
     * POST /v1/api/expense-management/employee-expenses
     */
    @Operation(
            summary = "Record an employee expense",
            description = "The shop owner records an expense for one of the shop's employees. The request "
                    + "is the same as a shop expense plus one extra field, **employeeUserId**, which is "
                    + "stored as the expense's userId. The employee must belong to the shop owner's shop.\n\n"
                    + "Same petty cash rules as shop expenses: Cash (the default) is deducted from petty "
                    + "cash, and the request is rejected if the balance is lower than the amount.\n\n"
                    + "**expenseDate** is optional: if left out or null, the current date-time (IST) is used.",
            parameters = @Parameter(hidden = true),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateEmployeeExpenseRequest.class),
                            examples = @ExampleObject(value = "{\n"
                                    + "  \"employeeUserId\": \"665f0a1b2c3d4e5f6a7b8c9d\",\n"
                                    + "  \"expenseType\": \"Travel & Refreshment\",\n"
                                    + "  \"expenseAmount\": 370,\n"
                                    + "  \"paymentMethod\": \"Cash\",\n"
                                    + "  \"notes\": \"Supplier visit\",\n"
                                    + "  \"expenseDate\": \"2026-11-12T10:30:00.000+05:30\",\n"
                                    + "  \"refreshmentDetails\": [\n"
                                    + "    { \"amount\": 120, \"description\": \"Tea and snacks\" }\n"
                                    + "  ],\n"
                                    + "  \"travelDetails\": [\n"
                                    + "    { \"date\": \"2026-11-12\", \"source\": \"Anna Nagar\", \"destination\": \"T Nagar\",\n"
                                    + "      \"amount\": 250, \"description\": \"Fabric pickup\" }\n"
                                    + "  ]\n"
                                    + "}"))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Employee expense recorded (userId = the employee)",
                            content = @Content(schema = @Schema(implementation = ExpenseResponse.class))),
                    @ApiResponse(responseCode = "400",
                            description = "Validation failed, employee not in the owner's shop, not enough "
                                    + "petty cash, or the shop owner is not assigned to a shop",
                            content = @Content(examples = {
                                    @ExampleObject(name = "Validation failed",
                                            value = "{\"employeeUserId\": \"employeeUserId is required\"}"),
                                    @ExampleObject(name = "Employee not in this shop", value = NOT_EMPLOYEE_EXAMPLE),
                                    @ExampleObject(name = "Not enough petty cash", value = INSUFFICIENT_PETTY_CASH_EXAMPLE),
                                    @ExampleObject(name = "Not assigned to a shop", value = NOT_ASSIGNED_EXAMPLE)
                            })),
                    @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @PostMapping("/employee-expenses")
    public ResponseEntity<ExpenseResponse> createEmployeeExpense(
            @Valid @RequestBody CreateEmployeeExpenseRequest request) {
        return new ResponseEntity<>(expenseWriteService.createEmployeeExpense(request), HttpStatus.CREATED);
    }

    /**
     * Updates the amount and/or notes of a shop or employee expense.
     * PATCH /v1/api/expense-management/expenses/{expenseId}
     */
    @Operation(
            summary = "Update an expense's amount and/or notes",
            description = "The shop owner changes the **amount** and/or **notes** of an expense (shop or "
                    + "employee) of their own shop. Nothing else can change; to change the type, payment "
                    + "method, date or employee, delete the expense and create a new one. Fields left out "
                    + "keep their current value; send at least one.\n\n"
                    + "When the amount changes, the difference (new âˆ’ old) is applied to the shop:\n"
                    + "- total expense changes by the difference (never below 0)\n"
                    + "- **Cash** expenses: petty cash goes down by an increase (rejected if the balance "
                    + "does not cover it) and goes up by a decrease\n"
                    + "- other payment methods: petty cash is not touched\n\n"
                    + "If the same expense is edited by two requests at the same time, one of them gets a "
                    + "\"changed or deleted by another request\" error and must retry, so totals stay correct.",
            parameters = {
                    @Parameter(name = "expenseId", in = ParameterIn.PATH, required = true,
                            description = "Id of the expense to update", example = "6672a9f0c1d2e3f4a5b6c7d8"),
                    @Parameter(hidden = true)
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateExpenseRequest.class),
                            examples = {
                                    @ExampleObject(name = "Amount and notes",
                                            value = "{\n  \"expenseAmount\": 2100,\n  \"notes\": \"EB bill for October (revised)\"\n}"),
                                    @ExampleObject(name = "Notes only",
                                            value = "{\n  \"notes\": \"Paid at the EB office\"\n}"),
                                    @ExampleObject(name = "Amount only",
                                            value = "{\n  \"expenseAmount\": 1500\n}")
                            })),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Expense updated; includes the shop's new balances",
                            content = @Content(schema = @Schema(implementation = ExpenseResponse.class))),
                    @ApiResponse(responseCode = "400",
                            description = "Validation failed, expense not found in the owner's shop, not enough "
                                    + "petty cash for a cash increase, or edited by another request at the same time",
                            content = @Content(examples = {
                                    @ExampleObject(name = "Nothing to update",
                                            value = "{\"anyFieldProvided\": \"Provide expenseAmount and/or notes to update\"}"),
                                    @ExampleObject(name = "Invalid amount",
                                            value = "{\"expenseAmount\": \"expenseAmount must be greater than 0\"}"),
                                    @ExampleObject(name = "Expense not found",
                                            value = "{\"message\": \"Expense not found\", \"status\": false}"),
                                    @ExampleObject(name = "Not enough petty cash", value = INSUFFICIENT_PETTY_CASH_EXAMPLE),
                                    @ExampleObject(name = "Concurrent edit",
                                            value = "{\"message\": \"This expense was changed or deleted by another request, please reload and try again\", \"status\": false}")
                            })),
                    @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @PatchMapping("/expenses/{expenseId}")
    public ResponseEntity<ExpenseResponse> updateExpense(@PathVariable String expenseId,
                                                         @Valid @RequestBody UpdateExpenseRequest request) {
        return ResponseEntity.ok(expenseWriteService.updateExpense(expenseId, request));
    }

    /**
     * Deletes a shop or employee expense and reverses it on the shop.
     * DELETE /v1/api/expense-management/expenses/{expenseId}
     */
    @Operation(
            summary = "Delete an expense",
            description = "The shop owner deletes an expense (shop or employee) of their own shop. The "
                    + "expense is removed and its effect on the shop is reversed: it is unlinked from the "
                    + "shop, its amount is subtracted from the shop's total expense (never below 0) and, "
                    + "if it was paid in Cash, the amount is added back to petty cash.\n\n"
                    + "Each step is atomic, so deleting the same expense twice at the same time refunds "
                    + "petty cash only once. Expenses of other shops cannot be deleted and return the "
                    + "same \"Expense not found\" error as a missing id.",
            parameters = {
                    @Parameter(name = "expenseId", in = ParameterIn.PATH, required = true,
                            description = "Id of the expense to delete", example = "6672a9f0c1d2e3f4a5b6c7d8"),
                    @Parameter(hidden = true)
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "Expense deleted; no response body"),
                    @ApiResponse(responseCode = "400",
                            description = "Expense not found in the owner's shop, or the shop owner is not assigned to a shop",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = {
                                            @ExampleObject(name = "Expense not found",
                                                    value = "{\"message\": \"Expense not found\", \"status\": false}"),
                                            @ExampleObject(name = "Not assigned to a shop", value = NOT_ASSIGNED_EXAMPLE)
                                    })),
                    @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @DeleteMapping("/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@PathVariable String expenseId) {
        expenseWriteService.deleteExpense(expenseId);
        return ResponseEntity.noContent().build();
    }
}
