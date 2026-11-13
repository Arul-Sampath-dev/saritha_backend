package com.saritha.fashions.modules.authentication.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The signed-in user, plus the access token when one was just issued (sign-in / refresh).
 * The token is also sent in the Authorization response header, as before.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AuthenticatedUser", description = "The signed-in user (and a new access token, if one was issued)")
public class AuthenticatedUserResponse {

    @Schema(example = "665f0a1b2c3d4e5f6a7b8c9d")
    private String userId;

    @Schema(example = "Asha")
    private String name;

    @Schema(example = "asha@example.com")
    private String email;

    @Schema(example = "[\"SHOP_OWNER\"]")
    private List<String> roles;

    @Schema(description = "The user's shop; null for platform owners", example = "665f1c2e9b1e8a3d4c7f0a12")
    private String shopId;

    @Schema(example = "Saritha Fashions - Anna Nagar")
    private String shopName;

    @Schema(description = "JWT access token (also in the Authorization header). Only on sign-in / refresh.")
    private String accessToken;

    @Schema(example = "Bearer")
    private String tokenType;

    @Schema(description = "Token lifetime in seconds", example = "86400")
    private Long expiresIn;
}
