package com.saritha.fashions.controller.OwnerControllerBox;


import com.saritha.fashions.model.Expense;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.payload.expense.RefreshmentDetails;
import com.saritha.fashions.payload.shop.AllShopDTO;
import com.saritha.fashions.payload.shop.ShopRegisterDTO;
import com.saritha.fashions.service.Interface.OwnerService;
import com.saritha.fashions.service.Interface.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import static com.saritha.fashions.config.OrderStatus.ORDER_DELIVERED;

@RestController
@RequestMapping("/v1/api/owner/shop")
public class OwnerShopController {

    @Autowired
    private ShopService shopService;

    @Autowired
    private OwnerService ownerService;

    @GetMapping
    public ResponseEntity<List<AllShopDTO>> getAllShops() {

        List<AllShopDTO> allShopDTOS = shopService.findAllShopSummaries();
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

    @PostMapping
    @PreAuthorize("hasAuthority('OWNER')")
    public ResponseEntity<Shop> createShop(@RequestBody ShopRegisterDTO shop) {
        return ResponseEntity.ok(shopService.createShop(shop));
    }
}
