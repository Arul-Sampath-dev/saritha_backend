package com.saritha.fashions.modules.production.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Optional note when the shop owner marks item(s) completed. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CompleteItemRequest", description = "Optional note kept in the item history and the activity log")
public class CompleteItemRequest {

    @Size(max = 200, message = "note must be at most 200 characters")
    @Schema(example = "Ravi finished it on Monday")
    private String note;
}
