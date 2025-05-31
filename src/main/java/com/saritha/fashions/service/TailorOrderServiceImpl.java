package com.saritha.fashions.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.items.Items;
import com.saritha.fashions.payload.shop.ShopDTO;
import com.saritha.fashions.payload.tailor.OrderStatusUpdate;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.service.Interface.TailorOrderService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TailorOrderServiceImpl implements TailorOrderService {

    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private AuthUtil authUtil;
    @Autowired
    private ModelMapper modelMapper;

    @Override
    public InvoiceResponse getAssignedOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();

        long skip = (long) pageNumber * pageSize;
        long total = invoiceRepository.countInvoicesWithTailorItems(user.getUserId());

        List<Invoice> invoices = invoiceRepository.findInvoicesWithTailorItemsPaginated(
                user.getUserId(), skip, pageSize);

        if (invoices.isEmpty()) {
            throw new APIException("No items assigned to you found in any invoice");
        }

        return new InvoiceResponse(
                invoices,
                pageNumber,
                pageSize,
                total,
                (int) Math.ceil((double) total / pageSize),
                (skip + pageSize) >= total
        );
    }

//    @Override
//    public InvoiceResponse getAssignedOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
//        User user = authUtil.loggedInUser();
//        Sort sortByAndOrder = sortOrder.equalsIgnoreCase(sortBy)
//                ? Sort.by(sortBy).ascending()
//                : Sort.by(sortBy).descending();
//
//        Pageable pageable = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
//        Page<Invoice> invoicePage = invoiceRepository.findByAssignedTailorId(user.getUserId(), pageable);
//        List<Invoice> invoiceList = invoicePage.getContent();
//        if (invoiceList.isEmpty()) {
//            throw new APIException("No Invoices found");
//        }
//
////        List<Invoice> invoices = invoiceList.stream()
////                .map(invoice -> modelMapper.map(invoice, Invoice.class))
////                .toList();
//        InvoiceResponse invoiceResponse = new InvoiceResponse();
//        invoiceResponse.setContent(invoiceList);
//        invoiceResponse.setPageNumber(invoicePage.getNumber());
//        invoiceResponse.setPageSize(invoicePage.getSize());
//        invoiceResponse.setTotalElements(invoicePage.getTotalElements());
//        invoiceResponse.setTotalPages(invoicePage.getTotalPages());
//        invoiceResponse.setLastPage(invoicePage.isLast());
//        return invoiceResponse;
//    }

    @Override
    @Transactional
    public Invoice updateOrderStatus(OrderStatusUpdate status) {
        // 1. Find the invoice
        Invoice invoice = invoiceRepository.findByInvoiceId(status.getInvoiceId())
                .orElseThrow(() -> new APIException("Invoice not found"));

        // 2. Get authenticated user and validate permissions
        User user = authUtil.loggedInUser();
        boolean isTailorMaster = user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.TAILOR_MASTER);

        // 3. Find and update the specific item
        boolean itemUpdated = false;
        for (Items item : invoice.getItems()) {
            if (item.getItemUniqueId().equals(status.getItemUniqueIds())) {
                // Validate tailor assignment
                if (!isTailorMaster && !user.getUserId().equals(item.getAssignedTo())) {
                    throw new APIException("You are not assigned to this item");
                }

                // Update item status
                item.setItemOrderStatus(status.getNewStatus());

                // Add to history
                item.getAssignmentHistory().add(new Items.AssignmentHistory(
                        user.getUserId(),
                        status.getNewStatus(),
                        DateTimeUtils.getNowInIST()
                ));

                itemUpdated = true;
                break;
            }
        }

        if (!itemUpdated) {
            throw new APIException("Item not found in invoice");
        }

        // 4. Save the updated invoice
        return invoiceRepository.save(invoice);
    }

    @Override
    @Transactional
    public Invoice updateOrderStatusByTailor(OrderStatusUpdate status) {
        // 1. Find the invoice
        Invoice invoice = invoiceRepository.findByInvoiceId(status.getInvoiceId())
                .orElseThrow(() -> new APIException("Invoice not found"));

        // 2. Get authenticated user and validate permissions
        User user = authUtil.loggedInUser();
        boolean isTailor = user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.TAILOR);

        // 3. Find and update the specific item
        boolean itemUpdated = false;
        for (Items item : invoice.getItems()) {
            if (item.getItemUniqueId().equals(status.getItemUniqueIds())) {
                // Validate tailor assignment
                if (!isTailor && !user.getUserId().equals(item.getAssignedTo())) {
                    throw new APIException("You are not assigned to this item");
                }

                // Update item status
                item.setItemOrderStatus(status.getNewStatus());

                // Add to history
                item.getAssignmentHistory().add(new Items.AssignmentHistory(
                        user.getUserId(),
                        status.getNewStatus(),
                        DateTimeUtils.getNowInIST()
                ));

                itemUpdated = true;
                break;
            }
        }

        if (!itemUpdated) {
            throw new APIException("Item not found in invoice");
        }

        // 4. Save the updated invoice
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
//                "Embroidery",
//                "Lining Material",
//                "Saree Fall",
//                "Saree Pico",
//                "Designing",
                "NoUse"
        );

        List<String> allowedStatuses = List.of(
                "ORDER_ACCEPTED",
                "CUTTING",
                "TAILOR_ASSIGNED",
                "STITCHING",
                "IRONING",
                "ORDER_COMPLETED",
                "ORDER_DELIVERED",
                "ASSIGNED",
                "UNDER_PRODUCTION"
        );

        Sort sort = sortOrder.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

//        Page<Invoice> invoicePage = invoiceRepository
//                .findByShopIdAndItemsItemNameNotInAndOrderStatusIn(shopId, excludedItems, allowedStatuses, pageable);

        Page<Invoice> invoicePage = invoiceRepository
                        .findByShopIdAndItemsItemNameNotInAndOrderStatusInWithAtLeastOneOrderAcceptedItem(shopId, excludedItems, allowedStatuses, pageable);

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

    @Override
    public MinimumUserDetailsDTO getTailorMasterProfile() {
        User userFromDb = authUtil.loggedInUser();
        MinimumUserDetailsDTO minimumUserDetailsDTO = new MinimumUserDetailsDTO();
        minimumUserDetailsDTO.setName(userFromDb.getName());
        minimumUserDetailsDTO.setAssignedShops(userFromDb.getAssignedShops().stream().map(shop -> modelMapper.map(shop, ShopDTO.class)).toList());

        return minimumUserDetailsDTO;
    }

    @Override
    public MinimumUserDetailsDTO getTailorProfileDetails() {
        User userFromDb = authUtil.loggedInUser();
        MinimumUserDetailsDTO minimumUserDetailsDTO = new MinimumUserDetailsDTO();
        minimumUserDetailsDTO.setName(userFromDb.getName());
        minimumUserDetailsDTO.setAssignedShops(userFromDb.getAssignedShops().stream().map(shop -> modelMapper.map(shop, ShopDTO.class)).toList());

        return minimumUserDetailsDTO;
    }

}

