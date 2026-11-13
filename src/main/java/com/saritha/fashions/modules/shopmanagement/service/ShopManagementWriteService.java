package com.saritha.fashions.modules.shopmanagement.service;

import com.saritha.fashions.modules.shopmanagement.model.ManagedShop;
import com.saritha.fashions.modules.shopmanagement.payload.AddPettyCashRequest;
import com.saritha.fashions.modules.shopmanagement.payload.CreateShopRequest;
import com.saritha.fashions.modules.shopmanagement.payload.PettyCashTopUpResponse;
import com.saritha.fashions.modules.shopmanagement.payload.UpdateShopRequest;

/**
 * Write side of shop management: create, update, delete and petty cash top-ups.
 */
public interface ShopManagementWriteService {

    ManagedShop create(CreateShopRequest shopRequest);

    ManagedShop update(String shopId, UpdateShopRequest shopUpdate);

    void delete(String shopId);

    /**
     * Adds petty cash to a shop and records who added it and when.
     * Allowed for a platform OWNER or a user assigned to that shop.
     */
    PettyCashTopUpResponse addPettyCash(String shopId, AddPettyCashRequest request);
}
