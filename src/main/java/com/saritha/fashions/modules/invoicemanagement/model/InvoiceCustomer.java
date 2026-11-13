package com.saritha.fashions.modules.invoicemanagement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Customer as seen by the InvoiceManagement module.
 *
 * Maps to the same "customers" collection as com.saritha.fashions.model.Customer with
 * the same field names. Only the fields invoice creation needs are declared; the old
 * embedded allInvoices / allReceipts arrays are never written from here.
 */
@Document(collection = "customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceCustomer {

    @Id
    private String id;

    /** Business id, e.g. "CUS-8F3K2Q1ZXA". */
    private String customerId;
    private String name;
    private String email;
    private String phone;
    private String alternatePhone;
    private String address;

    @Builder.Default
    private List<String> allInvoiceIds = new ArrayList<>();

    @Builder.Default
    private List<String> allReceiptIds = new ArrayList<>();
}
