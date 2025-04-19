package com.saritha.fashions.controller;

import com.saritha.fashions.model.Expense;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.RefreshmentDetails;
import com.saritha.fashions.payload.ShopRegisterDTO;
import com.saritha.fashions.service.Interface.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/shops")
@RequiredArgsConstructor
public class ShopController {

    @Autowired
    private ShopService shopService;

    @PostMapping
    @PreAuthorize("hasAuthority('OWNER')")
    public ResponseEntity<Shop> createShop(@RequestBody ShopRegisterDTO shop) {
        return ResponseEntity.ok(shopService.createShop(shop));
    }

    @GetMapping
    public ResponseEntity<List<Shop>> getAllShops() {
        return ResponseEntity.ok(shopService.getAllShops());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Shop> getShopById(@PathVariable String id) {
        return ResponseEntity.ok(shopService.getShopById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Shop> updateShop(@PathVariable String id, @RequestBody Shop shop) {
        return ResponseEntity.ok(shopService.updateShop(id, shop));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteShop(@PathVariable String id) {
        shopService.deleteShop(id);
        return ResponseEntity.ok("Shop deleted successfully");
    }

    @GetMapping("/all-employees")
    public ResponseEntity<List<User>> getAllEmployeeFromShops() {
        return ResponseEntity.ok(shopService.getAllEmployeeFromShops());
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
