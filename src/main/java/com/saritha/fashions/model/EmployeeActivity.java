package com.saritha.fashions.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "employee_activities")
public class EmployeeActivity {
    @Id
    private String id;

    private String shopId;

    private String invoiceId;
    private String invoiceDbId;

    private String employeeId;
    private String employeeName;

    private ActivityType type;
    private Date timestamp;
    private String notes;

    private int itemQuantity;
    private double itemPrice;
    private double totalRevenueGenerated;

    private int reassignmentCount;

    private String assignedTo;
}
