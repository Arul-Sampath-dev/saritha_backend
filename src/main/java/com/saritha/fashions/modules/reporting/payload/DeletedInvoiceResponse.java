package com.saritha.fashions.modules.reporting.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * One archived (deleted) invoice.
 */
@Schema(name = "DeletedInvoice", description = "An invoice that was deleted, with who deleted it and why")
public record DeletedInvoiceResponse(
        @Schema(description = "Id of the archive record", example = "6673c2d1e2f3a4b5c6d7e8f9") String archiveId,
        @Schema(example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
        @Schema(example = "SARI-12-11-2026-00042") String invoiceId,
        Date invoiceDate,
        @Schema(example = "Priya K") String customerName,
        @Schema(example = "9876543210") String customerPhone,
        @Schema(example = "3") int itemCount,
        @Schema(example = "3150.0") double totalAmountWithGST,
        @Schema(example = "1500.0") double advancePaid,
        @Schema(example = "1650.0") double balanceDue,
        @Schema(example = "Partial") String paymentStatus,
        Date deletedAt,
        @Schema(description = "User id of who deleted it", example = "665f0a1b2c3d4e5f6a7b8c9d") String deletedBy,
        @Schema(example = "Saritha R") String deletedByName,
        @Schema(example = "Duplicate invoice") String reason) {
}
