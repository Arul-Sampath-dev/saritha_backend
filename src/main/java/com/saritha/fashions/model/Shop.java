package com.saritha.fashions.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

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

    private String ownerName;
    private String shopAddress;
    private String shopContactNumber;

    private String gstNumber;
    private String shopType;

    @DBRef
    private User ownerDetails;

    @DBRef
    private List<Expense> allExpense;

    @DBRef
    @JsonIgnore
    private List<User> employees;
}
