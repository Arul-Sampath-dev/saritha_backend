package com.saritha.fashions.controller;

import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    @GetMapping("/download-invoice/{token}")
    public ResponseEntity<String> downloadInvoiceByCustomer(@PathVariable String token) {
        try {
            ResponseEntity<byte[]> pdfResponse = receiptService.downloadInvoiceByCustomerPdf(token);
            byte[] pdfBytes = pdfResponse.getBody();
            if (pdfBytes == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Invoice PDF is empty");
            }

            String base64Pdf = java.util.Base64.getEncoder().encodeToString(pdfBytes);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(base64Pdf);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/download-receipt/{token}")
    public ResponseEntity<?> downloadReceiptByCustomer(@PathVariable String token) {
        try {
            return receiptService.downloadReceiptByCustomerPdf(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }
}
