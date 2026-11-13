package com.saritha.fashions.modules.authentication.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A plain confirmation message.
 */
@Schema(name = "AuthMessage")
public record AuthMessageResponse(@Schema(example = "Password has been changed") String message) {
}
