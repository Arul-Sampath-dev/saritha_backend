package com.saritha.fashions.payload.expense;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshmentDetails {
//    private String description;
    private double amount;

    private double pettyCash;
    private double totalExpense;
    private String description;
}
