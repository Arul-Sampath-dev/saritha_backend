package com.saritha.fashions.modules.invoice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /** True when the shop owner marked the item completed directly (POST .../complete). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean markedByShopOwner;
    /** The status the item was in when it was marked completed (the steps after it were skipped). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String skippedFrom;
    /** Optional note given when it was marked completed. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String note;
}
