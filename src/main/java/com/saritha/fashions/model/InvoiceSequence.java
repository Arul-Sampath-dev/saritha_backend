package com.saritha.fashions.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "invoice_sequence")
public class InvoiceSequence {

    //    @Id
    //    private String year;
    //    private int sequence;

    @Id
    private String id;
    private String year;
    private String shopId;
    private int sequence;
}
