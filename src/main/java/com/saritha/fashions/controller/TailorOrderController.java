package com.saritha.fashions.controller;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.invoice.InvoiceAssignmentRequestDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponseDTO;
import com.saritha.fashions.payload.invoice.OrderStatusUpdateRequest;
import com.saritha.fashions.payload.tailor.OrderStatusUpdate;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.TailorAssignmentService;
import com.saritha.fashions.service.Interface.TailorOrderService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/api/tailor")
public class TailorOrderController {

    private final TailorAssignmentService tailorAssignmentService;
    private final InvoiceService invoiceService;
    private final ModelMapper modelMapper;
    private final TailorOrderService tailorOrderService;

    public TailorOrderController (TailorAssignmentService tailorAssignmentService,
                                       InvoiceService invoiceService,
                                        ModelMapper modelMapper,
                                       TailorOrderService tailorOrderService) {
        this.invoiceService = invoiceService;
        this.modelMapper = modelMapper;
        this.tailorAssignmentService = tailorAssignmentService;
        this.tailorOrderService = tailorOrderService;
    }


    @GetMapping("/orders")
    public ResponseEntity<InvoiceResponse> getAllInvoices(
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        InvoiceResponse invoice = tailorOrderService.getAssignedOrders(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(invoice, HttpStatus.OK);
    }

    @GetMapping("/all-shopAssistant")
    public ResponseEntity<?> fetchAllShopAssistantFromParticularShop() {
        List<User> user = tailorAssignmentService.fetchAllShopAssistantFromParticularShop();
        List<MinimumUserDetailsDTO> minimumUserDetailsDTOS = new ArrayList<>();

        for (User users : user) {
            MinimumUserDetailsDTO minimumUserDetailsDTO = new MinimumUserDetailsDTO();
            minimumUserDetailsDTO.setName(users.getName());
            minimumUserDetailsDTO.setUserId(users.getUserId());
            minimumUserDetailsDTOS.add(minimumUserDetailsDTO);
        }

        return new ResponseEntity<>(minimumUserDetailsDTOS, HttpStatus.OK);
    }

    @PostMapping("/assign-shopAssistant")
    public ResponseEntity<InvoiceResponseDTO> assignShopAssistant(@RequestBody InvoiceAssignmentRequestDTO requestDTO) {
        return new ResponseEntity<>(invoiceService.assignShopAssistantToInvoice(requestDTO), HttpStatus.OK);
    }

    @GetMapping("/view-order/{invoiceId}")
    public ResponseEntity<?> getInvoiceDetails(@PathVariable String invoiceId) {
//        Optional<Invoice> optionalInvoice = tailorOrderService.getInvoice(invoiceId);
        Invoice optionalInvoice = tailorOrderService.getInvoiceWithAssignedItems(invoiceId);
        List<Invoice> invoices = new ArrayList<>();
        invoices.add(optionalInvoice);
        return new ResponseEntity<>(invoices, HttpStatus.OK);

    }

    @GetMapping("/profile")
    public ResponseEntity<?> getTailorProfileDetails() {
        MinimumUserDetailsDTO minimumUserDetailsDTO = tailorOrderService.getTailorProfileDetails();
        return new ResponseEntity<>(minimumUserDetailsDTO, HttpStatus.OK);
    }

    // Update status of an assigned order
    @PutMapping("/orders/update-status")
    public ResponseEntity<OrderStatusUpdate> updateOrderStatus(
            @RequestBody OrderStatusUpdate statusUpdate
    ) {
        Invoice updatedInvoice = tailorOrderService.updateOrderStatusByTailor(statusUpdate);
        OrderStatusUpdate orderStatusUpdated = new OrderStatusUpdate();
        orderStatusUpdated.setNewStatus(updatedInvoice.getOrderStatus());
        orderStatusUpdated.setInvoiceId(updatedInvoice.getId());
        return ResponseEntity.ok(orderStatusUpdated);
    }
}

