package com.saritha.fashions.modules.employeemanagement.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.core.activity.ActivityEntry;
import com.saritha.fashions.modules.core.activity.ActivityLogService;
import com.saritha.fashions.modules.core.activity.ActivityType;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.modules.employeemanagement.exception.EmployeeEmailTakenException;
import com.saritha.fashions.modules.employeemanagement.model.ManagedEmployee;
import com.saritha.fashions.modules.employeemanagement.payload.EmployeeResponse;
import com.saritha.fashions.modules.employeemanagement.payload.UpdateEmployeeRequest;
import com.saritha.fashions.modules.employeemanagement.repository.EmployeeManagementRepository;
import com.saritha.fashions.modules.employeemanagement.repository.EmployeeManagementRepositoryCustom.ShopInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Employee assignment, update and deletion.
 *
 * Rules:
 *  - A user works in exactly ONE shop (every module finds "the user's shop" through
 *    shops.employeeIds, so two shops would make them act in the wrong one).
 *  - A SHOP_OWNER manages only their own shop and only staff accounts (TAILOR_MASTER, TAILOR,
 *    SHOP_ASSISTANT, ACCOUNTANT). They can assign a staff user only when that user works in no
 *    shop, so they cannot pull employees away from another shop.
 *  - The platform OWNER manages every shop and can MOVE a user from one shop to another.
 *  - Shop owner and platform owner accounts cannot be removed or deleted here.
 *  - When someone leaves a shop (removed, moved, deleted), their in-progress items go back one
 *    step with the same rules as the item workflow (tailor → CUTTING, shop assistant →
 *    STITCHING), using atomic per-item updates.
 *  - Removing / deleting / changing the email of an employee revokes their tokens.
 */
@Service
public class EmployeeManagementWriteServiceImpl implements EmployeeManagementWriteService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeManagementWriteServiceImpl.class);

    static final String HISTORY_REMOVED = "UNASSIGNED_DUE_TO_EMPLOYEE_REMOVAL";
    static final String HISTORY_DELETED = "UNASSIGNED_DUE_TO_EMPLOYEE_DELETION";

    private final EmployeeAccess access;
    private final EmployeeManagementRepository repository;
    private final ActivityLogService activityLog;

    public EmployeeManagementWriteServiceImpl(EmployeeAccess access,
                                              EmployeeManagementRepository repository,
                                              ActivityLogService activityLog) {
        this.access = access;
        this.repository = repository;
        this.activityLog = activityLog;
    }

    @Override
    public EmployeeResponse assign(String userId, String shopId) {
        EmployeeActor actor = access.requireManager();
        ShopInfo shop = access.requireManagedShop(actor, shopId.trim());

        ManagedEmployee employee = repository.findById(userId).orElseThrow(() -> new APIException("No user found"));
        if (EmployeeAccess.hasRole(employee, AppRole.OWNER)) {
            throw new APIException("The platform owner cannot be assigned to a shop");
        }
        if (!actor.isPlatformOwner() && !EmployeeAccess.isStaffOnly(employee)) {
            throw new AccessDeniedException("Shop owners can only assign staff (tailor master, tailor, shop assistant, accountant)");
        }

        Optional<String> currentShop = repository.findShopIdOfUser(userId);
        if (currentShop.isPresent() && currentShop.get().equals(shop.shopId())) {
            return EmployeeAccess.toResponse(employee, shop.shopId(), shop.shopName()); // already there
        }
        if (currentShop.isPresent()) {
            if (!actor.isPlatformOwner()) {
                throw new APIException("This user already works in another shop. Only the platform owner can move them.");
            }
            // Platform owner moves the user: release their items in the old shop, then unlink it.
            repository.unassignFromActiveItems(currentShop.get(), userId, HISTORY_REMOVED);
            repository.removeFromShop(userId, currentShop.get());
            log.info("User {} moved from shop {} to {} by {}", userId, currentShop.get(), shop.shopId(), actor.userId());
        }

        repository.addToShop(userId, shop.shopId());
        log.info("User {} assigned to shop {} by {}", userId, shop.shopId(), actor.userId());
        return EmployeeAccess.toResponse(employee, shop.shopId(), shop.shopName());
    }

    @Override
    public EmployeeResponse removeAssignment(String shopId, String userId) {
        EmployeeActor actor = access.requireManager();
        ShopInfo shop = access.requireManagedShop(actor, shopId);
        ManagedEmployee employee = access.requireShopEmployee(shop, userId);

        if (EmployeeAccess.hasRole(employee, AppRole.SHOP_OWNER)) {
            throw new APIException("The shop owner cannot be removed from their shop");
        }

        long invoices = repository.unassignFromActiveItems(shop.shopId(), userId, HISTORY_REMOVED);
        repository.removeFromShop(userId, shop.shopId());
        repository.revokeTokens(userId);

        log.info("User {} removed from shop {} by {} ({} invoice(s) had items released)",
                userId, shop.shopId(), actor.userId(), invoices);
        return EmployeeAccess.toResponse(employee, null, null);
    }

    @Override
    public EmployeeResponse update(String shopId, String userId, UpdateEmployeeRequest request) {
        EmployeeActor actor = access.requireManager();
        ShopInfo shop = access.requireManagedShop(actor, shopId);
        ManagedEmployee employee = access.requireShopEmployee(shop, userId);

        boolean editingSelf = userId.equals(actor.userId());
        if (!actor.isPlatformOwner() && !editingSelf && !EmployeeAccess.isStaffOnly(employee)) {
            throw new AccessDeniedException("Shop owners can only edit staff accounts and their own account");
        }

        Update update = new Update();
        if (request.getName() != null) {
            update.set("name", request.getName().trim());
        }
        if (request.getPhone() != null) {
            update.set("phone", request.getPhone().isBlank() ? null : request.getPhone().trim());
        }
        if (request.getSalary() != null) {
            update.set("salary", request.getSalary());
        }
        if (request.getAddress() != null) {
            update.set("address", request.getAddress().isBlank() ? null : request.getAddress().trim());
        }

        boolean emailChanged = false;
        if (request.getEmail() != null) {
            String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!email.equals(employee.getEmail())) {
                if (repository.findByEmail(email).isPresent()) {
                    throw new EmployeeEmailTakenException(email);
                }
                update.set("email", email);
                // The JWT subject is the email: old tokens must stop working.
                update.inc("tokenVersion", 1);
                emailChanged = true;
            }
        }

        if (!update.getUpdateObject().isEmpty()) {
            try {
                if (!repository.updateUser(userId, update)) {
                    throw new APIException("No user found");
                }
            } catch (DuplicateKeyException exception) {
                throw new EmployeeEmailTakenException(request.getEmail().trim().toLowerCase(Locale.ROOT));
            }
        }

        log.info("Employee {} of shop {} updated by {}{}", userId, shop.shopId(), actor.userId(),
                emailChanged ? " (email changed, tokens revoked)" : "");
        ManagedEmployee updated = repository.findById(userId).orElse(employee);
        return EmployeeAccess.toResponse(updated, shop.shopId(), shop.shopName());
    }

    @Override
    public void delete(String shopId, String userId) {
        EmployeeActor actor = access.requireManager();
        ShopInfo shop = access.requireManagedShop(actor, shopId);
        ManagedEmployee employee = access.requireShopEmployee(shop, userId);

        if (userId.equals(actor.userId())) {
            throw new APIException("You cannot delete your own account");
        }
        if (EmployeeAccess.hasRole(employee, AppRole.SHOP_OWNER) || EmployeeAccess.hasRole(employee, AppRole.OWNER)) {
            throw new APIException("Shop owner and platform owner accounts cannot be deleted here");
        }

        repository.unassignFromActiveItems(shop.shopId(), userId, HISTORY_DELETED);
        repository.removeFromAllShops(userId);
        repository.deleteUser(userId); // the account is gone, so every token stops working

        try {
            activityLog.record(ActivityEntry.of(shop.shopId(), null, null, actor.userId(), actor.name(),
                    ActivityType.USER_DELETED, "Deleted employee " + employee.getName() + " (" + userId + ")"));
        } catch (Exception exception) {
            log.error("Employee {} deleted, but the activity could not be recorded: {}", userId, exception.getMessage());
        }
        log.info("Employee {} ({}) of shop {} deleted by {}", userId, employee.getEmail(), shop.shopId(), actor.userId());
    }
}
