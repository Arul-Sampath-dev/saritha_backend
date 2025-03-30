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
@Document(collection = "invoices")
public class Invoice {

    @Id
    private String id;
    private String invoiceId;

    private String shopId;
    private String orderId;

    private double totalAmount;
    private String paymentStatus;

    private String invoiceUrl;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date invoiceDate;
    private String invoiceNotes;

    
    @DBRef
    private Customer customer;
    @DBRef
    private Receipt receipt;

}