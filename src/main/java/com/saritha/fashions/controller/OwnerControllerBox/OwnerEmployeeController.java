package com.saritha.fashions.controller.OwnerControllerBox;

import com.saritha.fashions.payload.owner.UserDtoForOwner;
import com.saritha.fashions.service.Interface.OwnerService;
import com.saritha.fashions.service.Interface.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/owner/employee")
public class OwnerEmployeeController {

    @Autowired
    private ShopService shopService;

    @Autowired
    private OwnerService ownerService;


    @GetMapping("/{shopId}/all-employee")
    public ResponseEntity<List<UserDtoForOwner>> getAllEmployeeFromParticularShop(@PathVariable String shopId){
        List<UserDtoForOwner> userDtoForOwners = ownerService.getAllEmployeeListByShopId(shopId);
        return new ResponseEntity<>(userDtoForOwners, HttpStatus.OK);
    }

    @GetMapping("/{shopId}/find-user")
    public ResponseEntity<List<UserDtoForOwner>> getUserByUserId(@PathVariable String shopId, @RequestParam String term) {
        List<UserDtoForOwner> userDtoForOwner = ownerService.getUserByUserId(shopId, term);
        return new ResponseEntity<>(userDtoForOwner, HttpStatus.OK);
    }

}
