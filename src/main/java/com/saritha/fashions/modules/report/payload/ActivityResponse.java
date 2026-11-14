package com.saritha.fashions.modules.report.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * One entry of the employee activity log.
 */
@Schema(name = "Activity", description = "Something an employee did (invoice created, item assigned, ...)")
public record ActivityResponse(
        @Schema(example = "6674d3e2f3a4b5c6d7e8f9a0") String id,
        @Schema(example = "665f1c2e9b1e8a3d4c7f0a12") String shopId,
        @Schema(description = "User id of who did it", example = "665f0a1b2c3d4e5f6a7b8c9d") String employeeId,
        @Schema(example = "Saritha R") String employeeName,
        @Schema(description = "Activity type (ActivityType)", example = "ASSIGNED_TO_TAILOR") String type,
        @Schema(example = "SARI-12-11-2026-00042") String invoiceId,
        @Schema(description = "User id the item was assigned to, for assignments") String assignedTo,
        @Schema(example = "Invoice created") String notes,
        Date timestamp) {
}
