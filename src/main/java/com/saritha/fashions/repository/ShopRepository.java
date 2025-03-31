package com.saritha.fashions.repository;

import com.saritha.fashions.model.Shop;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopRepository extends MongoRepository<Shop, String> {
    Shop findByShopName(String assignedShops);
}
