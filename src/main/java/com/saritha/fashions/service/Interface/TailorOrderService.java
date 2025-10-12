package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.tailor.OrderStatusUpdate;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;

import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;

public interface TailorOrderService {

//    InvoiceResponse getAssignedOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    Invoice updateOrderStatus(OrderStatusUpdate status);
    Invoice updateOrderStatusByTailor(OrderStatusUpdate status);

    Optional<Invoice> getInvoice(String invoiceId);
    Invoice getInvoiceWithAssignedItems(String invoiceId);

//    InvoiceResponse getAllInvoicesOfOnlyOrderAcceptedStatus(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    MinimumUserDetailsDTO getTailorMasterProfile();

    MinimumUserDetailsDTO getTailorProfileDetails();

    //        Testing the other mechanism for sending and filtering shop's employee datas

    PaginatedResponse getAllInvoiceFromParticularShopForTailor(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, Date startDate, Date endDate);


    InvoiceResponse getAllInvoicesOfOnlyOrderAcceptedStatus(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, LocalDate startDate, LocalDate endDate);
}
