package com.saritha.fashions.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "deleted_invoices")
public class DeletedReceipt {

    @Id
    private String id;

    private Receipt invoice;

    private String deletedBy;
    private Date deletedAt;
    private String reason;
}
