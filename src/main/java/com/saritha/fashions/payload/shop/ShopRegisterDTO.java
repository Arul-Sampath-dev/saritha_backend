package com.saritha.fashions.payload.shop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopRegisterDTO {
    private String shopName;

    private String ownerName;
    private String ownerEmailId;
    private String shopAddress;
    private String ownerNumber;

    private String gstNumber;
    private String password;

    private String role;
    private double salary;
}
