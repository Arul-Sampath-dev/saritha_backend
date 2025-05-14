package com.saritha.fashions.controller.OwnerControllerBox;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.payload.shop.PettyCashDTO;
import com.saritha.fashions.service.Interface.OwnerService;
import com.saritha.fashions.service.Interface.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/v1/api/owner/petty-cash")
public class OwnerPettyCashController {

    @Autowired
    private ShopService shopService;

    @Autowired
    private OwnerService ownerService;

//        @GetMapping("/{shopId}/all-pettyCash")
//    public ResponseEntity<PaginatedResponse<PettyCashDTO>> getAllEmployeeFromParticularShop(
//            @PathVariable String shopId,
//            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
//            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
//            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY_ADDING_DATE, required = false) String sortBy,
//            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
//    ) {
//        PaginatedResponse<PettyCashDTO> pettyCashDTOPaginatedResponse = ownerService.getAllPettyCashByShopId(pageNumber, pageSize, sortBy, sortOrder, shopId);
//        return new ResponseEntity<>(pettyCashDTOPaginatedResponse, HttpStatus.OK);
//    }

    @GetMapping("/{shopId}/all-pettyCash")
    public ResponseEntity<PaginatedResponse<PettyCashDTO>> getAllEmployeeFromParticularShop(
            @PathVariable String shopId,
            @RequestParam(name = "searchTerm", required = false) String searchTerm,
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY_ADDING_DATE, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate
    ) {
        PaginatedResponse<PettyCashDTO> invoiceDTOPaginatedResponse = ownerService.getAllPettyCashByShopId(
                pageNumber, pageSize, sortBy, sortOrder, shopId, startDate, endDate, searchTerm);
        return new ResponseEntity<>(invoiceDTOPaginatedResponse, HttpStatus.OK);
    }

    @GetMapping("/{shopId}/search-by-amount/{term}")
    public ResponseEntity<PaginatedResponse<PettyCashDTO>> searchPettyCashByAmount(
            @PathVariable String shopId,
            @PathVariable String term,
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY_ADDING_DATE, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        PaginatedResponse<PettyCashDTO> response = ownerService.searchPettyCashByAmount(shopId, term, pageNumber, pageSize);
        return ResponseEntity.ok(response);
    }
}
