package com.saritha.fashions.service;

import com.saritha.fashions.config.OrderStatus;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.tailor.OrderStatusUpdate;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.service.Interface.TailorOrderService;
import com.saritha.fashions.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        Page<Invoice> invoicePage = invoiceRepository.findByAssignedTailorId(user.getUserId(), pageable);
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
    @Transactional
    public Invoice updateOrderStatus(OrderStatusUpdate status) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findByInvoiceId(status.getInvoiceId());

        if (optionalInvoice.isEmpty()) {
            throw new APIException("Order not found");
        }

        Invoice invoice = optionalInvoice.get();
        User user = authUtil.loggedInUser();
        String tailorId = user.getUserId();

        boolean isTailorMaster = user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.TAILOR_MASTER);

        if (!tailorId.equals(invoice.getAssignedTailorId()) && !isTailorMaster) {
            throw new APIException("You are not assigned to this order");
        }

        invoice.setOrderStatus(status.getNewStatus());
        return invoiceRepository.save(invoice);
    }

    @Override
    public Optional<Invoice> getInvoice(String invoiceId) {
        User user = authUtil.loggedInUser();
        String shopId = user.getAssignedShops().get(0).getId();
        Optional<Invoice> invoice = invoiceRepository.findByInvoiceId(invoiceId);
        if (invoice.isPresent()) {

            if (!shopId.equals(invoice.get().getShopId())) {
                throw new APIException("You are not allowed to view or edit this order");
            }

            if (!invoice.get().getAssignedTailorId().equals(user.getUserId())) {
                throw new APIException("You are not assigned to this order");
            }

        }
        return invoice;
    }

    @Override
    public InvoiceResponse getAllInvoicesOfOnlyOrderAcceptedStatus(Integer page, Integer size, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();
        String shopId = user.getAssignedShops().get(0).getId();

        List<String> excludedItems = List.of(
                "Embroidery",
                "Lining Material",
                "Saree Fall",
                "Saree Pico",
                "Designing"
        );

        List<String> allowedStatuses = List.of(
                "ORDER_ACCEPTED"
        );

        Sort sort = sortOrder.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Invoice> invoicePage = invoiceRepository
                .findByShopIdAndItemsItemNameNotInAndOrderStatusIn(shopId, excludedItems, allowedStatuses, pageable);

        if (invoicePage.getContent().isEmpty()) {
            throw new APIException("No Invoices found");
        }

        InvoiceResponse invoiceResponse = new InvoiceResponse();
        invoiceResponse.setContent(invoicePage.getContent());
        invoiceResponse.setPageNumber(invoicePage.getNumber());
        invoiceResponse.setPageSize(invoicePage.getSize());
        invoiceResponse.setTotalElements(invoicePage.getTotalElements());
        invoiceResponse.setTotalPages(invoicePage.getTotalPages());
        invoiceResponse.setLastPage(invoicePage.isLast());

        return invoiceResponse;
    }

}

