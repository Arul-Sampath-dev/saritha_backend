package com.saritha.fashions.payload.shop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AllShopDTO {
    private String id;
    private String shopName;
    private double pettyCash;

    private String ownerName;
    private String shopAddress;
    private String shopContactNumber;

    private String gstNumber;
    private String shopType;

    private double totalExpense;
    private int totalReceipt;
    private int totalInvoice;
    private int totalEmployee;
    private int totalExpenseEntry;

    private int totalPendingOrders;
    private int totalCompletedOrders;

}
