package com.saritha.fashions.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.payload.invoice.InvoiceAssignmentRequestDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponseDTO;
import com.saritha.fashions.payload.invoice.UpdateItemsRequest;
import com.saritha.fashions.service.Custom.TwilioWhatsAppSender;
import com.saritha.fashions.service.Interface.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/api/invoices")
public class InvoiceController {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    TwilioWhatsAppSender twilioWhatsAppSender;

    // Generate Receipt
    @PostMapping("/generate")
    public ResponseEntity<?> generateInvoice(@RequestBody Invoice invoice) {
        Invoice invoiceGenerated = invoiceService.generateInvoice(invoice);
        if (invoiceGenerated != null) {
            return ResponseEntity.ok(invoiceGenerated);
        }else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Fetch Invoice by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getInvoice(@PathVariable String id) {

        Optional<Invoice> invoice = invoiceService.getInvoice(id);
        return invoice.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping("/{id}/update-payment")
    public ResponseEntity<?> updatePaymentFromReceiptPage(
            @PathVariable String id,
            @RequestParam String paymentAmount,
            @RequestParam String paymentMethod
    ) {
        Invoice invoice = invoiceService.updatePaymentAmountInInvoiceAndReceipt(id, paymentAmount, paymentMethod);
        return new ResponseEntity<>(invoice, HttpStatus.OK);
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
    public ResponseEntity<List<Invoice>> getInvoicesByShop(@PathVariable String shopId) {

        List<Invoice> allReceiptOfParticularShop = invoiceService.getInvoicesByShop(shopId);
        return ResponseEntity.ok(allReceiptOfParticularShop);
    }

    @GetMapping("/all-invoice")
    public ResponseEntity<InvoiceResponse> getAllInvoices(
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        InvoiceResponse invoice = invoiceService.getAllInvoiceFromParticularShop(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(invoice, HttpStatus.OK);
    }

    @GetMapping("/special-category/all-invoice")
    public ResponseEntity<InvoiceResponse> getAllInvoiceFromParticularShopInSpecialCategory(
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        InvoiceResponse invoice = invoiceService.getAllInvoiceFromParticularShopInSpecialCategory(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(invoice, HttpStatus.OK);
    }

    @PostMapping(value = "/generate-invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> generateInvoice(
            @RequestPart("invoiceJson") String invoiceJsonStr,
            @RequestPart(value = "images", required = false) List<MultipartFile> itemImages)
    {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Invoice invoice = objectMapper.readValue(invoiceJsonStr, Invoice.class);

            ResponseEntity<byte[]> savedInvoice = invoiceService.createInvoiceWithImages(invoice, itemImages, 1);
            return ResponseEntity.ok(savedInvoice);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @PutMapping(value = "/update-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateInvoiceItems(
            @RequestPart("request") String requestJson,
            @RequestPart(value = "images", required = false) MultipartFile[] imageFiles,
            @RequestPart(value = "imageFileNames", required = false) String imageFileNames) throws IOException {

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            UpdateItemsRequest updateItemsRequest = objectMapper.readValue(requestJson, UpdateItemsRequest.class);

            // Convert JSON string to list
            List<String> imageFileName = imageFileNames != null ?
                    objectMapper.readValue(imageFileNames, new TypeReference<>() {
                    }) :
                    new ArrayList<>();

            // Convert array to list
            List<MultipartFile> imagesList = imageFiles != null ? Arrays.asList(imageFiles) : new ArrayList<>();

            updateItemsRequest.setImageFiles(imagesList);
            updateItemsRequest.setImageFileNames(imageFileName);

            Invoice updatedInvoice = invoiceService.updateInvoiceItems(updateItemsRequest);
            return ResponseEntity.ok(updatedInvoice);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing request: " + e.getMessage());
        }
    }

    @GetMapping("/send-message")
    public ResponseEntity<?> sendDemoMessage() {
        twilioWhatsAppSender.sendWhatsAppMessage("+916203932586", "Hello","128934689234910283");
        return new ResponseEntity<>("Ok", HttpStatus.OK);
    }
}
