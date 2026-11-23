package com.saritha.fashions.modules.report.export;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.core.pdf.PdfGenerationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * GET /v1/api/reports/branch-report — one branch's report for a period as Excel (full detail) or PDF (printable).
 * Security: GET /v1/api/reports/** is open to the platform OWNER and the SHOP_OWNER (WebSecurityConfig); the service
 * applies the reports' shop rule (shop owner = own shop; owner = the shopId sent).
 */
@RestController
@RequestMapping("/v1/api/reports")
@Tag(name = "Reports")
@SecurityRequirement(name = "Bearer Authentication")
public class BranchReportController {

    private static final Logger log = LoggerFactory.getLogger(BranchReportController.class);
    static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final BranchReportService service;
    private final BranchReportExcelWriter excel;
    private final BranchReportPdfWriter pdf;

    public BranchReportController(BranchReportService service, BranchReportExcelWriter excel, BranchReportPdfWriter pdf) {
        this.service = service;
        this.excel = excel;
        this.pdf = pdf;
    }

    @Operation(summary = "Download a branch report (Excel or PDF)",
            description = "Everything about one branch for a period (any range up to 366 days; one day = the same date "
                    + "for from and to): summary vs the previous period of the same length, GST split, day by day, orders, "
                    + "payments, deliveries, expenses (+ deleted), petty cash, production, staff work, customers, deleted "
                    + "invoices and sales by item.\n\n"
                    + "- **xlsx**: one sheet per section, every row, numbers as numbers, SUM totals.\n"
                    + "- **pdf**: printable A4; lists capped at 500 rows (then \"+ N more — see the Excel report\"); "
                    + "periods over 62 days show the day-by-day table per week.\n\n"
                    + "A SHOP_OWNER gets their own branch (shopId ignored); the platform OWNER must pass shopId.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The file (attachment; name SarithaFashions_<Branch>_<from>_<to>.<ext>)"),
                    @ApiResponse(responseCode = "400", description = "Missing / bad dates, more than 366 days, unknown format or shop, shopId missing for the OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER or a SHOP_OWNER"),
                    @ApiResponse(responseCode = "502", description = "The PDF service failed (the Excel report still works)",
                            content = @Content(schema = @Schema(implementation = APIResponse.class)))
            })
    @GetMapping("/branch-report")
    public ResponseEntity<?> branchReport(
            @Parameter(description = "Branch (platform OWNER; ignored for a SHOP_OWNER)", example = "665f1c2e9b1e8a3d4c7f0a12")
            @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(description = "First day, inclusive (yyyy-MM-dd, IST)", example = "2026-11-01", required = true)
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day, inclusive (yyyy-MM-dd, IST)", example = "2026-11-30", required = true)
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "xlsx (default) or pdf", example = "xlsx")
            @RequestParam(name = "format", defaultValue = "xlsx") String format) {
        String kind = format == null ? "xlsx" : format.trim().toLowerCase();
        if (!kind.equals("xlsx") && !kind.equals("pdf")) {
            throw new APIException("format must be xlsx or pdf");
        }
        BranchReportService.checkPeriod(from, to);
        BranchReportData data = service.build(shopId, from, to);
        String fileName = fileName(data.header().branchName(), from, to, kind);

        byte[] body;
        MediaType type;
        if (kind.equals("pdf")) {
            try {
                body = pdf.write(data, fileName);
            } catch (PdfGenerationException exception) {
                log.error("Branch report PDF failed for {} {}..{}: {}", data.header().shopId(), from, to, exception.getMessage(), exception);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(new APIResponse("The PDF service is not available right now. Please try again, or download the Excel report.", false));
            }
            type = MediaType.APPLICATION_PDF;
        } else {
            body = excel.write(data);
            type = XLSX;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());
        headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    /** SarithaFashions_<Branch>_<from>_<to>.<ext> (one day: …_<date>.<ext>); the branch part keeps letters, digits and dashes. */
    static String fileName(String branchName, LocalDate from, LocalDate to, String ext) {
        String branch = branchName == null ? "Branch" : branchName;
        int dash = branch.lastIndexOf(" - ");
        if (dash >= 0 && dash + 3 < branch.length()) {
            branch = branch.substring(dash + 3); // "Saritha Fashions - Navalur" -> "Navalur"
        }
        branch = branch.trim().replaceAll("[^A-Za-z0-9-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (branch.isEmpty()) {
            branch = "Branch";
        }
        String period = from.equals(to) ? from.toString() : from + "_" + to;
        return "SarithaFashions_" + branch + "_" + period + "." + ext;
    }
}
