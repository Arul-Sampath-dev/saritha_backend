package com.saritha.fashions.controller;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.invoice.InvoiceAssignmentRequestDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponseDTO;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.TailorAssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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
    public ResponseEntity<InvoiceResponseDTO> assignTailor(@RequestBody InvoiceAssignmentRequestDTO requestDTO) {
        return new ResponseEntity<>(invoiceService.assignTailorToInvoice(requestDTO), HttpStatus.OK);
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

    @GetMapping("/all-tailor")
    public ResponseEntity<?> fetchAllTailorFromParticularShop() {
        List<User> user = tailorAssignmentService.fetchAllTailorFromParticularShop();
        List<MinimumUserDetailsDTO> minimumUserDetailsDTOS = new ArrayList<>();

        for (User users : user) {
            MinimumUserDetailsDTO minimumUserDetailsDTO = new MinimumUserDetailsDTO();
            minimumUserDetailsDTO.setName(users.getName());
            minimumUserDetailsDTO.setUserId(users.getUserId());
            minimumUserDetailsDTOS.add(minimumUserDetailsDTO);
        }

        return new ResponseEntity<>(minimumUserDetailsDTOS, HttpStatus.OK);
    }

    @GetMapping("/view-order/{invoiceId}")
    public ResponseEntity<?> getInvoiceDetails(@PathVariable String invoiceId) {
        Optional<Invoice> optionalInvoice = invoiceService.getInvoice(invoiceId);
        if (optionalInvoice.isPresent()) {
            return new ResponseEntity<>(optionalInvoice.get(), HttpStatus.OK);
        }else {
            throw new APIException("No invoice Found");
        }
    }
}
