package com.saritha.fashions.controller;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.payload.invoice.InvoiceAssignmentRequestDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponseDTO;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.TailorAssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/tailor-master")
public class TailorAssignmentController {


    private final TailorAssignmentService tailorAssignmentService;
    private final InvoiceService invoiceService;

    public TailorAssignmentController (TailorAssignmentService tailorAssignmentService, InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
        this.tailorAssignmentService = tailorAssignmentService;
    }

    @PostMapping("/assign-tailor")
    public InvoiceResponseDTO assignTailor(@RequestBody InvoiceAssignmentRequestDTO requestDTO) {
        return invoiceService.assignTailorToInvoice(requestDTO);
    }

    @PostMapping("/unassign-tailor/{invoiceId}")
    public InvoiceResponseDTO unassignTailor(@PathVariable String invoiceId) {
        return invoiceService.unassignTailorFromInvoice(invoiceId);
    }

    @GetMapping("/shop/all-invoice")
    public ResponseEntity<InvoiceResponse> getAllInvoices(
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        System.out.println("pageNumber: " + pageNumber + "pageSize: " + pageSize + "sortBy: " + sortBy + "sortOrder: " + sortOrder);
        InvoiceResponse invoice = invoiceService.getAllInvoiceFromParticularShop(pageNumber, pageSize, sortBy, sortOrder);
        System.out.println( "ReceiptResponse" + invoice);
        return new ResponseEntity<>(invoice, HttpStatus.OK);
    }

    @GetMapping("/tailor/{tailorId}")
    public List<Invoice> getInvoicesAssignedToTailor(@PathVariable String tailorId) {
        return tailorAssignmentService.getInvoicesAssignedToTailor(tailorId);
    }
}
