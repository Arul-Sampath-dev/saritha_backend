package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Order;

import java.util.List;

public interface OrderService {

    boolean createOrder(Order order);

    List<Order> getOrdersByShop(String shopId);

    boolean updateOrderStatus(String id, String status);
}
