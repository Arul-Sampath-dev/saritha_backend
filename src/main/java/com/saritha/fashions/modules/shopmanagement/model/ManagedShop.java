package com.saritha.fashions.modules.shopmanagement.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.saritha.fashions.model.Expense;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.model.User;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop entity used by the ShopManagement module.
 *
 * Copy of com.saritha.fashions.model.Shop. It maps to the same "shops" collection,
 * so both classes read and write the same documents. Keep the fields in sync with
 * the original Shop model.
 */
@Document(collection = "shops")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@CompoundIndex(name = "shop_name_idx", def = "{'shopName': 1}")
@CompoundIndex(name = "employees_idx", def = "{'employees.$id': 1}")
public class ManagedShop {
    @Id
    private String id;
    private String shopName;
    private double pettyCash;

    private String ownerName;
    private String shopAddress;
    private String shopState;
    private String shopContactNumber;

    private String gstNumber;
    private String shopType;

    private double totalExpense;

    @Builder.Default
    private List<PettyCashEntry> allPettyCashDetails = new ArrayList<>();

    @DBRef
    private User ownerDetails;

    @DBRef
    @Builder.Default
    private List<Expense> allExpense = new ArrayList<>();

    @DBRef(lazy = true)
    @JsonIgnore
    @Builder.Default
    private List<User> employees = new ArrayList<>();

    @Builder.Default
    private List<String> employeeIds = new ArrayList<>();

    @DBRef
    @Builder.Default
    private List<Receipt> allReceipts = new ArrayList<>();

    @Builder.Default
    private List<Invoice> allInvoice = new ArrayList<>();
}
