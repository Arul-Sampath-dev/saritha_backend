package com.saritha.fashions.controller;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/api/receipt")
public class ReceiptController {

    @Autowired
    private ReceiptService receiptService;

    // Generate Invoice
    @PostMapping("/generate")
    public ResponseEntity<?> generateInvoice(@RequestBody Invoice invoice) {
        Invoice invoiceGenerated = receiptService.generateReceipt(invoice);
        if (invoiceGenerated != null) {
            return ResponseEntity.ok(invoiceGenerated);
        }else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Fetch Invoice by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getReceipt(@PathVariable String id) {

        Optional<Invoice> receipt = receiptService.getReceipt(id);
        if (receipt.isPresent()) {
            return new ResponseEntity<>(receipt.get(), HttpStatus.OK);
        }else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // Update Payment Status
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updatePaymentStatus(@PathVariable String id, @RequestParam String status) {
       boolean updatedStatusOrNot = receiptService.updatePaymentStatus(id, status);
       if (updatedStatusOrNot) {
           return ResponseEntity.ok("Payment status updated!");
       }else {
           return ResponseEntity.badRequest().body("Receipt not found so that payment status not updated!");
       }
    }

    // Fetch All Invoice for a Specific Shop
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<Invoice>> getInvoicesByShop(@PathVariable String shopId) {

        List<Invoice> allInvoiceOfParticularShop = receiptService.getReceiptByShop(shopId);
        return ResponseEntity.ok(allInvoiceOfParticularShop);
    }

    // Fetch all Invoice from all the shops
    @GetMapping("/all-receipt")
    public Page<Invoice> getAllReceipts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return receiptService.getAllReceiptFromAllShop(page, size);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> generateReceipt(@RequestBody Invoice invoice) {
        return receiptService.generateReceiptAndInvoice(invoice,1);
    }

     @PostMapping("/download/invoice")
    public ResponseEntity<byte[]> generateAndDownloadInvoice(@RequestBody Invoice invoice) {
        return receiptService.generateReceiptAndInvoice(invoice,2);
    }

}
