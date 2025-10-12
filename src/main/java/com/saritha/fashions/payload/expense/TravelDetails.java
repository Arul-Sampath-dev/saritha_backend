package com.saritha.fashions.payload.expense;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TravelDetails {

//    Todo - Convert String to date
    private String date;
    private String destination;
    private String source;
    private double amount;
    private String description;

}
