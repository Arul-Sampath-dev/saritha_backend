package com.saritha.fashions.payload.invoice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceAssignmentRequestDTO {
    private String invoiceId;
    private String tailorId;
    private String itemUniqueId;
    private String invoiceDbId;
}
