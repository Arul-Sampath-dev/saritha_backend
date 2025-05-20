package com.saritha.fashions.controller.ShopOwnerControllerBox;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.service.Interface.ShopOwner.InvoiceAPIService;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/shopOwner/invoices")
public class InvoiceAPIController {

    @Autowired
    private InvoiceAPIService invoiceAPIService;

    @DeleteMapping("{shopId}/delete-invoice/{invoiceId}")
    public ResponseEntity<?> deleteInvoiceByInvoiceId(@PathVariable String shopId, @PathVariable String invoiceId) {
        Invoice invoice = invoiceAPIService.deleteInvoiceByInvoiceId(shopId, invoiceId);
        return new ResponseEntity<>(invoice.getCustomer().getName(), HttpStatus.OK);
    }
}
