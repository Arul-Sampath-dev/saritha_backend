package com.saritha.fashions.modules.shop.repository;

import com.saritha.fashions.modules.shop.model.ManagedShop;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Mongo repository for the Shop module ("shops" collection).
 *
 * Named differently from com.saritha.fashions.repository.ShopRepository so the two
 * Spring beans do not clash. Basic CRUD comes from MongoRepository; atomic updates
 * (e.g. addPettyCash) come from ShopRepositoryCustom.
 */
@Repository
public interface ShopRepository
        extends MongoRepository<ManagedShop, String>, ShopRepositoryCustom {

    ManagedShop findByShopName(String shopName);
}
