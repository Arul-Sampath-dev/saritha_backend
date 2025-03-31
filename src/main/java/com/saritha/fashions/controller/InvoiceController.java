package com.saritha.fashions.controller;

import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.service.Interface.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/api/invoices")
public class InvoiceController {

    @Autowired
    private InvoiceService invoiceService;

    // Generate Receipt
    @PostMapping("/generate")
    public ResponseEntity<?> generateInvoice(@RequestBody Receipt receipt) {
        Receipt receiptGenerated = invoiceService.generateInvoice(receipt);
        if (receiptGenerated != null) {
            return ResponseEntity.ok(receiptGenerated);
        }else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Fetch Receipt by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getInvoice(@PathVariable String id) {

        Optional<Receipt> invoice = invoiceService.getInvoice(id);
        return invoice.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // Update Payment Status
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updatePaymentStatus(@PathVariable String id, @RequestParam String status) {
       boolean updatedStatusOrNot = invoiceService.updatePaymentStatus(id, status);
       if (updatedStatusOrNot) {
           return ResponseEntity.ok("Payment status updated!");
       }else {
           return ResponseEntity.badRequest().body("Receipt not found so that payment status not updated!");
       }
    }

    // Fetch All Invoices for a Specific Shop
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<Receipt>> getInvoicesByShop(@PathVariable String shopId) {

        List<Receipt> allReceiptOfParticularShop = invoiceService.getInvoicesByShop(shopId);
        return ResponseEntity.ok(allReceiptOfParticularShop);
    }
}
