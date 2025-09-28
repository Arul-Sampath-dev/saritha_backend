package com.saritha.fashions.payload.invoice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.saritha.fashions.payload.CustomerDTO;
import com.saritha.fashions.payload.items.Items;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
//    private CustomerDTO customer;
    private String receiptId;

    private double taxableAmount;
    private double cgstRate;
    private double sgstRate;
    private double igstRate;
    private double cgstAmount;
    private double sgstAmount;
    private double igstAmount;
    private double totalAmountWithGST;
    private String gstin;
    private String sacCode;
    private String hsnCode;
    private List<String> creditNoteIds;
}

