package com.saritha.fashions.payload.activity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCountDTO {
    private String day; // yyyy-MM-dd
    private int count;
}