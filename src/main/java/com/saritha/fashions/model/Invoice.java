package com.saritha.fashions.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "invoices")
public class Invoice {
    @Id
    private String id;
    private String shopId; // Link invoice to a specific tailor shop
    private String customerName;
    private String orderId;
    private double totalAmount;
    private String status; // PAID or UNPAID
    private String paymentMethod; // Cash, GPay, etc.
    private String invoiceUrl;
    private String customerPhoneNumber; // WhatsApp integration
}
