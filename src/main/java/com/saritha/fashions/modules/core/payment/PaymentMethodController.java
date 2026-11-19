package com.saritha.fashions.modules.core.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The payment-method list for every form and filter, and the (platform owner) migration of stored values.
 *
 * <pre>
 *  GET  /v1/api/reference/payment-methods                    any signed-in user
 *  POST /v1/api/admin/migrations/payment-methods?dryRun=true  OWNER
 * </pre>
 */
@RestController
@RequestMapping("/v1/api")
@Tag(name = "Reference data", description = "Fixed lists used by forms and filters")
@SecurityRequirement(name = "bearerAuth")
public class PaymentMethodController {

    private final PaymentMethodMigrationService migrationService;

    public PaymentMethodController(PaymentMethodMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Operation(summary = "Payment methods",
            description = "The one list used for invoices, payments and expenses: Cash, UPI, Card, Bank Transfer, Other. "
                    + "Inputs also accept old spellings (\"cash\", \"BANK\", \"Debit/Credit Cards\", ...) and store the "
                    + "canonical name; reports and filters map old stored spellings too.")
    @GetMapping("/reference/payment-methods")
    public ResponseEntity<List<String>> paymentMethods() {
        return ResponseEntity.ok(PaymentMethods.ALL);
    }

    @Operation(summary = "Migrate stored payment methods (platform owner)",
            description = "Rewrites old spellings in invoices (paymentMethod, paymentHistories.paidVia), receipts, "
                    + "payment receipts and expenses to the canonical names. Unknown spellings are listed, not changed. "
                    + "Idempotent. Run with dryRun=true (default) first to see what would change.",
            responses = @ApiResponse(responseCode = "200", description = "What was (or would be) changed"))
    @PostMapping("/admin/migrations/payment-methods")
    public ResponseEntity<PaymentMethodMigrationService.Result> migrate(
            @Parameter(description = "true (default) = only report") @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(migrationService.migrate(dryRun));
    }
}
