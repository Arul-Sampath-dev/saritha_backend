package com.saritha.fashions.modules.itemmanagement.service;

import com.saritha.fashions.model.AppRole;

import java.util.Set;

/**
 * The logged-in user acting on items, with their workflow roles and shop.
 */
public record ItemActor(String userId, String name, Set<AppRole> roles, String shopId) {

    public boolean has(AppRole role) {
        return roles.contains(role);
    }
}
