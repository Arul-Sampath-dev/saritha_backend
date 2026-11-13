package com.saritha.fashions.modules.shopmanagement.repository;

import com.saritha.fashions.modules.shopmanagement.model.ManagedShop;
import com.saritha.fashions.modules.shopmanagement.model.PettyCashEntry;
import org.bson.Document;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Custom repository operations that need MongoTemplate (atomic updates).
 * Implemented by ShopManagementRepositoryCustomImpl and exposed through
 * ShopManagementRepository.
 */
public interface ShopManagementRepositoryCustom {

    /**
     * Atomically adds entry.amount to the shop's pettyCash balance and appends the
     * entry to allPettyCashDetails, in a single database operation.
     *
     * @return the shop after the update, with only id, shopName and pettyCash
     *         populated; or null if no shop exists with the given id
     */
    ManagedShop addPettyCash(String shopId, PettyCashEntry entry);

    /** Every shop's id and name, sorted by name (only those two fields are read). */
    List<Document> findShopNames();

    /** The id of the shop whose employeeIds contains the user, if any. */
    Optional<String> findShopIdOfEmployee(String userId);

    /**
     * One page of a shop's petty cash history (allPettyCashDetails), newest first.
     *
     * @param from   inclusive start (null = no limit)
     * @param before exclusive end   (null = no limit)
     * @param amount exact amount    (null = any)
     * @return the page of entries and the total count, or empty if the shop does not exist
     */
    Optional<PettyCashPage> findPettyCashHistory(String shopId, Date from, Date before, Double amount, int page, int size);

    /** userId → name. */
    Map<String, String> findUserNames(List<String> userIds);

    record PettyCashPage(List<PettyCashEntry> entries, long total) {
    }
}
