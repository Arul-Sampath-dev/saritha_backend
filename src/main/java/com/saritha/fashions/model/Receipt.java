package com.saritha.fashions.model;

import com.saritha.fashions.payload.Items;
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

    @DBRef
    private Customer customer;
    private String shopId;

    private List<Items> items;  // List of ordered items

    private String transactionId;
    private double totalAmount;
    private double advancePaid;
    private double balanceDue;
    private String paymentMethod;
    private String paymentStatus;
    private String orderStatus;  // Pending | In Progress | Completed | Delivered

    private String expectedDeliveryDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date paymentCompletedDate;

    private String receiptNotes;

    private String receiptUrl;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date receiptDate;


    @DBRef
    private Invoice invoice;

}