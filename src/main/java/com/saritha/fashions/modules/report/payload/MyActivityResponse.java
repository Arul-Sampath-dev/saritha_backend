package com.saritha.fashions.modules.report.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The logged-in employee's own activity for a period.
 */
@Schema(name = "MyActivity", description = "The logged-in user's own activity counts")
public record MyActivityResponse(
        @Schema(example = "665f0a1b2c3d4e5f6a7b8c9e") String employeeId,
        @Schema(example = "2026-11-01") LocalDate from,
        @Schema(example = "2026-11-30") LocalDate to,
        @Schema(example = "57") long total,
        @Schema(description = "Activity type → count", example = "{\"TAILOR_STITCHING\": 21, \"ASSIGNED_TO_ASSISTANT\": 20}")
        Map<String, Long> countsByType,
        List<ActivityDayResponse> daily) {
}
