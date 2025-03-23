package com.saritha.fashions.controller;

import com.saritha.fashions.model.Order;
import com.saritha.fashions.service.Interface.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    // Create a New Order
    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@RequestBody Order order) {
        boolean createdOrNot = orderService.createOrder(order);
        if (createdOrNot) {
            return new ResponseEntity<>("Order created", HttpStatus.CREATED);
        }else {
            return ResponseEntity.ok("Order created successfully!");
        }
    }

    // Fetch Orders by Shop ID
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<Order>> getOrdersByShop(@PathVariable String shopId) {
        List<Order> orders = orderService.getOrdersByShop(shopId);
        return ResponseEntity.ok(orders);
    }

    // Update Order Status
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String id, @RequestParam String status) {

        boolean savedOrNot = orderService.updateOrderStatus(id,status);
        if (savedOrNot) {
            return new ResponseEntity<>("Order updated successfully!", HttpStatus.OK);
        }else {
            return ResponseEntity.badRequest().body("Order not found");
        }
    }
}
