package com.saritha.fashions.repository;

import com.saritha.fashions.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByShopId(String shopId);
}
