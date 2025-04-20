package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.shop.ShopRegisterDTO;
import com.saritha.fashions.payload.shop.ShopUpdateDTO;

import java.util.List;

public interface ShopService {
    Shop createShop(ShopRegisterDTO shop);
    List<Shop> getAllShops();
    Shop getShopById(String id);
    Shop updateShop(String id, ShopUpdateDTO updatedShop);
    void deleteShop(String id);

    List<User> getAllEmployeeFromShops();

    Shop getPettyCashOfShop();

    Shop setPettyCashToShop(double amount, String shopId);
}