package com.saritha.fashions.service;

import com.saritha.fashions.model.Order;
import com.saritha.fashions.repository.OrderRepository;
import com.saritha.fashions.service.Interface.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderServiceImpl implements OrderService {


    @Autowired
    private OrderRepository orderRepository;

    @Override
    public boolean createOrder(Order order) {
        order.setStatus("Pending");
        try {
            orderRepository.save(order);
            return true;
        } catch (Exception e) {
            return false;
        }

    }

    @Override
    public List<Order> getOrdersByShop(String shopId) {
       return orderRepository.findByShopId(shopId);
    }

    @Override
    public boolean updateOrderStatus(String id, String status) {
        Optional<Order> order = orderRepository.findById(id);
        if (order.isPresent()) {
            Order existingOrder = order.get();
            existingOrder.setStatus(status);
            try {
                orderRepository.save(existingOrder);
                return true;
            }catch (Exception e) {
                return false;
            }
        }else {
            return false;
        }
    }
}
