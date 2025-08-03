package com.saritha.fashions.model;

import com.saritha.fashions.payload.shop.PettyCashDTO;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "shops")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@CompoundIndex(name = "shop_name_idx", def = "{'shopName': 1}")
public class Shop {
    @Id
    private String id;
    private String shopName;
    private double pettyCash;

    private String ownerName;
    private String shopAddress;
    private String shopContactNumber;

    private String gstNumber;
    private String shopType;

    private double totalExpense;

    private List<PettyCashDTO> allPettyCashDetails = new ArrayList<>();

    @DBRef
    private User ownerDetails;

    @DBRef
    private List<Expense> allExpense = new ArrayList<>();

    @DBRef
    private List<User> employees = new ArrayList<>();

    @DBRef
    private List<Receipt> allReceipts = new ArrayList<>();

    @DBRef
    private List<Invoice> allInvoice = new ArrayList<>();

}
