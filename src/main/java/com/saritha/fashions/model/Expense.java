package com.saritha.fashions.model;

import com.saritha.fashions.payload.expense.RefreshmentDetails;
import com.saritha.fashions.payload.expense.TravelDetails;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Document(collection = "expenses")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Expense {

    @Id
    private String id;
    private String shopId;
    private String expenseType;
    private double expenseAmount;

    private String userId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date expenseDate;

    private String paymentMethod;

    private List<RefreshmentDetails> refreshmentDetails = new ArrayList<>();
    private List<RefreshmentDetails> miscellaneousDetails = new ArrayList<>();
    private List<TravelDetails> travelDetails = new ArrayList<>();

}

