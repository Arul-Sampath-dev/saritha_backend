package com.saritha.fashions.service;

import com.saritha.fashions.config.AppConfig;
import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.config.OrderStatus;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.items.Items;
import com.saritha.fashions.payload.shop.ShopDTO;
import com.saritha.fashions.payload.tailor.OrderStatusUpdate;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.service.Interface.EmployeeActivityService;
import com.saritha.fashions.service.Interface.ShopAssistantService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ShopAssistantServiceImpl implements ShopAssistantService {

    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private AuthUtil authUtil;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private EmployeeActivityService employeeActivityService;

    @Override
    public InvoiceResponse getAssignedOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();

        // Set default sorting if not provided
        if (sortBy == null || sortBy.isEmpty()) {
            sortBy = "invoiceDate";
        }

        // Convert sort direction (1 for ascending, -1 for descending)
        int sortDirection = sortOrder.equalsIgnoreCase("desc") ? 1 : -1;

        // Calculate skip value
        long skip = (long) pageNumber * pageSize;

        // Get total count
        long total = invoiceRepository.countAssignedInvoicesExcludingDeliveredForShopAssistant(user.getUserId());

        // Get paginated and sorted results
        List<Invoice> invoices = invoiceRepository.findAssignedInvoicesExcludingDeliveredForShopAssistant(
                user.getUserId(),
                sortBy,
                sortDirection,
                skip,
                pageSize);

        if (invoices.isEmpty()) {
            throw new APIException("No assigned orders found (excluding delivered orders)");
        }

        // Calculate pagination metadata
        int totalPages = (int) Math.ceil((double) total / pageSize);
        boolean isLastPage = (skip + pageSize) >= total;

        return new InvoiceResponse(
                invoices,
                pageNumber,
                pageSize,
                total,
                totalPages,
                isLastPage
        );
    }

//    @Override
//    public InvoiceResponse getAssignedOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
//        User user = authUtil.loggedInUser();
//
//        long skip = (long) pageNumber * pageSize;
//        long total = invoiceRepository.countInvoicesWithTailorItems(user.getUserId());
//
//        List<Invoice> invoices = invoiceRepository.findInvoicesWithTailorItemsPaginated(
//                user.getUserId(), skip, pageSize);
//
//        if (invoices.isEmpty()) {
//            throw new APIException("No items assigned to you found in any invoice");
//        }
//
//        return new InvoiceResponse(
//                invoices,
//                pageNumber,
//                pageSize,
//                total,
//                (int) Math.ceil((double) total / pageSize),
//                (skip + pageSize) >= total
//        );
//    }

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
        boolean isShopAssistant = user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.SHOP_ASSISTANT);

        // 3. Find and update the specific item
        boolean itemUpdated = false;
        for (Items item : invoice.getItems()) {
            if (item.getItemUniqueId().equals(status.getItemUniqueIds())) {
                // Validate tailor assignment
                if (!isShopAssistant && !user.getUserId().equals(item.getAssignedToWhichShopAssistant())) {
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

        if (status.getNewStatus().equals(OrderStatus.STITCHING)) {
            employeeActivityService.recordActivity(invoice.getShopId(), invoice.getInvoiceId(), invoice.getId(),
                    user.getUserId(), user.getName(), ActivityType.ASSISTANT_COMPLETED, "Ironing completed, order completed");
        } else if (status.getNewStatus().equals(OrderStatus.IRONING)) {
            employeeActivityService.recordActivity(invoice.getShopId(), invoice.getInvoiceId(), invoice.getId(),
                    user.getUserId(), user.getName(), ActivityType.ASSISTANT_IRONING, "Ironing completed");
        } else if (status.getNewStatus().equals(OrderStatus.HEMMING)) {
            employeeActivityService.recordActivity(invoice.getShopId(), invoice.getInvoiceId(), invoice.getId(),
                    user.getUserId(), user.getName(), ActivityType.ASSISTANT_HEMMING, "Hemming completed");
        }

        // 4. Save the updated invoice
        return invoiceRepository.save(invoice);
    }

    @Override
    public Optional<Invoice> getInvoice(String invoiceId) {
        User user = authUtil.loggedInUser();
//        String shopId = user.getAssignedShops().get(0).getId();

        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();
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
    public Invoice getInvoiceWithAssignedItems(String invoiceId) {
        User user = authUtil.loggedInUser();
        String tailorId = user.getUserId();

        Optional<Invoice> invoiceOptional = invoiceRepository
                .findInvoiceWithAssignedItemsExcludingDeliveredForShopAssistant(invoiceId, tailorId);

        if (invoiceOptional.isEmpty()) {
            throw new APIException(
                    "No items assigned to you found in the searched Invoice or invoice is already delivered"
            );
        }

        // The invoice will only contain items assigned to this tailor
        return invoiceOptional.get();
    }

    @Override
    public InvoiceResponse getAllInvoicesOfOnlyOrderAcceptedStatus(Integer page, Integer size, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();
//        String shopId = user.getAssignedShops().get(0).getId();

        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

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
    public MinimumUserDetailsDTO getMyProfileDetails() {
        User userFromDb = authUtil.loggedInUser();
        MinimumUserDetailsDTO minimumUserDetailsDTO = new MinimumUserDetailsDTO();
        minimumUserDetailsDTO.setName(userFromDb.getName());
        minimumUserDetailsDTO.setAssignedShops(userFromDb.getAssignedShops().stream().map(shop -> modelMapper.map(shop, ShopDTO.class)).toList());

        return minimumUserDetailsDTO;
    }

}

