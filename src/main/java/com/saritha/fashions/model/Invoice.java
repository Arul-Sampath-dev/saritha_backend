package com.saritha.fashions.model;

import com.saritha.fashions.payload.Items;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "invoices")
public class Invoice {

    @Id
    private String id;
    private String shopId;
    private String orderId;
    private List<Items> items;
    private double totalAmount;
    private String paymentStatus;  // Paid | Unpaid | partial
    private String paymentMethod;
    private String invoiceUrl;
    private String invoiceDate;
    private String notes;
    private String invoiceNumber;

    
    @DBRef
    private Customer customer;

//    private List<Service> items;  // List of items in invoice
//    private List<OrderItem> items;  // List of items in invoice

}