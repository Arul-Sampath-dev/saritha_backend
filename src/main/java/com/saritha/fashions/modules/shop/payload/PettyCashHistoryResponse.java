package com.saritha.fashions.modules.shop.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * One petty cash top-up from a shop's history.
 */
@Schema(name = "PettyCashHistoryEntry", description = "A petty cash top-up")
public record PettyCashHistoryResponse(
        @Schema(example = "5000.0") double amount,
        @Schema(description = "When the cash was added") Date addedAt,
        @Schema(description = "User id of who added it", example = "665f0a1b2c3d4e5f6a7b8c9d") String addedBy,
        @Schema(description = "Name of who added it; null if that account no longer exists", example = "Saritha R")
        String addedByName) {
}
