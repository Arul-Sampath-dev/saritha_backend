package com.saritha.fashions.modules.shop.service;

import com.saritha.fashions.modules.shop.model.ManagedShop;
import com.saritha.fashions.modules.shop.payload.AddPettyCashRequest;
import com.saritha.fashions.modules.shop.payload.CreateShopRequest;
import com.saritha.fashions.modules.shop.payload.PettyCashTopUpResponse;
import com.saritha.fashions.modules.shop.payload.UpdateShopRequest;

/**
 * Write side of shop management: create, update, delete and petty cash top-ups.
 */
public interface ShopWriteService {

    ManagedShop create(CreateShopRequest shopRequest);

    ManagedShop update(String shopId, UpdateShopRequest shopUpdate);

    void delete(String shopId);

    /**
     * Adds petty cash to a shop and records who added it and when.
     * Allowed for a platform OWNER or a user assigned to that shop.
     */
    PettyCashTopUpResponse addPettyCash(String shopId, AddPettyCashRequest request);

    /**
     * Updates the shop owner's account (platform OWNER only): name, phone, email, password, salary.
     * The previous details are backed up in user_backup_history.
     */
    com.saritha.fashions.modules.shop.payload.ShopOwnerUpdateResponse updateShopOwner(
            String shopId, com.saritha.fashions.modules.shop.payload.UpdateShopOwnerRequest request);
}
