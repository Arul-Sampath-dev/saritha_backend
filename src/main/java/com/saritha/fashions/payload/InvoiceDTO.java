package com.saritha.fashions.payload;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class InvoiceDTO {
    private String id;
    private String invoiceId;
    private String shopId;
    private List<Items> items;
    private double totalAmount;
    private double advancePaid;
    private double balanceDue;
    private String paymentMethod;
    private String paymentStatus;
    private String orderStatus;
    private String expectedDeliveryDate;
    private Date paymentCompletedDate;
    private Date orderDate;
    private Date invoiceDate;
    private String notes;

    private String customerName;
    private String receiptId;
}

