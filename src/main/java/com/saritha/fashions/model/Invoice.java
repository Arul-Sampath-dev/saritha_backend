package com.saritha.fashions.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.saritha.fashions.payload.Items;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "invoice")
@ToString(exclude = "customer")
public class Invoice {

    @Id
    private String id;
    @Indexed(unique = true)
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
    private String orderStatus;  // Pending | In Progress | Completed | Delivered

    private String expectedDeliveryDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date paymentCompletedDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date orderDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date invoiceDate;

    private String notes;

    private String receiptId;

}
