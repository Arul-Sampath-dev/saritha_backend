package com.saritha.fashions.controller;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponseDTO;
import com.saritha.fashions.payload.invoice.OrderStatusUpdateRequest;
import com.saritha.fashions.service.Interface.TailorOrderService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/tailor")
public class TailorOrderController {

    @Autowired
    private TailorOrderService tailorOrderService;

    @Autowired
    private ModelMapper modelMapper;

    // Get all assigned orders
//    @GetMapping("/orders")
//    public ResponseEntity<List<Invoice>> getAssignedOrders() {
//        List<Invoice> orders = tailorOrderService.getAssignedOrders();
////        List<InvoiceResponseDTO> invoiceResponseDTOS = orders.stream().map(invoice -> modelMapper.map(invoice, InvoiceResponseDTO.class)).toList();
//        return ResponseEntity.ok(orders);
//    }

    @GetMapping("/orders")
    public ResponseEntity<InvoiceResponse> getAllInvoices(
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        System.out.println("pageNumber: " + pageNumber + "pageSize: " + pageSize + "sortBy: " + sortBy + "sortOrder: " + sortOrder);
        InvoiceResponse invoice = tailorOrderService.getAssignedOrders(pageNumber, pageSize, sortBy, sortOrder);
        System.out.println( "ReceiptResponse" + invoice);
        return new ResponseEntity<>(invoice, HttpStatus.OK);
    }

    // Update status of an assigned order
    @PutMapping("/orders/{invoiceId}/status")
    public ResponseEntity<Invoice> updateOrderStatus(
            @PathVariable String invoiceId,
            @RequestParam String tailorId,
            @RequestBody OrderStatusUpdateRequest statusUpdate
    ) {
        Invoice updatedInvoice = tailorOrderService.updateOrderStatus(tailorId, invoiceId, statusUpdate.getStatus());
        return ResponseEntity.ok(updatedInvoice);
    }
}

