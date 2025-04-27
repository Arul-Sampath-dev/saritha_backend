package com.saritha.fashions.payload.invoice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceResponseDTO {
    private String invoiceId;
    private String customerName;
    private Double totalAmount;
    private String assignedTailorId;
    private String assignmentStatus;
    private List<AssignmentHistoryDTO> assignmentHistory;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignmentHistoryDTO {
        private String tailorId;
        private String action;
        private String timestamp;
    }
}
