package com.saritha.fashions.modules.shop.repository;

import com.saritha.fashions.modules.shop.model.ManagedShop;
import com.saritha.fashions.modules.shop.model.PettyCashEntry;
import org.bson.Document;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Custom repository operations that need MongoTemplate (atomic updates).
 * Implemented by ShopRepositoryCustomImpl and exposed through
 * ShopRepository.
 */
public interface ShopRepositoryCustom {

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
    default Optional<PettyCashPage> findPettyCashHistory(String shopId, Date from, Date before, Double amount, int page, int size) {
        return findPettyCashHistory(shopId, from, before, amount, null, page, size);
    }

    /** @param sort newest (default), oldest, highest or lowest (amount) */
    Optional<PettyCashPage> findPettyCashHistory(String shopId, Date from, Date before, Double amount, String sort,
                                                 int page, int size);

    /** userId → name. */
    Map<String, String> findUserNames(List<String> userIds);

    // ------------------------------------------------------------------ shop owner update

    /** The raw shop document (name, type, employeeIds), if it exists. */
    Optional<org.bson.Document> findShopDocument(String shopId);

    /** Raw user documents (with embedded roles) for the given user ids. */
    List<org.bson.Document> findUserDocuments(List<String> userIds);

    /** True if another account (not userId) already uses this email (emails are stored lower-case). */
    boolean emailUsedByOtherUser(String email, String userId);

    /** Stores a backup of a user's previous details in "user_backup_history" (legacy UserBackupHistory shape). */
    void insertUserBackup(org.bson.Document backup);

    /**
     * Applies the update to the user.
     *
     * @return false if the user no longer exists
     * @throws org.springframework.dao.DuplicateKeyException if the new email was taken meanwhile (unique index)
     */
    boolean updateUser(String userId, org.springframework.data.mongodb.core.query.Update update);

    /** Mirrors the owner's name / phone on the shop (null = unchanged) and keeps the owner in employeeIds. */
    void updateShopOwnerInfo(String shopId, String ownerUserId, String ownerName, String contactNumber);

    record PettyCashPage(List<PettyCashEntry> entries, long total) {
    }
}
