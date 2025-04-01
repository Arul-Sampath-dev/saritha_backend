package com.saritha.fashions.controller;

import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.ShopRegisterDTO;
import com.saritha.fashions.service.Interface.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/shops")
@RequiredArgsConstructor
public class ShopController {

    @Autowired
    private ShopService shopService;

    @PostMapping
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

}
