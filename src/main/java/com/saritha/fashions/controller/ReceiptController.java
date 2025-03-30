package com.saritha.fashions.controller;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/v1/api/receipt")
public class ReceiptController {

    @Autowired
    private ReceiptService receiptService;

    // Generate Receipt
    @PostMapping("/generate")
    public ResponseEntity<?> generateInvoice(@RequestBody Receipt receipt) {
        Receipt invoiceGenerated = receiptService.generateReceipt(receipt);
        if (invoiceGenerated != null) {
            return ResponseEntity.ok(invoiceGenerated);
        }else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Fetch Receipt by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getReceipt(@PathVariable String id) {

        Optional<Receipt> receipt = receiptService.getReceipt(id);
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
           return ResponseEntity.badRequest().body("Invoice not found so that payment status not updated!");
       }
    }

    // Fetch All Receipt for a Specific Shop
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<Receipt>> getInvoicesByShop(@PathVariable String shopId) {

        List<Receipt> allInvoiceOfParticularShop = receiptService.getReceiptByShop(shopId);
        return ResponseEntity.ok(allInvoiceOfParticularShop);
    }

    // Fetch all Receipt from all the shops
    @GetMapping("/all-receipt")
    public Page<Receipt> getAllReceipts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return receiptService.getAllReceiptFromAllShop(page, size);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> generateReceipt(@RequestBody Receipt receipt) {
        return receiptService.generateReceiptAndInvoice(receipt,1);
    }

     @PostMapping("/download/invoice")
    public ResponseEntity<byte[]> generateAndDownloadInvoice(@RequestBody Receipt receipt) {
        return receiptService.generateReceiptAndInvoice(receipt,2);
    }

}
