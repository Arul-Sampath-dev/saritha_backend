package com.saritha.fashions.payload;

import com.saritha.fashions.model.Invoice;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopUpdateDTO {
    private String shopName;
    private String ownerName;
    private String shopAddress;
    private String shopContactNumber;
    private String gstNumber;
    private String shopType;
    private List<Invoice> allInvoice;
}
