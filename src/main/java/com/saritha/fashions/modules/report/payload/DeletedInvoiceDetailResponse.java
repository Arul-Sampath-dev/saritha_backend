package com.saritha.fashions.modules.report.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.Map;

/**
 * One deleted invoice with everything it had when it was deleted (GET /v1/api/reports/deleted-invoices/{archiveId}).
 * {@code invoice} is the archived invoice as stored (items with measurements and statuses, amounts, payments,
 * dates, notes); its customer reference is replaced by the customer's details, and ids are plain strings.
 */
@Schema(name = "DeletedInvoiceDetail", description = "A deleted invoice with all its details and the deletion note")
public record DeletedInvoiceDetailResponse(
        @Schema(example = "6674d3e2f3a4b5c6d7e8f9a0") String archiveId,
        Date deletedAt,
        @Schema(description = "User id of who deleted it") String deletedBy,
        @Schema(example = "Saritha R") String deletedByName,
        @Schema(example = "Customer cancelled") String reason,
        @Schema(description = "The invoice as it was when deleted") Map<String, Object> invoice) {
}
