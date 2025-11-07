package com.saritha.fashions.repository;

import com.saritha.fashions.model.UserBackupHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface UserBackupHistoryRepository extends MongoRepository<UserBackupHistory, String> {

    // Find by original user ID
    List<UserBackupHistory> findByOriginalUserIdOrderByBackupDateDesc(String originalUserId);

    // Find by shop ID (the shop that was being updated)
    List<UserBackupHistory> findByShopIdOrderByBackupDateDesc(String shopId);

    // Find by backup reason
    List<UserBackupHistory> findByBackupReasonOrderByBackupDateDesc(String backupReason);

    // Find backups where user was assigned to a specific shop
    @Query("{ 'assignedShopIds': ?0 }")
    List<UserBackupHistory> findByAssignedShopIdsContaining(String shopId);

    // Find backups for a user within a specific shop context
    @Query("{ 'originalUserId': ?0, 'assignedShopIds': ?1 }")
    List<UserBackupHistory> findByUserIdAndAssignedShop(String userId, String shopId);

    // Find all backups for users who were assigned to a shop (useful for shop history)
    @Query("{ 'assignedShopIds': { $in: [?0] } }")
    List<UserBackupHistory> findAllBackupsForShop(String shopId);

    // Find the latest backup for a user in a specific shop
    @Query(value = "{ 'originalUserId': ?0, 'shopId': ?1 }", sort = "{ 'backupDate': -1 }")
    List<UserBackupHistory> findLatestByUserIdAndShopId(String userId, String shopId);
}