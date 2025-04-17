package com.saritha.fashions.payload;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ReceiptDTO {
    private String id;
    private String receiptNo;
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
    private Date receiptDate;
    private Date orderDate;
    private String receiptNotes;
    private String receiptUrl;

    private String customerName;
    private String invoiceId;
}

