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

    @GetMapping("/{shopId}/find-user/{userId}")
    public ResponseEntity<UserDtoForOwner> getUserByUserId(@PathVariable String shopId, @PathVariable String userId) {
        UserDtoForOwner userDtoForOwner = ownerService.getUserByUserId(shopId, userId);
        return new ResponseEntity<>(userDtoForOwner, HttpStatus.OK);
    }

//    @GetMapping("/{shopId}/all-employee")
//    public ResponseEntity<PaginatedResponse<UserDtoForOwner>> getAllEmployeeFromParticularShop(
//            @PathVariable String shopId,
//            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
//            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
//            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY_ACCOUNT_CREATION_DATE, required = false) String sortBy,
//            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
//    ) {
//        PaginatedResponse<UserDtoForOwner> employee = ownerService.getAllEmployeeByShopId(pageNumber, pageSize, sortBy, sortOrder, shopId);
//        return new ResponseEntity<>(employee, HttpStatus.OK);
//    }
}
