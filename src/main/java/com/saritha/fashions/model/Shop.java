package com.saritha.fashions.model;

import lombok.*;
import org.springframework.data.annotation.Id;
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
    private String address;
    private String contactNumber;
    private String gstNumber; // Optional, if applicable
    private String shopType;  // e.g., "Tailoring", "Boutique"

    private List<Expense> allExpense;
}
