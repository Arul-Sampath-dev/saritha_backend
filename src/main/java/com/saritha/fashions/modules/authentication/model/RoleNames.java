package com.saritha.fashions.modules.authentication.model;

import com.saritha.fashions.model.AppRole;

import java.util.Map;
import java.util.Optional;

/**
 * Parses role names sent by clients. Accepts the AppRole names (e.g. "SHOP_OWNER") and the
 * short names the old /v1/api/auth/signup used (e.g. "shopOwner"), case-insensitively.
 *
 * Unknown names are rejected; the old signup silently turned them into ROLE_USER.
 * ROLE_USER itself cannot be requested.
 */
public final class RoleNames {

    private RoleNames() {
    }

    private static final Map<String, AppRole> ALIASES = Map.of(
            "owner", AppRole.OWNER,
            "shopowner", AppRole.SHOP_OWNER,
            "tailormaster", AppRole.TAILOR_MASTER,
            "tailor", AppRole.TAILOR,
            "shopassistant", AppRole.SHOP_ASSISTANT,
            "accountant", AppRole.ACCOUNTANT);

    public static Optional<AppRole> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String key = value.trim().replace("_", "").replace("-", "").toLowerCase();
        return Optional.ofNullable(ALIASES.get(key));
    }

    /** Roles that work inside a shop and therefore must be linked to one. */
    public static boolean requiresShop(AppRole role) {
        return role != AppRole.OWNER && role != AppRole.ROLE_USER;
    }
}
