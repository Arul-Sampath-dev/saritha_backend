package com.saritha.fashions.model;

import com.saritha.fashions.payload.Items;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "receipt")
public class Receipt {
    @Id
    private String id;
    @DBRef
    private Customer customer;
    private String shopId;

    private List<Items> items;  // List of ordered items

    private double totalAmount;
    private double advancePaid;
    private double balanceDue;
    private String paymentMethod;
    private String paymentStatus;
    private String orderStatus;  // Pending | In Progress | Completed | Delivered
    private String orderDate;
    private String expectedDeliveryDate;  // Expected completion date

    private String notes;

}
