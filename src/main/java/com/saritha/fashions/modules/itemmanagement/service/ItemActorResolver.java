package com.saritha.fashions.modules.itemmanagement.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.itemmanagement.repository.ItemWorkflowRepository;
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
 * Resolves the logged-in user into an ItemActor: their workflow roles and their shop.
 * Only SHOP_OWNER, TAILOR_MASTER, TAILOR and SHOP_ASSISTANT take part in the workflow.
 */
@Component
public class ItemActorResolver {

    static final Set<AppRole> WORKFLOW_ROLES =
            EnumSet.of(AppRole.SHOP_OWNER, AppRole.TAILOR_MASTER, AppRole.TAILOR, AppRole.SHOP_ASSISTANT);

    private final AuthUtil authUtil;
    private final ItemWorkflowRepository repository;

    public ItemActorResolver(AuthUtil authUtil, ItemWorkflowRepository repository) {
        this.authUtil = authUtil;
        this.repository = repository;
    }

    /**
     * @throws AccessDeniedException if not logged in or not a workflow role (403)
     * @throws APIException          if the user is not assigned to a shop (400)
     */
    public ItemActor resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required");
        }

        User user = authUtil.loggedInUser();
        Set<AppRole> roles = user.getRoles() == null ? Set.of() : user.getRoles().stream()
                .map(role -> role.getRoleName())
                .filter(Objects::nonNull)
                .filter(WORKFLOW_ROLES::contains)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AppRole.class)));
        if (roles.isEmpty()) {
            throw new AccessDeniedException("Only shop owners, tailor masters, tailors and shop assistants can work on items");
        }

        String shopId = repository.findShopIdByEmployeeId(user.getUserId())
                .orElseThrow(() -> new APIException("User is not assigned to any shop"));

        return new ItemActor(user.getUserId(), user.getName(), roles, shopId);
    }
}
