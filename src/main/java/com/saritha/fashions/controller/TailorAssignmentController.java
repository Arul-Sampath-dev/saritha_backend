package com.saritha.fashions.controller;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.service.Interface.TailorAssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tailor-assignment")
public class TailorAssignmentController {

    @Autowired
    private TailorAssignmentService tailorAssignmentService;

    @PostMapping("/assign")
    public Invoice assignTailor(
            @RequestParam String invoiceId,
            @RequestParam String tailorId) {
        return tailorAssignmentService.assignTailorToInvoice(invoiceId, tailorId);
    }

    @PostMapping("/unassign")
    public Invoice unassignTailor(
            @RequestParam String invoiceId) {
        return tailorAssignmentService.unassignTailorFromInvoice(invoiceId);
    }

    @GetMapping("/shop/{shopId}")
    public List<Invoice> getInvoicesByShop(@PathVariable String shopId) {
        return tailorAssignmentService.getInvoicesForShop(shopId);
    }

    @GetMapping("/tailor/{tailorId}")
    public List<Invoice> getInvoicesAssignedToTailor(@PathVariable String tailorId) {
        return tailorAssignmentService.getInvoicesAssignedToTailor(tailorId);
    }
}
