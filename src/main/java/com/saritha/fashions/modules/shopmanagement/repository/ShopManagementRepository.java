package com.saritha.fashions.modules.shopmanagement.repository;

import com.saritha.fashions.modules.shopmanagement.model.ManagedShop;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Mongo repository for the ShopManagement module ("shops" collection).
 *
 * Named differently from com.saritha.fashions.repository.ShopRepository so the two
 * Spring beans do not clash. Basic CRUD comes from MongoRepository; atomic updates
 * (e.g. addPettyCash) come from ShopManagementRepositoryCustom.
 */
@Repository
public interface ShopManagementRepository
        extends MongoRepository<ManagedShop, String>, ShopManagementRepositoryCustom {

    ManagedShop findByShopName(String shopName);
}
