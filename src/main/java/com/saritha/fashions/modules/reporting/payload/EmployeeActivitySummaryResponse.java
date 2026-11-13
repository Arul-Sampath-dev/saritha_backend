package com.saritha.fashions.modules.reporting.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.Map;

/**
 * One employee's activity counts for a period.
 */
@Schema(name = "EmployeeActivitySummary", description = "How many activities of each type an employee did")
public record EmployeeActivitySummaryResponse(
        @Schema(example = "665f0a1b2c3d4e5f6a7b8c9e") String employeeId,
        @Schema(example = "Lakshmi T") String employeeName,
        @Schema(example = "57") long total,
        @Schema(description = "Activity type → count", example = "{\"TAILOR_STITCHING\": 21, \"ASSIGNED_TO_ASSISTANT\": 20}")
        Map<String, Long> countsByType,
        Date lastActivityAt) {
}
