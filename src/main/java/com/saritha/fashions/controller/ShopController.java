package com.saritha.fashions.controller;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Expense;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.expense.ExpenseDTO;
import com.saritha.fashions.payload.expense.RefreshmentDetails;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.payload.shop.*;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;
import com.saritha.fashions.service.Interface.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/v1/api/shops")
@RequiredArgsConstructor
public class ShopController {

    @Autowired
    private ShopService shopService;

    @GetMapping
    public ResponseEntity<List<AllShopDTO>> getAllShops() {

        List<Shop> shops = shopService.getAllShops();
        List<AllShopDTO> allShopDTOS = new ArrayList<>();

        for (Shop shop : shops) {

            AllShopDTO allShopDTO = new AllShopDTO();

            allShopDTO.setId(shop.getId());
            allShopDTO.setShopContactNumber(shop.getShopContactNumber());
            allShopDTO.setShopAddress(shop.getShopAddress());
            allShopDTO.setShopType(shop.getShopType());
            allShopDTO.setShopName(shop.getShopName());
            allShopDTO.setGstNumber(shop.getGstNumber());
            allShopDTO.setPettyCash(shop.getPettyCash());
            allShopDTO.setOwnerName(shop.getOwnerName());
            allShopDTO.setTotalEmployee(shop.getEmployees().size());
            allShopDTO.setTotalExpense(shop.getAllExpense().stream().mapToDouble(Expense::getExpenseAmount).sum());
            allShopDTO.setTotalInvoice(shop.getAllInvoice().size());
            allShopDTO.setTotalReceipt(shop.getAllReceipts().size());
            allShopDTO.setTotalExpenseEntry(shop.getAllExpense().size());
            allShopDTO.setTotalPendingOrders(shop.getAllInvoice().stream().filter(receipt -> receipt.getOrderStatus().equals("ORDER_ACCEPTED")).toList().size());
            allShopDTO.setTotalCompletedOrders(shop.getAllInvoice().stream().filter(receipt -> receipt.getOrderStatus().equals("ORDER_DELIVERED")).toList().size());

            allShopDTOS.add(allShopDTO);
        }

        return new ResponseEntity<>(allShopDTOS, HttpStatus.OK);
    }

    @GetMapping("/shop-name")
    public ResponseEntity<List<ShopDTO>> getShopDetails() {
        List<Shop> allShops = shopService.getAllShops();
        List<ShopDTO> shopDtos = new ArrayList<>();

        for (Shop shop : allShops) {
            ShopDTO shopDTO = new ShopDTO();
            shopDTO.setShopName(shop.getShopName());
            shopDTO.setShopId(shop.getId());
            shopDtos.add(shopDTO);
        }
        return new ResponseEntity<>(shopDtos, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Shop> getShopById(@PathVariable String id) {
        return ResponseEntity.ok(shopService.getShopById(id));
    }

    @GetMapping("/view-expenses/{shopId}")
    public ResponseEntity<?> getAllExpenseOfParticularShop(@PathVariable String shopId) {
        Shop shop = shopService.getShopById(shopId);
        if (shop != null) {
            return new ResponseEntity<>(shop.getAllExpense(), HttpStatus.OK);
        } else {
            throw new APIException("No Expense Entries Found");
        }

    }

    @GetMapping("/view-expenses")
    public ResponseEntity<PaginatedResponse<ExpenseDTO>> viewAllExpenses(
            @RequestParam(name = "shopId", required = false) String shopId,
            @RequestParam(name = "term", required = false) String term,
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY_EXPENSE_DATE, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate
    ) {
        PaginatedResponse<ExpenseDTO> invoiceDTOPaginatedResponse = shopService.searchAllExpensesByShopId(
                pageNumber, pageSize, sortBy, sortOrder, shopId, startDate, endDate, term);
        return new ResponseEntity<>(invoiceDTOPaginatedResponse, HttpStatus.OK);
    }

    @GetMapping("/all-employees")
    public ResponseEntity<List<MinimumUserDetailsDTO>> getAllEmployeeFromShops() {
        List<User> allUsers = shopService.getAllEmployeeFromShops();
        List<MinimumUserDetailsDTO> minimumUserDetailsDTO = new ArrayList<>();
        for (User user : allUsers) {
            MinimumUserDetailsDTO minimumUserDetail = new MinimumUserDetailsDTO();
            minimumUserDetail.setUserId(user.getUserId());
            minimumUserDetail.setName(user.getName());
            minimumUserDetail.setAccountCreationDate(user.getAccountCreationDate());
            minimumUserDetail.setAssignedShops(user.getAssignedShops().stream().map(shop -> new ShopDTO().builder().shopName(shop.getShopName()).shopId(shop.getId()).build()).toList());

            minimumUserDetailsDTO.add(minimumUserDetail);
        }

        return ResponseEntity.ok(minimumUserDetailsDTO);
    }

    @GetMapping("/petty-cash")
    public ResponseEntity<RefreshmentDetails> getPettyCashFromShops() {
        Shop shop = shopService.getPettyCashOfShop();
        List<Expense> allexpense = shop.getAllExpense();
        double total = allexpense.stream()
                .mapToDouble(Expense::getExpenseAmount)
                .sum();
        if (shop != null) {
            RefreshmentDetails details = new RefreshmentDetails();
            details.setAmount(shop.getPettyCash());
            details.setDescription(String.valueOf(total));

            return new ResponseEntity<>(details, HttpStatus.OK);
        }else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Shop> updateShop(@PathVariable String id, @RequestBody ShopUpdateDTO shop) {
        return ResponseEntity.ok(shopService.updateShop(id, shop));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteShop(@PathVariable String id) {
        shopService.deleteShop(id);
        return ResponseEntity.ok("Shop deleted successfully");
    }

}
