package com.saritha.fashions.controller;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.tailor.OrderStatusUpdate;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;
import com.saritha.fashions.service.Interface.ShopAssistantService;
import com.saritha.fashions.service.Interface.TailorOrderService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v1/api/shop-assistant")
public class ShopAssistantController {

    @Autowired
    private ShopAssistantService shopAssistantService;

    @Autowired
    private ModelMapper modelMapper;

    @GetMapping("/orders")
    public ResponseEntity<InvoiceResponse> getAllInvoices(
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        InvoiceResponse invoice = shopAssistantService.getAssignedOrders(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(invoice, HttpStatus.OK);
    }

    @GetMapping("/view-order/{invoiceId}")
    public ResponseEntity<?> getInvoiceDetails(@PathVariable String invoiceId) {
//        Optional<Invoice> optionalInvoice = tailorOrderService.getInvoice(invoiceId);
        Invoice optionalInvoice = shopAssistantService.getInvoiceWithAssignedItems(invoiceId);
        List<Invoice> invoices = new ArrayList<>();
        invoices.add(optionalInvoice);
        return new ResponseEntity<>(invoices, HttpStatus.OK);

    }

    @GetMapping("/profile")
    public ResponseEntity<?> getMyProfileDetails() {
        MinimumUserDetailsDTO minimumUserDetailsDTO = shopAssistantService.getMyProfileDetails();
        return new ResponseEntity<>(minimumUserDetailsDTO, HttpStatus.OK);
    }

    // Update status of an assigned order
    @PutMapping("/orders/update-status")
    public ResponseEntity<OrderStatusUpdate> updateOrderStatus(
            @RequestBody OrderStatusUpdate statusUpdate
    ) {
        Invoice updatedInvoice = shopAssistantService.updateOrderStatusByTailor(statusUpdate);
        OrderStatusUpdate orderStatusUpdated = new OrderStatusUpdate();
        orderStatusUpdated.setNewStatus(updatedInvoice.getOrderStatus());
        orderStatusUpdated.setInvoiceId(updatedInvoice.getId());
        return ResponseEntity.ok(orderStatusUpdated);
    }
}

