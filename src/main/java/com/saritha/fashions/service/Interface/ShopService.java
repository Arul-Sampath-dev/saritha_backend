package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Shop;
import com.saritha.fashions.payload.ShopRegisterDTO;

import java.util.List;

public interface ShopService {
    Shop createShop(ShopRegisterDTO shop);
    List<Shop> getAllShops();
    Shop getShopById(String id);
    Shop updateShop(String id, Shop updatedShop);
    void deleteShop(String id);
}