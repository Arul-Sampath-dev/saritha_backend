package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Shop;
import java.util.List;

public interface ShopService {
    Shop createShop(Shop shop);
    List<Shop> getAllShops();
    Shop getShopById(String id);
    Shop updateShop(String id, Shop updatedShop);
    void deleteShop(String id);
}