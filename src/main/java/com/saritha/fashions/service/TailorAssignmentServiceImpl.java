package com.saritha.fashions.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.repository.UserRepository;
import com.saritha.fashions.service.Interface.TailorAssignmentService;
import com.saritha.fashions.util.AuthUtil;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TailorAssignmentServiceImpl implements TailorAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(TailorAssignmentServiceImpl.class);

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
    public List<MinimumUserDetailsDTO> fetchAllTailorFromParticularShop() {
        long start = System.currentTimeMillis();

        String tailorMasterId = authUtil.loggedInUserId();

        // Step 1: Find the shop by tailorMasterId
        Query shopQuery = new Query(Criteria.where("employeeIds").is(tailorMasterId));
        shopQuery.fields().include("employeeIds");
        Shop shop = mongoTemplate.findOne(shopQuery, Shop.class, "shops");

        if (shop == null) {
            throw new APIException("No shop found for tailor master " + tailorMasterId);
        }

        // Step 2: Filter employeeIds (exclude the master himself)
        List<String> employeeIds = shop.getEmployeeIds()
                .stream()
                .filter(id -> !id.equals(tailorMasterId))
                .toList();

        if (employeeIds.isEmpty()) {
            throw new APIException("No tailors found for this shop.");
        }

        long shopFetchTime = System.currentTimeMillis();
        logger.debug("Shop fetch took: {} ms", (shopFetchTime - start));

        // Step 3: Fetch users with role = TAILOR and only project necessary fields
        Query userQuery = new Query(Criteria.where("_id").in(employeeIds)
                .and("roles.roleName").is(AppRole.TAILOR));
        userQuery.fields().include("_id").include("name");

        List<User> tailors = mongoTemplate.find(userQuery, User.class, "users");

        long userFetchTime = System.currentTimeMillis();
        logger.debug("Users fetch took: {} ms", (userFetchTime - shopFetchTime));

        // Step 4: Map to DTOs
        return tailors.stream()
                .map(u -> new MinimumUserDetailsDTO(u.getUserId(), u.getName(), u.getAccountCreationDate()))
                .toList();
    }

    @Override
    public List<MinimumUserDetailsDTO> fetchAllShopAssistantFromParticularShop() {
        long start = System.currentTimeMillis();

        String tailorId = authUtil.loggedInUserId();

        // Step 1: Find the shop by tailorMasterId
        Query shopQuery = new Query(Criteria.where("employeeIds").is(tailorId));
        shopQuery.fields().include("employeeIds");
        Shop shop = mongoTemplate.findOne(shopQuery, Shop.class, "shops");

        if (shop == null) {
            throw new APIException("No shop found for tailor " + tailorId);
        }

        // Step 2: Filter employeeIds (exclude the master himself)
        List<String> employeeIds = shop.getEmployeeIds()
                .stream()
                .filter(id -> !id.equals(tailorId))
                .toList();

        if (employeeIds.isEmpty()) {
            throw new APIException("No shop assistant found for this shop.");
        }

        long shopFetchTime = System.currentTimeMillis();
        logger.debug("Shop fetch took: {} ms", (shopFetchTime - start));

        // Step 3: Fetch users with role = SHOP_ASSISTANT and only project necessary fields
        Query userQuery = new Query(Criteria.where("_id").in(employeeIds)
                .and("roles.roleName").is(AppRole.SHOP_ASSISTANT));
        userQuery.fields().include("_id").include("name");

        List<User> tailors = mongoTemplate.find(userQuery, User.class, "users");

        long userFetchTime = System.currentTimeMillis();
        logger.debug("Users fetch took: {} ms", (userFetchTime - shopFetchTime));

        // Step 4: Map to DTOs
        return tailors.stream()
                .map(u -> new MinimumUserDetailsDTO(u.getUserId(), u.getName(), u.getAccountCreationDate()))
                .toList();
    }
}

