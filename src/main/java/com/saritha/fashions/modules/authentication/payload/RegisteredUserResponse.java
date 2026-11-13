package com.saritha.fashions.modules.authentication.payload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The account that was created.
 */
@Schema(name = "RegisteredUser", description = "The created account")
public record RegisteredUserResponse(
        @Schema(example = "6672c3d4e5f6a7b8c9d0e1f2") String userId,
        @Schema(example = "Lakshmi") String name,
        @Schema(example = "lakshmi@example.com") String email,
        @Schema(example = "[\"TAILOR\"]") List<String> roles,
        @Schema(example = "665f1c2e9b1e8a3d4c7f0a12") String shopId) {
}
