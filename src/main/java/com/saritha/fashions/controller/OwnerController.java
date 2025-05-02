package com.saritha.fashions.controller;

import com.saritha.fashions.model.Expense;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.payload.expense.RefreshmentDetails;
import com.saritha.fashions.payload.shop.AllShopDTO;
import com.saritha.fashions.payload.shop.ShopRegisterDTO;
import com.saritha.fashions.service.Interface.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v1/api/owner")
public class OwnerController {

    @Autowired
    private ShopService shopService;

    @PostMapping
    @PreAuthorize("hasAuthority('OWNER')")
    public ResponseEntity<Shop> createShop(@RequestBody ShopRegisterDTO shop) {
        return ResponseEntity.ok(shopService.createShop(shop));
    }


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

    @GetMapping("/add-petty-cash/{amount}/{shopId}")
    @PreAuthorize("hasAuthority('OWNER')")
    public ResponseEntity<?> addPettyCashToShop(@PathVariable double amount, @PathVariable String shopId) {

        Shop shop = shopService.setPettyCashToShop(amount, shopId);

        if (shop != null) {
            RefreshmentDetails details = new RefreshmentDetails();
            details.setAmount(shop.getPettyCash());
            return new ResponseEntity<>(details, HttpStatus.OK);
        }else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

    }
}
