package com.saritha.fashions.service;

import com.saritha.fashions.config.OrderStatus;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.service.Interface.TailorOrderService;
import com.saritha.fashions.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

@Service
public class TailorOrderServiceImpl implements TailorOrderService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AuthUtil authUtil;

    @Override
    public InvoiceResponse getAssignedOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase(sortBy)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Invoice> invoicePage = invoiceRepository.findByShopId(user.getAssignedShops().get(0).getId(), pageable);
        List<Invoice> invoiceList = invoicePage.getContent();
        if (invoiceList.isEmpty()) {
            throw new APIException("No Invoices found");
        }

//        List<Invoice> invoices = invoiceList.stream()
//                .map(invoice -> modelMapper.map(invoice, Invoice.class))
//                .toList();
        InvoiceResponse invoiceResponse = new InvoiceResponse();
        invoiceResponse.setContent(invoiceList);
        invoiceResponse.setPageNumber(invoicePage.getNumber());
        invoiceResponse.setPageSize(invoicePage.getSize());
        invoiceResponse.setTotalElements(invoicePage.getTotalElements());
        invoiceResponse.setTotalPages(invoicePage.getTotalPages());
        invoiceResponse.setLastPage(invoicePage.isLast());
        return invoiceResponse;
    }

    @Override
    public Invoice updateOrderStatus(String tailorId, String invoiceId, OrderStatus status) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findById(invoiceId);

        if (optionalInvoice.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Order not found");
        }

        Invoice invoice = optionalInvoice.get();

        if (!tailorId.equals(invoice.getAssignedTailorId())) {
            throw new ResponseStatusException(FORBIDDEN, "You are not assigned to this order");
        }

        invoice.setOrderStatus(status.toString());
        return invoiceRepository.save(invoice);
    }
}

