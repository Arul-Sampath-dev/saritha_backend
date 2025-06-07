package com.saritha.fashions.model;

import com.saritha.fashions.payload.items.Items;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "receipt")
public class Receipt {

    @Id
    private String id;
    private String receiptNo;


    private String invoiceId;

    @DBRef
    private Customer customer;
    private String shopId;

    private List<Items> items;

    private String transactionId;
    private double totalAmount;
    private double advancePaid;
    private double balanceDue;
    private String paymentMethod;
    private String paymentStatus;
    private String orderStatus;

    private String expectedDeliveryDate;

    private String receiptNotes;

    private String receiptUrl;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date receiptDate;


    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date orderDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date paymentCompletedDate;

}