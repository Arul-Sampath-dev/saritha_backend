package com.saritha.fashions.modules.production.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Result of marking every item of an invoice completed. */
@Schema(name = "CompleteItemsResponse", description = "Items marked completed, already completed, and any that could not be")
public record CompleteItemsResponse(
        @Schema(example = "SARI-12-11-2026-00042") String invoiceId,
        @Schema(description = "Items marked completed now (itemUniqueId)") List<String> completed,
        @Schema(description = "Items that were already completed", example = "1") int alreadyCompleted,
        @Schema(description = "Items not changed, with the reason (e.g. changed meanwhile)") List<Failure> failed) {

    public record Failure(
            @Schema(example = "Blouse_1762935000000_0") String itemUniqueId,
            @Schema(example = "Blouse") String itemName,
            @Schema(example = "The item was changed by someone else meanwhile; reload and try again") String reason) {
    }
}
