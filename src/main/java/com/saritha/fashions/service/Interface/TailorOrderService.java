package com.saritha.fashions.service.Interface;

import com.saritha.fashions.config.OrderStatus;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.payload.invoice.InvoiceResponse;

import java.util.List;

public interface TailorOrderService {

    InvoiceResponse getAssignedOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    Invoice updateOrderStatus(String tailorId, String invoiceId, OrderStatus status);
}
