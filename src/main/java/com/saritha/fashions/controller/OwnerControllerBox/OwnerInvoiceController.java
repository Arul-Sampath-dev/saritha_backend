package com.saritha.fashions.controller.OwnerControllerBox;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.receipt.ReceiptDTO;
import com.saritha.fashions.service.Interface.OwnerService;
import com.saritha.fashions.service.Interface.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/v1/api/owner/invoice")
public class OwnerInvoiceController {

    @Autowired
    private ShopService shopService;

    @Autowired
    private OwnerService ownerService;

    @GetMapping("/{shopId}/all-invoice")
    public ResponseEntity<PaginatedResponse<InvoiceDTO>> getAllReceiptFromParticularShop(
            @PathVariable String shopId,
            @RequestParam(name = "term", required = false) String term,
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate
    ) {
        PaginatedResponse<InvoiceDTO> invoiceDTOPaginatedResponse = ownerService.getAllInvoiceFromParticularShop(
                pageNumber, pageSize, sortBy, sortOrder, shopId, startDate, endDate, term);
        return new ResponseEntity<>(invoiceDTOPaginatedResponse, HttpStatus.OK);
    }

    @GetMapping("/{shopId}/download/{invoiceId}")
    public ResponseEntity<InvoiceDTO> getInvoiceByInvoiceId(@PathVariable String shopId, @PathVariable String invoiceId) {
        InvoiceDTO receiptDTO = ownerService.getInvoiceByInvoiceId(shopId, invoiceId);
        return new ResponseEntity<>(receiptDTO, HttpStatus.OK);
    }

    @PostMapping("/invoice-download")
    public ResponseEntity<byte[]> downloadInvoiceById(@RequestBody InvoiceDTO receiptDTO) {
        byte[] receipt = ownerService.downloadInvoice(receiptDTO);
        return new ResponseEntity<>(receipt, HttpStatus.OK);
    }

}
