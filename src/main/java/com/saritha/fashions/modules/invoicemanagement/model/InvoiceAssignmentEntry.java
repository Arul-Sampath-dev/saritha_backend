package com.saritha.fashions.modules.invoicemanagement.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Tailor assignment history entry (same stored shape as Invoice.AssignmentHistory /
 * Items.AssignmentHistory). Not written at creation; tailor assignment fills it later.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceAssignmentEntry {
    private String tailorId;
    private String action;
    private Date timestamp;
}
