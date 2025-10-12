package com.saritha.fashions.payload.invoice;

import com.saritha.fashions.payload.items.Items;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class InvoiceAggregationResult {
    private List<CountResult> totalCount;
    private List<InvoiceWithCustomer> data;

    @Data
    public static class CountResult {
        private long totalElements;
    }

    @Data
    public static class InvoiceWithCustomer {
        // Invoice fields
        private String id;
        private String invoiceId;
        private Date invoiceDate;
        private double totalAmount;
        private double totalAmountWithGST;
        private double advancePaid;
        private double balanceDue;
        private String paymentMethod;
        private String paymentStatus;
        private String orderStatus;
        private String shopId;
        private String dueDate;

        // Customer fields
        private String customerId;
        private String customerName;
        private String customerNumber;

        private String receiptId;
        private String expectedDeliveryDate;
        private Date paymentCompletedDate;
        private Date orderDate;

        // Items data
        private List<Items> items;
    }
}