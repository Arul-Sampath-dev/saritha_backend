package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.expense.ExpenseDTO;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.payload.shop.AllShopDTO;
import com.saritha.fashions.payload.shop.ShopRegisterDTO;
import com.saritha.fashions.payload.shop.ShopUpdateDTO;

import java.util.Date;
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

    PaginatedResponse<ExpenseDTO> searchAllExpensesByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId, Date startDate, Date endDate, String term);

    PaginatedResponse<ExpenseDTO> searchAllExpensesByShopIdWithPaymentMethod(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId, Date startDate, Date endDate, String term);

    List<AllShopDTO> findAllShopSummaries();
}