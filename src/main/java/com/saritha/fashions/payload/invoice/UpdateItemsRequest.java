//package com.saritha.fashions.payload.invoice;
//
//import com.saritha.fashions.payload.items.Items;
//import lombok.Data;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.util.List;
//
//@Data
//public class UpdateItemsRequest {
//    private String invoiceId;
//    private List<Items> items;
//    private List<MultipartFile> imageFiles;
//    private List<String> imageFileNames;
//}

package com.saritha.fashions.payload.invoice;

import com.saritha.fashions.model.Customer;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.payload.items.Items;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateItemsRequest {
    @NotBlank(message = "Invoice ID is required")
    private String invoiceId;

    private Customer customer;
    private Double discount;
    private String assignedTailorId;
    private String assignmentStatus;
    private List<Invoice.AssignmentHistory> assignmentHistory;
    private List<Items> items;
    private String transactionId;
    private Double totalAmount;
    private Double advancePaid;
    private Double balanceDue;
    private String paymentMethod;
    private String paymentStatus;
    private String orderStatus;

    @FutureOrPresent(message = "Expected delivery date must be today or in future")
    private String expectedDeliveryDate;

    private Date paymentCompletedDate;
    private Date orderDate;
    private Date invoiceDate;
    private String notes;
    private String receiptId;
    private List<String> creditNoteIds;
    private List<MultipartFile> imageFiles;
    private List<String> imageFileNames;
    private List<String> deletedItems;
}