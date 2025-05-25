package com.saritha.fashions.payload.tailor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderStatusUpdate {
    private String invoiceId;
    private String newStatus;
    private String itemUniqueIds;
}
