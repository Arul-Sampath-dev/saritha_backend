package com.saritha.fashions.controller;

import com.saritha.fashions.service.Interface.DeletedInvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/api/deleted/invoices")
public class DeletedInvoiceController {

    @Autowired
    private DeletedInvoiceService deletedInvoiceService;

    @GetMapping("/all-deleted-invoices")
    public ResponseEntity<Map<String, Object>> getDeletedInvoices(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String invoiceId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate
    ) {
        return ResponseEntity.ok(
                deletedInvoiceService.getDeletedInvoices(
                        pageNumber, pageSize,
                        invoiceId, customerName, paymentStatus, fromDate, toDate
                )
        );
    }
}
