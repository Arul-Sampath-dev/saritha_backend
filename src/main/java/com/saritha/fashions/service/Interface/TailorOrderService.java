package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.tailor.OrderStatusUpdate;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;

import java.util.Optional;

public interface TailorOrderService {

    InvoiceResponse getAssignedOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    Invoice updateOrderStatus(OrderStatusUpdate status);

    Optional<Invoice> getInvoice(String invoiceId);

    InvoiceResponse getAllInvoicesOfOnlyOrderAcceptedStatus(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    MinimumUserDetailsDTO getTailorMasterProfile();

    MinimumUserDetailsDTO getTailorProfileDetails();
}
