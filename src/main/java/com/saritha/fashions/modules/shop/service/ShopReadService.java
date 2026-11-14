package com.saritha.fashions.modules.shop.service;

import com.saritha.fashions.modules.shop.model.ManagedShop;
import com.saritha.fashions.modules.shop.payload.PettyCashHistoryResponse;
import com.saritha.fashions.modules.shop.payload.ShopNameResponse;
import com.saritha.fashions.modules.shop.payload.ShopSummaryResponse;
import com.saritha.fashions.payload.PaginatedResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Read side of shop management: queries only, never changes data.
 */
public interface ShopReadService {

    ManagedShop getById(String shopId);

    List<ManagedShop> getAll();

    /**
     * Platform owner view: basic details (name, owner, address, contact, GST, type)
     * plus pettyCash and totalExpense of every shop, sorted by shop name.
     */
    List<ShopSummaryResponse> getAllShopSummaries();

    /** Every shop's id and name, sorted by name. */
    List<ShopNameResponse> getShopNames();

    /**
     * A shop's petty cash top-ups, newest first. Platform owner: any shop; shop owner: own shop.
     *
     * @param from   optional first day (inclusive, IST)
     * @param to     optional last day (inclusive, IST)
     * @param amount optional exact amount
     */
    PaginatedResponse<PettyCashHistoryResponse> getPettyCashHistory(String shopId, LocalDate from, LocalDate to,
                                                                    Double amount, int pageNumber, int pageSize);
}
