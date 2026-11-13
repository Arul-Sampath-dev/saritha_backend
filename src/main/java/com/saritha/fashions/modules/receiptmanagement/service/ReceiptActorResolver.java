package com.saritha.fashions.modules.receiptmanagement.service;

import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.receiptmanagement.repository.ReceiptRecordRepository;
import com.saritha.fashions.util.AuthUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may do what with payments and receipts:
 *  - SHOP_OWNER: everything, for their own shop
 *  - OWNER (platform owner): read receipts of every shop (no payments / receipt creation)
 */
@Component
public class ReceiptActorResolver {

    private final AuthUtil authUtil;
    private final ReceiptRecordRepository repository;

    public ReceiptActorResolver(AuthUtil authUtil, ReceiptRecordRepository repository) {
        this.authUtil = authUtil;
        this.repository = repository;
    }

    /** A shop owner, with their shop. */
    public ReceiptActor requireShopOwner() {
        ReceiptActor actor = resolve();
        if (!actor.isShopOwner()) {
            throw new AccessDeniedException("Only the shop owner can record payments and receipts");
        }
        actor.requireShopId();
        return actor;
    }

    /** A shop owner (own shop) or the platform owner (all shops). */
    public ReceiptActor requireReader() {
        ReceiptActor actor = resolve();
        if (!actor.isShopOwner() && !actor.isPlatformOwner()) {
            throw new AccessDeniedException("Only shop owners and the platform owner can view receipts");
        }
        if (actor.isShopOwner() && !actor.isPlatformOwner()) {
            actor.requireShopId();
        }
        return actor;
    }

    private ReceiptActor resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required");
        }
        User user = authUtil.loggedInUser();
        Set<AppRole> roles = user.getRoles() == null ? Set.of() : user.getRoles().stream()
                .map(role -> role.getRoleName()).filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AppRole.class)));
        String shopId = repository.findShopIdByEmployeeId(user.getUserId()).orElse(null);
        return new ReceiptActor(user.getUserId(), user.getName(), roles, shopId);
    }
}
