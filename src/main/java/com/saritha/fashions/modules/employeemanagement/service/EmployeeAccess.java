package com.saritha.fashions.modules.employeemanagement.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.employeemanagement.model.EmployeeRole;
import com.saritha.fashions.modules.employeemanagement.model.ManagedEmployee;
import com.saritha.fashions.modules.employeemanagement.payload.EmployeeResponse;
import com.saritha.fashions.modules.employeemanagement.repository.EmployeeManagementRepository;
import com.saritha.fashions.modules.employeemanagement.repository.EmployeeManagementRepositoryCustom.ShopInfo;
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
 * Shared access checks and mapping for the employee services.
 *
 * Access: the platform OWNER manages every shop; a SHOP_OWNER manages only their own shop
 * (the shop whose employeeIds contains them — the same rule every other module uses).
 */
@Component
public class EmployeeAccess {

    private final AuthUtil authUtil;
    private final EmployeeManagementRepository repository;

    public EmployeeAccess(AuthUtil authUtil, EmployeeManagementRepository repository) {
        this.authUtil = authUtil;
        this.repository = repository;
    }

    /** Platform owner or shop owner (with their shop). */
    public EmployeeActor requireManager() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required");
        }
        User user = authUtil.loggedInUser();
        Set<AppRole> roles = user.getRoles() == null ? Set.of() : user.getRoles().stream()
                .map(role -> role.getRoleName()).filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AppRole.class)));

        if (roles.contains(AppRole.OWNER)) {
            return new EmployeeActor(user.getUserId(), user.getName(), roles, null);
        }
        if (roles.contains(AppRole.SHOP_OWNER)) {
            String shopId = repository.findShopIdOfUser(user.getUserId())
                    .orElseThrow(() -> new APIException("User is not assigned to any shop"));
            return new EmployeeActor(user.getUserId(), user.getName(), roles, shopId);
        }
        throw new AccessDeniedException("Only the platform owner or a shop owner can manage employees");
    }

    /** The shop, if the actor may manage it (platform owner: any shop; shop owner: own shop). */
    public ShopInfo requireManagedShop(EmployeeActor actor, String shopId) {
        if (!actor.isPlatformOwner() && !shopId.equals(actor.shopId())) {
            throw new AccessDeniedException("You can only manage employees of your own shop");
        }
        return repository.findShop(shopId).orElseThrow(() -> new APIException("Shop Not Found"));
    }

    /** The user, if they are an employee of the shop. */
    public ManagedEmployee requireShopEmployee(ShopInfo shop, String userId) {
        if (!shop.employeeIds().contains(userId)) {
            throw new APIException("User is not an employee of this shop");
        }
        return repository.findById(userId).orElseThrow(() -> new APIException("No user found"));
    }

    public static boolean hasRole(ManagedEmployee employee, AppRole role) {
        return employee.getRoles() != null && employee.getRoles().stream()
                .filter(Objects::nonNull).anyMatch(r -> r.getRoleName() == role);
    }

    /** True if every role of the user is a staff role (no SHOP_OWNER / OWNER). */
    public static boolean isStaffOnly(ManagedEmployee employee) {
        return employee.getRoles() != null && !employee.getRoles().isEmpty() && employee.getRoles().stream()
                .filter(Objects::nonNull).allMatch(r -> EmployeeRole.STAFF_ROLES.contains(r.getRoleName()));
    }

    public static EmployeeResponse toResponse(ManagedEmployee employee, String shopId, String shopName) {
        return EmployeeResponse.builder()
                .userId(employee.getUserId())
                .name(employee.getName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .salary(employee.getSalary())
                .address(employee.getAddress())
                .roles(employee.getRoles() == null ? java.util.List.of() : employee.getRoles().stream()
                        .map(EmployeeRole::getRoleName).filter(Objects::nonNull).map(Enum::name).toList())
                .shopId(shopId)
                .shopName(shopName)
                .accountCreationDate(employee.getAccountCreationDate())
                .lastLoginDate(employee.getLastLoginDate())
                .build();
    }
}
