package com.saritha.fashions.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.repository.UserRepository;
import com.saritha.fashions.service.Interface.TailorAssignmentService;
import com.saritha.fashions.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TailorAssignmentServiceImpl implements TailorAssignmentService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public Invoice assignTailorToInvoice(String invoiceId, String tailorId) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findById(invoiceId);
        if (optionalInvoice.isPresent()) {
            Invoice invoice = optionalInvoice.get();
            invoice.setAssignedTailorId(tailorId);
            invoice.setAssignmentStatus("ASSIGNED");
            return invoiceRepository.save(invoice);
        } else {
            throw new RuntimeException("Invoice not found with ID: " + invoiceId);
        }
    }

    @Override
    public Invoice unassignTailorFromInvoice(String invoiceId) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findById(invoiceId);
        if (optionalInvoice.isPresent()) {
            Invoice invoice = optionalInvoice.get();
            invoice.setAssignedTailorId(null);
            invoice.setAssignmentStatus("UNASSIGNED");
            return invoiceRepository.save(invoice);
        } else {
            throw new RuntimeException("Invoice not found with ID: " + invoiceId);
        }
    }

    @Override
    public List<Invoice> getInvoicesForShop() {
        User user = authUtil.loggedInUser();
        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();
        return invoiceRepository.findByShopId(shopId);
    }

    @Override
    public InvoiceResponse getInvoicesAssignedToTailor(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase(sortBy)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        Page<Invoice> invoicePage = invoiceRepository.findByShopId(shopId, pageable);
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
    public List<User> fetchAllTailorFromParticularShop() {

        User loggedInUser = authUtil.loggedInUser();
//        String shopId = loggedInUser.getAssignedShops().get(0).getId();

        String userId = loggedInUser.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        if (shopId == null || shopId.isBlank()) {
            throw new APIException("Shop ID must not be null or blank.");
        }

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new APIException("Shop not found with ID: " + shopId));

        List<User> employees = shop.getEmployees();

        if (employees == null || employees.isEmpty()) {
            throw new APIException("No employees found for the shop with ID: " + shopId);
        }

//        List<User> userList = new ArrayList<>();
//
//        for (User user : employees) {
//            Set<Role> role = user.getRoles();
//            for (Role roles : role) {
//                if (roles.getRoleName().equals(AppRole.TAILOR.toString())) {
//                    userList.add(user);
//                }
//            }
//        }

        List<User> userList = employees.stream()
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> role.getRoleName().equals(AppRole.TAILOR))
                )
                .collect(Collectors.toList());

        return userList;
    }

    @Override
    public List<User> fetchAllShopAssistantFromParticularShop() {
        User loggedInUser = authUtil.loggedInUser();
//        String shopId = loggedInUser.getAssignedShops().get(0).getId();

        String userId = loggedInUser.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        if (shopId == null || shopId.isBlank()) {
            throw new APIException("Shop ID must not be null or blank.");
        }

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new APIException("Shop not found with ID: " + shopId));

        List<User> employees = shop.getEmployees();

        if (employees == null || employees.isEmpty()) {
            throw new APIException("No employees found for the shop with ID: " + shopId);
        }

        List<User> userList = employees.stream()
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> role.getRoleName().equals(AppRole.SHOP_ASSISTANT))
                )
                .collect(Collectors.toList());

        return userList;
    }

}

