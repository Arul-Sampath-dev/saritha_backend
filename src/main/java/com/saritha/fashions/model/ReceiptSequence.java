package com.saritha.fashions.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "receipt_sequence")
public class ReceiptSequence {
    @Id
    private String year;
    private int sequence;
}
