package com.saritha.fashions.modules.employee.service;

import com.saritha.fashions.model.AppRole;

import java.util.Set;

/**
 * The logged-in manager: the platform OWNER (all shops) or a SHOP_OWNER (own shop).
 *
 * @param shopId the shop owner's shop; null for the platform owner
 */
public record EmployeeActor(String userId, String name, Set<AppRole> roles, String shopId) {

    public boolean isPlatformOwner() {
        return roles.contains(AppRole.OWNER);
    }
}
