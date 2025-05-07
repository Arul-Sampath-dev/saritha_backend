package com.saritha.fashions.service;

import com.saritha.fashions.controller.AuthController;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.shop.PettyCashDTO;
import com.saritha.fashions.payload.shop.ShopRegisterDTO;
import com.saritha.fashions.payload.shop.ShopUpdateDTO;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.security.request.SignupRequest;
import com.saritha.fashions.service.Interface.ShopService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public Shop createShop(ShopRegisterDTO shop) {

        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        Shop shopConverted = modelMapper.map(shop, Shop.class);
        shopConverted.setShopContactNumber(shop.getOwnerNumber());

        Shop shopSaved = shopRepository.save(shopConverted);

        SignupRequest signupRequest = new SignupRequest();

        // Explicitly setting the correct name field
        signupRequest.setName(shop.getOwnerName());
        signupRequest.setPassword(shop.getPassword());
        signupRequest.setAssignedShops(shopSaved.getId());
        signupRequest.setPhone(shop.getOwnerNumber());
        signupRequest.setEmail(shop.getOwnerEmailId());
        Set<String> roles = new HashSet<>();
        roles.add("shopOwner");
        signupRequest.setRoles(roles);
        signupRequest.setSalary(shop.getSalary());
        signupRequest.setAddress(shop.getShopAddress());

        try {
            authController.registerUser(signupRequest);
        } catch (Exception e) {
            throw new APIException("User can't be created during shop creation");
        }

        return shopSaved;
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
    public Shop updateShop(String id, ShopUpdateDTO updatedFields) {
//        Optional<Shop> existingShop = shopRepository.findById(id);
//        if (existingShop.isPresent()) {
//            Shop shop = existingShop.get();
//            shop.setShopName(updatedShop.getShopName());
//            shop.setOwnerName(updatedShop.getOwnerName());
//            shop.setShopAddress(updatedShop.getShopAddress());
//            shop.setShopContactNumber(updatedShop.getShopContactNumber());
//            shop.setGstNumber(updatedShop.getGstNumber());
//            shop.setShopType(updatedShop.getShopType());
//            return shopRepository.save(shop);
//        }
        return shopRepository.findById(id).map(existingShop -> {
            modelMapper.map(updatedFields, existingShop);
            return shopRepository.save(existingShop);
        }).orElse(null);
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

    @Override
    public Shop getPettyCashOfShop() {
        User user = authUtil.loggedInUser();
        String shopId = user.getAssignedShops().get(0).getId();

        return shopRepository.findById(shopId).orElse(null);
    }

    @Override
    public Shop setPettyCashToShop(double amount, String shopId) {
        Shop shop = shopRepository.findById(shopId).orElse(null);

        if (shop != null) {
            double previousPettyCashAmount = shop.getPettyCash();
            shop.setPettyCash(previousPettyCashAmount+amount);

            PettyCashDTO pettyCashDTO = new PettyCashDTO();
            pettyCashDTO.setAddingDate(DateTimeUtils.getNowInIST());
            pettyCashDTO.setAmount(amount);
            pettyCashDTO.setAddedBy(authUtil.loggedInUserId());

            List<PettyCashDTO> pettyCashDTOS = shop.getAllPettyCashDetails();
            pettyCashDTOS.add(pettyCashDTO);
            shop.setAllPettyCashDetails(pettyCashDTOS);

            return shopRepository.save(shop);
        } else {
            throw new APIException("Shop Not Found");
        }
    }
}
