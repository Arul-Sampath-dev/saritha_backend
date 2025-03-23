package com.saritha.fashions.controller;

import com.saritha.fashions.model.Order;
import com.saritha.fashions.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/v1/api/notifications")
public class NotificationController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OrderRepository orderRepository;

    // Notify Tailor & Shop Owner of Order Status Updates
    @PatchMapping("/order-status/{id}")
    public void sendOrderUpdate(@PathVariable String id, @RequestParam String status) {
        Optional<Order> order = orderRepository.findById(id);
        if (order.isPresent()) {
            Order existingOrder = order.get();
            existingOrder.setStatus(status);
            orderRepository.save(existingOrder);

            messagingTemplate.convertAndSend("/topic/order-updates", existingOrder);
        }
    }
}
