package com.saritha.fashions.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.payload.receipt.ReceiptResponse;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/api/receipt")
public class ReceiptController {

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private InvoiceService invoiceService;

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
           return ResponseEntity.badRequest().body("Receipt not found so that payment status not updated!");
       }
    }

    // Fetch All Invoice for a Specific Shop
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<Receipt>> getInvoicesByShop(@PathVariable String shopId) {

        List<Receipt> allInvoiceOfParticularShop = receiptService.getReceiptByShop(shopId);
        return ResponseEntity.ok(allInvoiceOfParticularShop);
    }

    // Fetch all receipt from all the shops
//    @GetMapping("/all-receipt")
//    public Page<Receipt> getAllReceipts(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size) {
//        return receiptService.getAllReceiptFromAllShop(page, size);
//    }

    @GetMapping("/all-receipt")
    public ResponseEntity<ReceiptResponse> getAllCategories(
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        System.out.println("pageNumber: " + pageNumber + "pageSize: " + pageSize + "sortBy: " + sortBy + "sortOrder: " + sortOrder);
        ReceiptResponse receipt = receiptService.getAllReceiptFromAllShops(pageNumber, pageSize, sortBy, sortOrder);
        System.out.println( "ReceiptResponse" + receipt);
        return new ResponseEntity<>(receipt, HttpStatus.OK);
    }


    @PostMapping("/download/receipt")
    public ResponseEntity<byte[]> generateReceipt(@RequestBody Invoice invoice) {
        return receiptService.generateReceiptAndInvoice(invoice,2);
    }

//     @PostMapping("/download/invoice")
//    public ResponseEntity<byte[]> generateAndDownloadInvoice(@RequestBody Invoice invoice) {
//        return receiptService.generateReceiptAndInvoice(invoice,1);
//    }

    @PostMapping(value = "/download/invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> generateAndDownloadInvoice(
            @RequestPart("invoice") String invoiceJson,
            @RequestPart(value = "images", required = false) List<MultipartFile> itemImages) {

        try {
            System.out.println("invoiceJSON: " + invoiceJson);
            ObjectMapper objectMapper = new ObjectMapper();
            Invoice invoice = objectMapper.readValue(invoiceJson, Invoice.class);
            System.out.println("Invoice after conversion: " + invoice);
            return invoiceService.createInvoiceWithImages(invoice, itemImages, 1);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

//    @PostMapping(value = "/download/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<byte[]> generateReceipt(
//            @RequestPart("invoice") String invoiceJson,
//            @RequestPart(value = "images", required = false) List<MultipartFile> itemImages) {
//
//        try {
//            ObjectMapper objectMapper = new ObjectMapper();
//            Invoice invoice = objectMapper.readValue(invoiceJson, Invoice.class);
//            return receiptService.generateReceiptAndInvoice(invoice, itemImages, 2);
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
//        }
//    }


}
