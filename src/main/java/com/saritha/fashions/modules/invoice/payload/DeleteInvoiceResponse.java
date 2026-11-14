package com.saritha.fashions.modules.invoice.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * Result of deleting an invoice. The invoice is archived in "deleted_invoices".
 */
@Schema(name = "DeleteInvoiceResponse", description = "A deleted (archived) invoice")
public record DeleteInvoiceResponse(
        @Schema(example = "SARI-12-11-2026-00042") String invoiceId,
        @Schema(description = "Id of the archive record in deleted_invoices", example = "6673c2d1e2f3a4b5c6d7e8f9")
        String archiveId,
        @Schema(example = "Duplicate invoice") String reason,
        Date deletedAt,
        @Schema(example = "Invoice deleted") String message) {
}
