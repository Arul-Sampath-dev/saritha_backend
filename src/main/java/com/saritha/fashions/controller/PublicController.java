package com.saritha.fashions.controller;

import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api/public")
public class PublicController {


    @Autowired
    private ReceiptService receiptService;

    @GetMapping("/download-invoice/{invoiceId}")
    public ResponseEntity<?> downloadInvoiceByCustomer(@PathVariable String invoiceId) {
        try {
            ResponseEntity<byte[]> savedInvoice = receiptService.downloadInvoiceByCustomerPdf(invoiceId);
            System.out.println("SavedInvoice: " + savedInvoice);
            return ResponseEntity.ok(savedInvoice);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/download-receipt/{receiptId}")
    public ResponseEntity<?> downloadReceiptByCustomer(@PathVariable String receiptId) {
        try {
            ResponseEntity<byte[]> savedInvoice = receiptService.downloadReceiptByCustomerPdf(receiptId);
            System.out.println("SavedInvoice: " + savedInvoice);
            return ResponseEntity.ok(savedInvoice);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }
}
