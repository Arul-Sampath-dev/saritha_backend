package com.saritha.fashions.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@CompoundIndex(name = "employees_idx", def = "{'employees.$id': 1}")
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

    @DBRef(lazy = true)
    @JsonIgnore
    private List<User> employees = new ArrayList<>();
    private List<String> employeeIds = new ArrayList<>();

    @DBRef
    private List<Receipt> allReceipts = new ArrayList<>();

    private List<Invoice> allInvoice = new ArrayList<>();

}
