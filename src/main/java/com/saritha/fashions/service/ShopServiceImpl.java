package com.saritha.fashions.service;

import com.saritha.fashions.controller.AuthController;
import com.saritha.fashions.model.Role;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.ShopRegisterDTO;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.security.request.SignupRequest;
import com.saritha.fashions.security.response.MessageResponse;
import com.saritha.fashions.service.Interface.ShopService;
import com.saritha.fashions.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private AuthController authController;

    @Autowired
    private AuthUtil authUtil;

    @Override
    public Shop createShop(ShopRegisterDTO shop) {
        System.out.println("Shop Register before modelmapper" + shop);

        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        Shop shopConverted = modelMapper.map(shop, Shop.class);
        shopConverted.setShopContactNumber(shop.getOwnerNumber());

        Shop savedShop = shopRepository.save(shopConverted);

        SignupRequest signupRequest = new SignupRequest();

        // Explicitly setting the correct name field
        signupRequest.setName(shop.getOwnerName());
        signupRequest.setPassword(shop.getPassword());
        signupRequest.setAssignedShops(shop.getShopName());
        signupRequest.setPhone(shop.getOwnerNumber());
        signupRequest.setEmail(shop.getOwnerEmailId());
        Set<String> roles = new HashSet<>();
        roles.add("shopOwner");
        signupRequest.setRoles(roles);
        signupRequest.setSalary(shop.getSalary());
        signupRequest.setAddress(shop.getShopAddress());

        System.out.println("signupRequest before saving " + signupRequest);
        authController.registerUser(signupRequest);

        return savedShop;
    }

    @Override
    public List<Shop> getAllShops() {
        return shopRepository.findAll();
    }

    @Override
    public Shop getShopById(String id) {
        return shopRepository.findById(id).orElse(null);
    }

    @Override
    public Shop updateShop(String id, Shop updatedShop) {
        Optional<Shop> existingShop = shopRepository.findById(id);
        if (existingShop.isPresent()) {
            Shop shop = existingShop.get();
            shop.setShopName(updatedShop.getShopName());
            shop.setOwnerName(updatedShop.getOwnerName());
            shop.setShopAddress(updatedShop.getShopAddress());
            shop.setShopContactNumber(updatedShop.getShopContactNumber());
            shop.setGstNumber(updatedShop.getGstNumber());
            shop.setShopType(updatedShop.getShopType());
            return shopRepository.save(shop);
        }
        return null;
    }

    @Override
    public void deleteShop(String id) {
        shopRepository.deleteById(id);
    }

    @Override
    public List<User> getAllEmployeeFromShops() {
        User user = authUtil.loggedInUser();
        String shopId = user.getAssignedShops().get(0).getId();
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop != null) {
            return shop.getEmployees() != null ? shop.getEmployees() : new ArrayList<User>();
        }else {
            return null;
        }
    }
}
