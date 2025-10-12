package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.tailor.OrderStatusUpdate;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;

import java.util.Date;
import java.util.Optional;

public interface ShopAssistantService {

//    InvoiceResponse getAssignedOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    Invoice updateOrderStatus(OrderStatusUpdate status);

    Optional<Invoice> getInvoice(String invoiceId);
    Invoice getInvoiceWithAssignedItems(String invoiceId);

    InvoiceResponse getAllInvoicesOfOnlyOrderAcceptedStatus(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    MinimumUserDetailsDTO getMyProfileDetails();

    Invoice updateOrderStatusByTailor(OrderStatusUpdate statusUpdate);

    PaginatedResponse getAllInvoiceFromParticularShopForAssistant(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, Date startDateConverted, Date endDateConverted);
}
