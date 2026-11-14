package com.saritha.fashions.modules.receipt.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;

import java.util.Set;

/**
 * The logged-in user working with payments / receipts.
 *
 * @param shopId the user's shop; null for the platform OWNER (who belongs to no shop)
 */
public record ReceiptActor(String userId, String name, Set<AppRole> roles, String shopId) {

    public boolean isPlatformOwner() {
        return roles.contains(AppRole.OWNER);
    }

    public boolean isShopOwner() {
        return roles.contains(AppRole.SHOP_OWNER);
    }

    /** The shop of a shop owner (never null for a shop owner). */
    public String requireShopId() {
        if (shopId == null) {
            throw new APIException("User is not assigned to any shop");
        }
        return shopId;
    }
}
