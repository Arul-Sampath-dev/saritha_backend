package com.saritha.fashions.payload.expense;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExpenseDTO {

    private String id;
    private String shopId;
    private String expenseType;
    private double expenseAmount;
    private String notes;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date expenseDate;

    private String paymentMethod;

    private List<RefreshmentDetails> refreshmentDetails = new ArrayList<>();
    private List<RefreshmentDetails> miscellaneousDetails = new ArrayList<>();
    private List<TravelDetails> travelDetails = new ArrayList<>();
}
