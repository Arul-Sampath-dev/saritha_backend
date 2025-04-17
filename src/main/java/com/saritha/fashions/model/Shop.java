package com.saritha.fashions.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.saritha.fashions.payload.PettyCashDTO;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "shops")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

    @DBRef
    private User ownerDetails;

    @DBRef
    private List<Expense> allExpense = new ArrayList<>();

    @DBRef
    @JsonIgnore
    private List<User> employees = new ArrayList<>();

    @DBRef
    @JsonIgnore
    private List<Receipt> allReceipts = new ArrayList<>();

    @DBRef
    @JsonIgnore
    private List<Invoice> allInvoice = new ArrayList<>();

    @DBRef
    @JsonIgnore
    private List<PettyCashDTO> allPettyCashDetails = new ArrayList<>();
}
