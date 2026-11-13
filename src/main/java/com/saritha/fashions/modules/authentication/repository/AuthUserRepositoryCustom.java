package com.saritha.fashions.modules.authentication.repository;

import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.modules.authentication.model.AuthRole;
import com.saritha.fashions.modules.authentication.model.AuthUser;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Custom user / shop / role operations, implemented with MongoTemplate.
 */
public interface AuthUserRepositoryCustom {

    /** Shop of the user: first of assignedShops, else the shop whose employeeIds contains the user. */
    Optional<ShopInfo> findShopOfUser(String userId);

    boolean shopExists(String shopId);

    /** The role documents ({_id, roleName}) for the given role names, from the "roles" collection. */
    List<AuthRole> findRoles(Collection<AppRole> roleNames);

    /**
     * Inserts a new user, linked to the shop (assignedShops DBRef) if shopId is not null.
     *
     * @throws org.springframework.dao.DuplicateKeyException if the email is already taken
     *         (unique index on users.email)
     */
    AuthUser insertUser(AuthUser user, String shopId);

    /** Atomically adds the user to the shop's employeeIds and employees ($addToSet, no shop re-save). */
    void addEmployeeToShop(String shopId, String userId);

    /** Sets a new password hash and increments tokenVersion (revokes all existing tokens). */
    boolean updatePasswordAndRevokeTokens(String userId, String passwordHash);

    /** Increments tokenVersion (revokes all existing tokens). */
    boolean revokeTokens(String userId);

    /** Best-effort: lastLoginDate / lastOnlineDate = now. */
    void recordLogin(String userId);

    record ShopInfo(String shopId, String shopName) {
    }
}
