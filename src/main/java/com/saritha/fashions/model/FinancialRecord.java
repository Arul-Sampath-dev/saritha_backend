package com.saritha.fashions.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "financial_records")
public class FinancialRecord {
    @Id
    private String id;
    private String shopId; // Link financial record to a specific shop
    private LocalDate date; // Date of record
    private double totalIncome; // Sum of all invoices for the day
    private double totalExpenses; // Sum of all expenses for the day
    private double pettyCash; // Cash-in-hand for small expenses
    private double netProfit; // Calculated as totalIncome - totalExpenses
}
