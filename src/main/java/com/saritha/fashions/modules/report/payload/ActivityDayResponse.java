package com.saritha.fashions.modules.report.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Number of activities on one day (IST). Every day of the range is present, zeros included.
 */
@Schema(name = "ActivityDay", description = "Activities on one day")
public record ActivityDayResponse(
        @Schema(example = "2026-11-12") LocalDate date,
        @Schema(example = "7") long count) {
}
