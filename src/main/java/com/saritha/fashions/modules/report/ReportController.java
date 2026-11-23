package com.saritha.fashions.modules.report;

import org.springframework.web.bind.annotation.PathVariable;
import com.saritha.fashions.modules.report.payload.DeletedInvoiceDetailResponse;
import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.report.payload.ActivityDayResponse;
import com.saritha.fashions.modules.report.payload.ActivityResponse;
import com.saritha.fashions.modules.report.payload.DailyFinancialsResponse;
import com.saritha.fashions.modules.report.payload.DeletedInvoiceResponse;
import com.saritha.fashions.modules.report.payload.DeliveryDashboardResponse;
import com.saritha.fashions.modules.report.payload.EmployeeActivitySummaryResponse;
import com.saritha.fashions.modules.report.payload.EmployeeAnalyticsResponse;
import com.saritha.fashions.modules.report.payload.FinancialSummaryResponse;
import com.saritha.fashions.modules.report.payload.MonthlyFinancialsResponse;
import com.saritha.fashions.modules.report.payload.MyActivityResponse;
import com.saritha.fashions.modules.report.payload.ShopOverviewResponse;
import com.saritha.fashions.modules.report.service.ReportReadService;
import com.saritha.fashions.payload.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only reports, computed from the live data.
 *
 * - GET /shops/overview          per-shop dashboard (platform OWNER)
 * - GET /financials/summary      sales, collections, expenses, petty cash, outstanding
 * - GET /financials/daily        the same per day
 * - GET /activities              employee activity log (paginated)
 * - GET /activities/by-employee  activity counts per employee
 * - GET /activities/daily        activity counts per day
 * - GET /my-activity             the logged-in employee's own activity
 * - GET /deleted-invoices        archived invoices (paginated)
 *
 * Access (WebSecurityConfig + service): /my-activity is for shop staff; everything else for the
 * platform OWNER (any shop, or all shops without shopId) and the SHOP_OWNER (own shop only).
 */
@RestController
@RequestMapping("/v1/api/reports")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Reports",
        description = "Financial, activity and deleted-invoice reports for the platform owner and shop owners, "
                + "computed from the live data. A SHOP_OWNER always sees their own shop; the platform OWNER passes "
                + "shopId for one shop or leaves it out for all shops.")
public class ReportController {

    private static final String SHOP_ID = "Platform OWNER: the shop to report on; leave out for all shops. "
            + "Ignored for a SHOP_OWNER (always their own shop).";
    private static final String FROM = "First day, yyyy-MM-dd (IST)";
    private static final String TO = "Last day, inclusive, yyyy-MM-dd (IST)";
    private static final String BAD_REQUEST_EXAMPLE = "{\"message\": \"to must not be before from\", \"status\": false}";
    private static final String FORBIDDEN_EXAMPLE =
            "{\"message\": \"You are not authorized to access this resource\", \"status\": false}";

    private final ReportReadService reportingService;

    public ReportController(ReportReadService reportingService) {
        this.reportingService = reportingService;
    }

    @Operation(
            summary = "Shops overview (platform owner)",
            description = "One row per shop for the period: invoices, paid / delivered counts, money collected, "
                    + "expenses (all and cash), net cash flow, current pending orders and balance due, receipts, "
                    + "employees and petty cash. Without from / to the figures cover all time. Replaces the legacy "
                    + "shop dashboard, whose expense totals were always 0.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One row per shop, sorted by name",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ShopOverviewResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "to before from",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = BAD_REQUEST_EXAMPLE))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @GetMapping("/shops/overview")
    public ResponseEntity<List<ShopOverviewResponse>> getShopsOverview(
            @Parameter(description = FROM, example = "2026-11-01") @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO, example = "2026-11-30") @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getShopsOverview(from, to));
    }

    @Operation(
            summary = "Financial summary",
            description = "For one shop or all shops and a period (all time without from / to):\n"
                    + "- **sales**: invoices dated in the period (count, amount with GST, taxable, GST, discount)\n"
                    + "- **collections**: money received in the period (payment entries by date), per payment method\n"
                    + "- **expenses**: expenses dated in the period, shop vs employee, per payment method\n"
                    + "- **pettyCash**: top-ups in the period and the current balance\n"
                    + "- **netCashFlow** = collections − expenses\n"
                    + "- **outstanding**: invoices not yet delivered and their balance due (now)\n"
                    + "- receipt and deleted-invoice counts in the period\n\n"
                    + "Replaces /v1/api/financials/** and /v1/api/dashboard/financial-summary/**, which read manually "
                    + "entered numbers.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The summary",
                            content = @Content(schema = @Schema(implementation = FinancialSummaryResponse.class))),
                    @ApiResponse(responseCode = "400", description = "to before from, shop not found, not assigned to a shop",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = BAD_REQUEST_EXAMPLE))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @GetMapping("/financials/summary")
    public ResponseEntity<FinancialSummaryResponse> getFinancialSummary(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = FROM, example = "2026-11-01") @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO, example = "2026-11-30") @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getFinancialSummary(shopId, from, to));
    }

    @Operation(
            summary = "Daily financials",
            description = "One row per day (IST) with invoices, amount invoiced, money collected, expenses and net "
                    + "cash flow. Every day of the range is listed, zeros included. Default: the last 30 days; at "
                    + "most 366 days.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One row per day, oldest first",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DailyFinancialsResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "to before from, range over 366 days, shop not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"A daily report can cover at most 366 days\", \"status\": false}"))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/financials/daily")
    public ResponseEntity<List<DailyFinancialsResponse>> getDailyFinancials(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = FROM + "; default 29 days before to") @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO + "; default today") @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getDailyFinancials(shopId, from, to));
    }

    @Operation(
            summary = "Financials per month",
            description = "Invoices (count, amount with GST), money collected, expenses and net cash flow per IST month "
                    + "(yyyy-MM). Without from: from the first month with any sale, payment or expense (the dashboard's "
                    + "\"Overall\" chart); without to: up to the current month. Months without data have zeros. "
                    + "A SHOP_OWNER sees their shop; the platform OWNER passes shopId or leaves it out for all shops.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One row per month, oldest first",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MonthlyFinancialsResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "to before from, shop not found"),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/financials/monthly")
    public ResponseEntity<List<MonthlyFinancialsResponse>> getMonthlyFinancials(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = FROM + "; default: the first month with data") @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO + "; default: today") @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getMonthlyFinancials(shopId, from, to));
    }

    @Operation(
            summary = "Activity log",
            description = "Employee activities (invoice created / deleted, assignments, production steps, "
                    + "deliveries), newest first. Filter by employee, type and period.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "PaginatedResponse of Activity"),
                    @ApiResponse(responseCode = "400", description = "Unknown type, bad paging, to before from",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/activities")
    public ResponseEntity<PaginatedResponse<ActivityResponse>> listActivities(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = "Only this user's activities") @RequestParam(name = "employeeId", required = false) String employeeId,
            @Parameter(description = "Activity type, e.g. INVOICE_CREATED, ASSIGNED_TO_TAILOR, ORDER_COMPLETED")
            @RequestParam(name = "type", required = false) String type,
            @Parameter(description = FROM) @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO) @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Only the activities of this invoice (invoice number), e.g. for an invoice timeline")
            @RequestParam(name = "invoiceId", required = false) String invoiceId,
            @Parameter(description = "newest (default) or oldest") @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @Parameter(description = "1 to 100") @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(reportingService.listActivities(shopId, employeeId, type, invoiceId, from, to,
                sort, pageNumber, pageSize));
    }

    @Operation(
            summary = "Activity per employee",
            description = "For each employee with activity in the period: total, counts per activity type and the "
                    + "last activity time. Most active first. Replaces /v1/api/employee/activities/summary and the "
                    + "per-role analytics.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One row per employee",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = EmployeeActivitySummaryResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "to before from, shop not found"),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/activities/by-employee")
    public ResponseEntity<List<EmployeeActivitySummaryResponse>> getActivityByEmployee(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = FROM) @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO) @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getActivityByEmployee(shopId, from, to));
    }

    @Operation(
            summary = "Activity per day",
            description = "Number of activities per day (IST) for the shop, or one employee with employeeId. Every "
                    + "day of the range is listed. Default: the last 30 days; at most 366 days. Replaces "
                    + "/v1/api/employee/activities/employee/{employeeId}/daily.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One row per day, oldest first",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ActivityDayResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "to before from, range over 366 days"),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/activities/daily")
    public ResponseEntity<List<ActivityDayResponse>> getActivityDaily(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = "Only this user's activities") @RequestParam(name = "employeeId", required = false) String employeeId,
            @Parameter(description = FROM) @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO) @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getActivityDaily(shopId, employeeId, from, to));
    }

    @Operation(
            summary = "Employee analytics",
            description = "Performance figures of one employee in the period (IST days), the same figures as the legacy "
                    + "/v1/api/employee/activities/employee/{employeeId}/analytics:\n"
                    + "- **TAILOR_MASTER**: `assigned` (items given to a tailor), quantity / value of the master's "
                    + "first-time activities, reassignments;\n"
                    + "- **TAILOR**: `totalAssigned` (items assigned to the tailor), `assigned` / `totalCompleted` "
                    + "(items handed to a shop assistant = stitched), quantity / value, reassignments;\n"
                    + "- **SHOP_ASSISTANT**: `totalAssigned`, `assigned` / `totalCompleted` (items completed), quantity / value;\n"
                    + "- **SHOP_OWNER**: the shop's invoices of the period: `itemStatusCounts`, `totalRevenue` "
                    + "(Σ price × quantity, before GST), `totalItems`, `invoiceCount`, `averageRevenuePerInvoice`, "
                    + "`receiptCount`, `deletedInvoiceCount` (employeeId not needed).\n\n"
                    + "Values are money / quantities rounded to 2 decimals. A SHOP_OWNER sees their shop; the platform "
                    + "OWNER may pass shopId.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The figures (fields that do not apply to the role are left out)",
                            content = @Content(schema = @Schema(implementation = EmployeeAnalyticsResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Unknown role, employeeId missing, to before from, shop not found"),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/activities/employee-analytics")
    public ResponseEntity<EmployeeAnalyticsResponse> getEmployeeAnalytics(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = "The employee (not needed for role SHOP_OWNER)")
            @RequestParam(name = "employeeId", required = false) String employeeId,
            @Parameter(description = "TAILOR_MASTER, TAILOR, SHOP_ASSISTANT or SHOP_OWNER", example = "TAILOR")
            @RequestParam(name = "role") String role,
            @Parameter(description = FROM) @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO) @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getEmployeeAnalytics(shopId, employeeId, role, from, to));
    }

    @Operation(
            summary = "My activity",
            description = "The logged-in shop owner's, tailor master's, tailor's or shop assistant's own activity: "
                    + "total, counts per type and per day. Default: the last 30 days; at most 366 days.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The user's activity",
                            content = @Content(schema = @Schema(implementation = MyActivityResponse.class))),
                    @ApiResponse(responseCode = "400", description = "to before from, range over 366 days"),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not shop staff")
            })
    @GetMapping("/my-activity")
    public ResponseEntity<MyActivityResponse> getMyActivity(
            @Parameter(description = FROM) @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO) @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getMyActivity(from, to));
    }

    @Operation(
            summary = "Customers",
            description = "Customer analytics from the invoices (a customer is new in the period of their first invoice in scope):\n"
                    + "- totals: customers (all time), active / new / returning in the period, repeat rate (% with 2+ invoices), "
                    + "average invoiced per active customer;\n"
                    + "- months: new vs returning customers per month (the period's months, at most 12; else the last 12);\n"
                    + "- top 20 customers by amount invoiced in the period;\n"
                    + "- customers with balance due on undelivered orders (largest first, 50 listed) + count and total.\n\n"
                    + "A SHOP_OWNER sees their shop; the platform OWNER passes shopId or leaves it out for all shops.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The report",
                            content = @Content(schema = @Schema(implementation = com.saritha.fashions.modules.report.payload.CustomerReportResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Shop not found, not assigned to a shop, to before from",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/customers")
    public ResponseEntity<com.saritha.fashions.modules.report.payload.CustomerReportResponse> getCustomers(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = FROM, example = "2026-11-01") @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO, example = "2026-11-30") @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getCustomers(shopId, from, to));
    }

    @Operation(
            summary = "Production health",
            description = "Per branch and for all of them together:\n"
                    + "- **now**: items not delivered yet per stage (ORDER_ACCEPTED … ORDER_COMPLETED), how many are on "
                    + "orders past their delivery date, and the average / longest days in the current stage;\n"
                    + "- **turnaround** of orders delivered (receipt generated) between from and to: average days from "
                    + "the invoice to delivery, % delivered on or before the expected date, and the average hours items "
                    + "spent in each stage (from the item history; READY = completed until delivery).\n\n"
                    + "A SHOP_OWNER sees their shop; the platform OWNER passes shopId or leaves it out for all shops.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The report",
                            content = @Content(schema = @Schema(implementation = com.saritha.fashions.modules.report.payload.ProductionReportResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Shop not found, not assigned to a shop, to before from",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/production")
    public ResponseEntity<com.saritha.fashions.modules.report.payload.ProductionReportResponse> getProduction(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = FROM + " (turnaround period)", example = "2026-11-01") @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO, example = "2026-11-30") @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getProduction(shopId, from, to));
    }

    @Operation(
            summary = "Delivery dashboard (today's deliveries)",
            description = "Orders to deliver on a day (default **today**, IST):\n"
                    + "- **due**: not delivered, expected delivery on the date — `ready` when every item is ORDER_COMPLETED "
                    + "(ready orders first), otherwise still in production (item statuses included);\n"
                    + "- **overdue**: not delivered, expected delivery before the date (most overdue first, with `daysOverdue`);\n"
                    + "- **delivered**: receipts generated on the date;\n"
                    + "- **summary**: counts, plus the balance still to collect on due and overdue orders.\n\n"
                    + "Open orders without a readable delivery date are only counted (`withoutDeliveryDateCount`). "
                    + "A SHOP_OWNER sees their shop; the platform OWNER passes shopId or leaves it out for all shops.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The dashboard",
                            content = @Content(schema = @Schema(implementation = DeliveryDashboardResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Shop not found, not assigned to a shop",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/deliveries")
    public ResponseEntity<DeliveryDashboardResponse> getDeliveries(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = "The day, yyyy-MM-dd (default today, IST)", example = "2026-11-14")
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportingService.getDeliveries(shopId, date));
    }

    @Operation(
            summary = "Deleted invoices",
            description = "Archived (deleted) invoices, most recently deleted first, with who deleted them and why. "
                    + "from / to filter the deletion date; search matches the invoice number or the customer's name or "
                    + "phone. Replaces /v1/api/deleted/invoices/all-deleted-invoices.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "PaginatedResponse of DeletedInvoice"),
                    @ApiResponse(responseCode = "400", description = "Bad paging, search too long, to before from",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/deleted-invoices")
    public ResponseEntity<PaginatedResponse<DeletedInvoiceResponse>> listDeletedInvoices(
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = "Invoice number, customer name or phone", example = "Priya")
            @RequestParam(name = "search", required = false) String search,
            @Parameter(description = "First deletion day") @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last deletion day, inclusive") @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @Parameter(description = "1 to 100") @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(reportingService.listDeletedInvoices(shopId, search, from, to, pageNumber, pageSize));
    }

    @Operation(
            summary = "One deleted invoice",
            description = "Everything the invoice had when it was deleted (items with measurements and statuses, amounts, "
                    + "payments, dates, notes, customer details) plus who deleted it, when and why. A SHOP_OWNER sees "
                    + "their shop's invoices only; the platform OWNER any shop (or pass shopId).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The deleted invoice",
                            content = @Content(schema = @Schema(implementation = DeletedInvoiceDetailResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Deleted invoice not found (or another shop's)"),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER")
            })
    @GetMapping("/deleted-invoices/{archiveId}")
    public ResponseEntity<DeletedInvoiceDetailResponse> getDeletedInvoice(
            @PathVariable String archiveId,
            @Parameter(description = SHOP_ID) @RequestParam(name = "shopId", required = false) String shopId) {
        return ResponseEntity.ok(reportingService.getDeletedInvoice(archiveId, shopId));
    }

}
