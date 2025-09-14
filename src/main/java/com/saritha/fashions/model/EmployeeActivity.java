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

    private String shopId;         // shop where activity happened
    private String invoiceId;      // invoice reference (invoice.invoiceId)
    private String invoiceDbId;    // invoice._id (optional)
    private String employeeId;     // userId who performed the action or assigned to
    private String employeeName;   // cached name for faster UI
    private ActivityType type;     // enum (ASSIGNED_TO_TAILOR, CUTTING_DONE, ASSIGNED_TO_TAILOR_MASTER, STITCHING_DONE, ASSIGNED_TO_ASSISTANT, IRONING_DONE, COMPLETED etc)
    private Date timestamp;        // when it happened (use DateTimeUtils.getNowInIST())
    private String notes;          // optional
}
