package com.saritha.fashions.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customers")
public class Customer {

    @Id
    private String id;
    private String customerId;
    private String name;
    private String email;
    private String phone;
    private String alternatePhone;
    private String address;
    private List<String> allInvoiceIds = new ArrayList<>();
    private List<String> allReceiptIds = new ArrayList<>();

    @JsonIgnore
    private List<Invoice> allInvoices =  new ArrayList<>();
    @JsonIgnore
    private List<Receipt> allReceipts =  new ArrayList<>();

}
