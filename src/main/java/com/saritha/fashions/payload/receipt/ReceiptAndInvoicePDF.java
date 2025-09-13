//package com.saritha.fashions.payload.receipt;
//
//import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//@JsonIgnoreProperties(ignoreUnknown = true)
//public class ReceiptAndInvoicePDF {
//    private double totalAmount;
//    private double advancePaid;
//    private double balanceDue;
//    private double discount;
//    private double totalAmountWithGST;
//    private String paymentMethod;
//    private String paymentStatus;
//    private String deliveryDate;
//
//    public ReceiptAndInvoicePDF(double totalAmount, double advancePaid, double balanceDue, String paymentMethod, String paymentStatus, String expectedDeliveryDate, double discount, double totalAmountWithGST) {
//        this.advancePaid = advancePaid;
//        this.balanceDue = balanceDue;
//        this.totalAmount = totalAmount;
//        this.paymentMethod = paymentMethod;
//        this.paymentStatus = paymentStatus;
//        this.deliveryDate = expectedDeliveryDate;
//        this.discount = discount;
//        this.totalAmountWithGST = totalAmountWithGST;
//    }
//}

package com.saritha.fashions.payload.receipt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReceiptAndInvoicePDF {
    private double totalAmount;
    private double advancePaid;
    private double balanceDue;

    private double discount;
    private double taxableAmount;

    private double cgstRate;
    private double sgstRate;
    private double igstRate;

    private double cgstAmount;
    private double sgstAmount;
    private double igstAmount;
    private String hsnCode;
    private String sacCode;


    private double totalAmountWithGST;

    private String paymentMethod;
    private String paymentStatus;
    private String deliveryDate;

    // Constructor for quick mapping
    public ReceiptAndInvoicePDF(
            double totalAmount,
            double advancePaid,
            double balanceDue,
            String paymentMethod,
            String paymentStatus,
            String expectedDeliveryDate,
            double discount,
            double taxableAmount,
            double cgstRate,
            double sgstRate,
            double igstRate,
            double cgstAmount,
            double sgstAmount,
            double igstAmount,
            double totalAmountWithGST,
            String hsnCode
    ) {
        this.totalAmount = totalAmount;
        this.advancePaid = advancePaid;
        this.balanceDue = balanceDue;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.deliveryDate = expectedDeliveryDate;
        this.discount = discount;
        this.taxableAmount = taxableAmount;
        this.cgstRate = cgstRate;
        this.sgstRate = sgstRate;
        this.igstRate = igstRate;
        this.cgstAmount = cgstAmount;
        this.sgstAmount = sgstAmount;
        this.igstAmount = igstAmount;
        this.totalAmountWithGST = totalAmountWithGST;
        this.hsnCode = hsnCode;
    }
}
