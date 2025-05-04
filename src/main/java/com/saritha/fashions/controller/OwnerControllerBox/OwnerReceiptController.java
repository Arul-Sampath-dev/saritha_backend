package com.saritha.fashions.controller.OwnerControllerBox;

import com.saritha.fashions.payload.receipt.ReceiptDTO;
import com.saritha.fashions.payload.receipt.ReceiptResponse;
import com.saritha.fashions.service.Interface.OwnerService;
import com.saritha.fashions.service.Interface.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/v1/api/owner/receipt")
public class OwnerReceiptController {

    @Autowired
    private ShopService shopService;

    @Autowired
    private OwnerService ownerService;

    @GetMapping("/{shopId}/all-receipt")
    public ResponseEntity<ReceiptResponse> getAllReceiptFromParticularShop(
            @PathVariable String shopId,
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        ReceiptResponse invoice = ownerService.getAllReceiptByShopId(pageNumber, pageSize, sortBy, sortOrder, shopId);
        return new ResponseEntity<>(invoice, HttpStatus.OK);
    }

    @GetMapping("/{shopId}/download/{receiptId}")
    public ResponseEntity<ReceiptDTO> getReceiptByReceiptId(@PathVariable String shopId, @PathVariable String receiptId) {
        ReceiptDTO receiptDTO = ownerService.getReceiptByReceiptId(shopId, receiptId);
        return new ResponseEntity<>(receiptDTO, HttpStatus.OK);
    }

    @PostMapping("/receipt-download")
    public ResponseEntity<byte[]> downloadReceiptById(@RequestBody ReceiptDTO receiptDTO) throws IOException {
        byte[] receipt = ownerService.downloadReceipt(receiptDTO);
        return new ResponseEntity<>(receipt, HttpStatus.OK);
    }

}
