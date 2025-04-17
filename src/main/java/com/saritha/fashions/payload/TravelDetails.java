package com.saritha.fashions.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TravelDetails {

    private String date;
    private String destination;
    private String source;
    private double amount;
    private String description;

}
