package com.saritha.fashions.modules.employeemanagement.repository;

import com.saritha.fashions.modules.employeemanagement.model.ManagedEmployee;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Raw-document operations on "users", "shops" and "invoice" (no dependency on other
 * modules' models). Implemented with MongoTemplate; every query reads only the fields it needs.
 */
public interface EmployeeManagementRepositoryCustom {

    /** The shop the user works in (shops.employeeIds), if any. */
    Optional<String> findShopIdOfUser(String userId);

    /** Shop id, name and employee ids. */
    Optional<ShopInfo> findShop(String shopId);

    /** shopId → shop name. */
    Map<String, String> findShopNames(Collection<String> shopIds);

    /** The given users, sorted by name. */
    List<ManagedEmployee> findEmployees(Collection<String> userIds);

    /** Users with a staff role who currently work in no shop, sorted by name. */
    List<ManagedEmployee> findUnassignedStaff(Collection<String> staffRoleNames);

    /** Links the user to the shop: users.assignedShops = [shop]; shop.employeeIds / employees += user. */
    void addToShop(String userId, String shopId);

    /** Removes the links between the user and the shop (both directions). */
    void removeFromShop(String userId, String shopId);

    /** Removes the user from every shop's employeeIds / employees. */
    void removeFromAllShops(String userId);

    /** Applies the update to the user; false if the user does not exist. */
    boolean updateUser(String userId, Update update);

    /** Increments tokenVersion: every existing JWT of the user stops working. */
    void revokeTokens(String userId);

    void deleteUser(String userId);

    /**
     * Takes the user off the shop's in-progress items, with the same rules as the item workflow,
     * using atomic per-item updates (no invoice re-save):
     *  - tailor of an item in TAILOR_ASSIGNED / STITCHING  → tailor removed, item back to CUTTING
     *  - shop assistant of an item in SHOP_ASSISTANT_ASSIGNED / HEMMING / IRONING
     *                                                     → assistant removed, item back to STITCHING
     * Finished stages keep their record of who did the work. Delivered invoices are not touched.
     *
     * @return number of invoices changed
     */
    long unassignFromActiveItems(String shopId, String userId, String historyAction);

    record ShopInfo(String shopId, String shopName, List<String> employeeIds) {
    }
}
