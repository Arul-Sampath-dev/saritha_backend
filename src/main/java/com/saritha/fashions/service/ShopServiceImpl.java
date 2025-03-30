package com.saritha.fashions.service;

import com.saritha.fashions.model.Shop;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.service.Interface.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    @Autowired
    private ShopRepository shopRepository;

    @Override
    public Shop createShop(Shop shop) {
        return shopRepository.save(shop);
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
            shop.setAddress(updatedShop.getAddress());
            shop.setContactNumber(updatedShop.getContactNumber());
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
}
