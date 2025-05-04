package com.saritha.fashions.payload.invoice;


import com.saritha.fashions.config.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderStatusUpdateRequest {

    private OrderStatus status;

}
