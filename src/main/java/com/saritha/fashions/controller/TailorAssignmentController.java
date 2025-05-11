package com.saritha.fashions.controller;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.invoice.InvoiceAssignmentRequestDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponseDTO;
import com.saritha.fashions.payload.tailor.OrderStatusUpdate;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.TailorAssignmentService;
import com.saritha.fashions.service.Interface.TailorOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/api/tailor-master")
public class TailorAssignmentController {


    private final TailorAssignmentService tailorAssignmentService;
    private final InvoiceService invoiceService;
    private final TailorOrderService tailorOrderService;

    public TailorAssignmentController (TailorAssignmentService tailorAssignmentService,
                                       InvoiceService invoiceService,
                                       TailorOrderService tailorOrderService) {
        this.invoiceService = invoiceService;
        this.tailorAssignmentService = tailorAssignmentService;
        this.tailorOrderService = tailorOrderService;
    }

    @GetMapping("/shop/all-invoice")
    public ResponseEntity<InvoiceResponse> getAllInvoices(
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        InvoiceResponse invoice = invoiceService.getAllInvoiceFromParticularShopForTailorMaster(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(invoice, HttpStatus.OK);
    }

    @GetMapping("/shop/order-accepted-status/all-invoice")
    public ResponseEntity<InvoiceResponse> getAllInvoicesOfOnlyOrderAcceptedStatus(
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        InvoiceResponse invoice = tailorOrderService.getAllInvoicesOfOnlyOrderAcceptedStatus(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(invoice, HttpStatus.OK);
    }

    @GetMapping("/tailor/")
    public InvoiceResponse getInvoicesAssignedToTailor(
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        return tailorAssignmentService.getInvoicesAssignedToTailor(pageNumber, pageSize, sortBy, sortOrder);
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
            List<Invoice> invoices = new ArrayList<>();
            invoices.add(optionalInvoice.get());
            return new ResponseEntity<>(invoices, HttpStatus.OK);
        }else {
            throw new APIException("No invoice Found");
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getTailorMasterProfileDetails() {
        MinimumUserDetailsDTO minimumUserDetailsDTO = tailorOrderService.getTailorMasterProfile();
        return new ResponseEntity<>(minimumUserDetailsDTO, HttpStatus.OK);
    }

    @PostMapping("/assign-tailor")
    public ResponseEntity<InvoiceResponseDTO> assignTailor(@RequestBody InvoiceAssignmentRequestDTO requestDTO) {
        return new ResponseEntity<>(invoiceService.assignTailorToInvoice(requestDTO), HttpStatus.OK);
    }

    @PostMapping("/unassign-tailor/{invoiceId}")
    public InvoiceResponseDTO unassignTailor(@PathVariable String invoiceId) {
        return invoiceService.unassignTailorFromInvoice(invoiceId);
    }

    @PutMapping("/my-order/update-status")
    public ResponseEntity<OrderStatusUpdate> updateOrderStatus(
            @RequestBody OrderStatusUpdate statusUpdate
    ) {
        Invoice updatedInvoice = tailorOrderService.updateOrderStatus(statusUpdate);
        OrderStatusUpdate orderStatusUpdated = new OrderStatusUpdate();
        orderStatusUpdated.setNewStatus(updatedInvoice.getOrderStatus());
        orderStatusUpdated.setInvoiceId(updatedInvoice.getId());
        return ResponseEntity.ok(orderStatusUpdated);
    }
}
