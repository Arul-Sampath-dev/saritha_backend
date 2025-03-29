package com.saritha.fashions.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Document(collection = "expenses")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Expense {
    @Id
    private String id;
    private String shopId;  // Reference to the shop
    private String description;  // Purpose of the expense
    private double amount;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date expenseDate;

    private String rentBill;
    private String electricityBill;
    private String materialExpensesBill;

    private String paymentMethod; // Example: Cash, UPI, Bank Transfer

}

