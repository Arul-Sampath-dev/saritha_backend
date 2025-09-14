package com.saritha.fashions.payload.activity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeActivitySummaryDTO {
    private String employeeId;
    private String employeeName;
    private List<DailyCountDTO> dailyCounts;
}