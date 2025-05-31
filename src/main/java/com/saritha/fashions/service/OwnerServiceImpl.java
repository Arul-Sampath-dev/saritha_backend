package com.saritha.fashions.service;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.config.OrderStatus;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.items.Items;
import com.saritha.fashions.payload.owner.UserDtoForOwner;
import com.saritha.fashions.payload.receipt.ReceiptDTO;
import com.saritha.fashions.payload.receipt.ReceiptResponse;
import com.saritha.fashions.payload.shop.PettyCashDTO;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.repository.UserRepository;
import com.saritha.fashions.service.Interface.OwnerService;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.jetbrains.annotations.NotNull;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.saritha.fashions.config.OrderStatus.ORDER_DELIVERED;

@Service
public class OwnerServiceImpl implements OwnerService {

    private final ShopRepository shopRepository;
    private final ReceiptRepository receiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final ModelMapper modelMapper;
    private final ReceiptService receiptService;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public OwnerServiceImpl(ShopRepository shopRepository,
                            ModelMapper modelMapper,
                            ReceiptRepository receiptRepository,
                            InvoiceRepository invoiceRepository,
                            ReceiptService receiptService,
                            UserRepository userRepository,
                            MongoTemplate mongoTemplate)
    {
        this.shopRepository = shopRepository;
        this.modelMapper = modelMapper;
        this.receiptRepository = receiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.receiptService = receiptService;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public byte[] downloadReceipt(ReceiptDTO receiptDTO) throws IOException {
//        Optional<Invoice> optionalInvoice = invoiceRepository.findById(receiptDTO.getInvoiceId());
        Optional<Receipt> optionalReceipt = receiptRepository.findById(receiptDTO.getId());
        if (optionalReceipt.isPresent()) {
//            byte[] byteData = receiptService.generateReceiptDocument(optionalInvoice.get(), 2).getBody();
            byte[] byteData = receiptService.getReceiptPdfMethod(optionalReceipt.get()).getBody();
            return byteData;
        } else {
            throw new APIException("Invoice Not Found for the Receipt " + receiptDTO.getReceiptNo());
        }
    }

    @Override
    public ReceiptDTO getReceiptByReceiptId(String shopId, String receiptId) {
        Optional<Shop> optionalShop = shopRepository.findById(shopId);
        if (optionalShop.isPresent()) {
            List<Receipt> allReceipts = optionalShop.get().getAllReceipts();
            for (Receipt receipt : allReceipts) {
                if (receipt.getReceiptNo().equals(receiptId)) {
                    return modelMapper.map(receipt, ReceiptDTO.class);
                }
            }
            throw new APIException("Receipt Not found in " + optionalShop.get().getShopName() + " Shop");
        }else {
            throw new APIException("Shop Not Found, Please contact Developer");
        }
    }

//    ALl invoice related methods starts from here ========================================

    @Override
    public InvoiceDTO getInvoiceByInvoiceId(String shopId, String invoiceId) {
        Optional<Shop> optionalShop = shopRepository.findById(shopId);
        if (optionalShop.isPresent()) {
            List<Invoice> allInvoices = optionalShop.get().getAllInvoice();
            for (Invoice invoice : allInvoices) {
                if (invoice.getInvoiceId().equals(invoiceId)) {
                    return modelMapper.map(invoice, InvoiceDTO.class);
                }
            }
            throw new APIException("Invoice Not found in " + optionalShop.get().getShopName() + " Shop");
        }else {
            throw new APIException("Shop Not Found, Please contact Developer");
        }
    }

    @Override
    public byte[] downloadInvoice(InvoiceDTO invoiceDTO) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findByInvoiceId(invoiceDTO.getInvoiceId());
        if (optionalInvoice.isPresent()) {
            byte[] byteData = receiptService.getInvoicePdfMethod(optionalInvoice.get()).getBody();
            return byteData;
        } else {
            throw new APIException("Invoice Not Found for the Receipt " + invoiceDTO.getReceiptId());
        }
    }

    @Override
    public List<UserDtoForOwner> getAllEmployeeListByShopId(String shopId) {
        Optional<Shop> shopOptional = shopRepository.findById(shopId);

        if (shopOptional.isEmpty()) {
            throw new APIException("Shop Not Found");
        }

        List<User> userList = shopOptional.get().getEmployees();
        List<UserDtoForOwner> userDtoForOwners = userList.stream().map(user -> modelMapper.map(user, UserDtoForOwner.class)).toList();
        System.out.println("UserDtos For Owner" + userDtoForOwners);
        return userDtoForOwners;


    }

    @Override
    public List<UserDtoForOwner> getUserByUserId(String shopId, String userId) {
        Optional<Shop> shop = shopRepository.findById(shopId);
        if (shop.isEmpty()) {
            throw new APIException("Shop Not Found");
        }

        // Create case-insensitive regex pattern for partial matching
        Pattern pattern = Pattern.compile(".*" + Pattern.quote(userId) + ".*", Pattern.CASE_INSENSITIVE);

        List<UserDtoForOwner> matchingUsers = shop.get().getEmployees().stream()
                .filter(user -> pattern.matcher(user.getUserId()).matches())
                .map(user -> modelMapper.map(user, UserDtoForOwner.class))
                .collect(Collectors.toList());

        if (matchingUsers.isEmpty()) {
            throw new APIException("User Not found in " + shop.get().getShopName() + " Shop");
        }

        return matchingUsers;

//        List<User> userList = shop.get().getEmployees();
//        for (User user : userList) {
//            if (user.getUserId().equals(userId)) {
//                return modelMapper.map(user, UserDtoForOwner.class);
//            }
//        }
//
//        throw new APIException("User Not found in " + shop.get().getShopName() + " Shop");
    }

//    @Override
//    public PaginatedResponse<PettyCashDTO> getAllPettyCashByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId) {
//        Shop shop = shopRepository.findById(shopId)
//                .orElseThrow(() -> new APIException("Shop Not Found"));
//
//        List<PettyCashDTO> pettyCashDTOS = shop.getAllPettyCashDetails();
//
//        // Optional: Sort the list manually based on `sortBy` and `sortOrder`
//        pettyCashDTOS.sort((u1, u2) -> {
//            Comparator<PettyCashDTO> comparator;
//            if (sortBy.equalsIgnoreCase("addingDate")) {
//                comparator = Comparator.comparing(PettyCashDTO::getAddingDate);
//            } else {
//                comparator = Comparator.comparing(PettyCashDTO::getAmount);
//            }
//            return sortOrder.equalsIgnoreCase("asc") ? comparator.reversed().compare(u1, u2) : comparator.compare(u1, u2);
//        });
//
//        int totalElements = pettyCashDTOS.size();
//        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
//
//        int fromIndex = pageNumber * pageSize;
//        int toIndex = Math.min(fromIndex + pageSize, totalElements);
//
//        if (fromIndex >= totalElements) {
//            throw new APIException("Page number out of bounds");
//        }
//
//        List<PettyCashDTO> pagedContent = pettyCashDTOS.subList(fromIndex, toIndex).stream()
//                .map(pettycash -> modelMapper.map(pettycash, PettyCashDTO.class))
//                .collect(Collectors.toList());
//
//        return new PaginatedResponse<>(
//                pagedContent,
//                pageNumber,
//                pageSize,
//                (long) totalElements,
//                totalPages,
//                pageNumber == totalPages - 1
//        );
//    }

    @Override
    public PaginatedResponse<PettyCashDTO> searchPettyCashByAmount(String shopId, String term, Integer pageNumber, Integer pageSize) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found with ID: " + shopId));

        List<PettyCashDTO> filteredList = shop.getAllPettyCashDetails().stream()
                .filter(dto -> String.valueOf(dto.getAmount()).contains(term))
                .sorted(Comparator.comparing(PettyCashDTO::getAddingDate).reversed())
                .collect(Collectors.toList());

        int totalElements = filteredList.size();
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        List<PettyCashDTO> paginatedList = fromIndex > totalElements ? Collections.emptyList()
                : filteredList.subList(fromIndex, toIndex);

        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        boolean lastPage = pageNumber >= totalPages - 1;

        return new PaginatedResponse<>(
                paginatedList,
                pageNumber,
                pageSize,
                (long) totalElements,
                totalPages,
                lastPage
        );

    }

    @Override
    public PaginatedResponse<InvoiceDTO> getALlPendingOrdersByShopId(
            Integer pageNumber,
            Integer pageSize,
            String sortBy,
            String sortOrder,
            String shopId,
            Date startDate,
            Date endDate,
            String searchTerm) {

        // Validate date range if both dates are provided
        if (startDate != null && endDate != null && endDate.before(startDate)) {
            throw new APIException("End date must be after start date");
        }

        // Validate shop exists
        if (!shopRepository.existsById(shopId)) {
            throw new APIException("Shop Not Found");
        }

        // Create sort direction
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);

        // Create pageable with sorting
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        // Build the MongoDB query
        Query query = new Query();

        // Filter by shop ID
        query.addCriteria(Criteria.where("shopId").is(shopId));

        // Filter by pending status (not delivered)
        query.addCriteria(Criteria.where("orderStatus").ne(ORDER_DELIVERED));

        // Add date range filtering if dates are provided
        if (startDate != null && endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("invoiceDate")
                    .gte(startDate)
                    .lt(inclusiveEndDate)); // Using lt instead of lte
        } else if (startDate != null) {
            query.addCriteria(Criteria.where("invoiceDate").gte(startDate));
        } else if (endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("invoiceDate").lt(inclusiveEndDate));
        }

        // Add search term filtering if provided
        if (searchTerm != null && !searchTerm.isEmpty()) {
            Pattern pattern = Pattern.compile(searchTerm, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("invoiceId").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }

        // Get total count
        long totalCount = mongoTemplate.count(query, Invoice.class);

        // Apply pagination and execute query
        query.with(pageable);
        List<Invoice> invoiceList = mongoTemplate.find(query, Invoice.class);

        if (invoiceList.isEmpty()) {
            throw new APIException("No pending orders found");
        }

        // Map to DTOs
        List<InvoiceDTO> invoiceDTOS = invoiceList.stream()
                .map(invoice -> {
                    InvoiceDTO dto = modelMapper.map(invoice, InvoiceDTO.class);
                    // Manually set customer name if not automatically mapped
                    if (invoice.getCustomer() != null) {
                        dto.setCustomerName(invoice.getCustomer().getName());
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        boolean lastPage = pageNumber >= totalPages - 1;

        return new PaginatedResponse<>(
                invoiceDTOS,
                pageNumber,
                invoiceList.size(),
                totalCount,
                totalPages,
                lastPage
        );
    }

    @Override
    public PaginatedResponse<InvoiceDTO> searchPendingOrdersByName(Integer pageNumber, Integer pageSize,
                                                                   String sortBy, String sortOrder, String shopId,
                                                                   Date startDate, Date endDate, String term) {


        // 1. Base criteria
        Criteria criteria = Criteria.where("shopId").is(shopId)
                .and("orderStatus").ne(ORDER_DELIVERED);

        // 2. Convert input dates to UTC start/end of day
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("orderDate");

            if (startDate != null) {
                // Convert startDate to UTC start of day
                Instant startOfDay = startDate.toInstant()
                        .atZone(ZoneId.of("Asia/Kolkata"))
                        .toLocalDate()
                        .atStartOfDay(ZoneId.of("Asia/Kolkata"))
                        .toInstant();
                dateCriteria.gte(Date.from(startOfDay));
            }

            if (endDate != null) {
                // Convert endDate to UTC end of day
                Instant endOfDay = endDate.toInstant()
                        .atZone(ZoneId.of("Asia/Kolkata"))
                        .toLocalDate()
                        .plusDays(1)
                        .atStartOfDay(ZoneId.of("Asia/Kolkata"))
                        .minusNanos(1)
                        .toInstant();
                dateCriteria.lte(Date.from(endOfDay));
            }

            criteria.andOperator(dateCriteria);
        }

        // 3. Search term handling
        if (StringUtils.hasText(term)) {
            criteria = criteria.andOperator(
                    new Criteria().orOperator(
                            Criteria.where("invoiceId").regex(term, "i")
                    )
            );
        }

        long totalCount = mongoTemplate.count(Query.query(criteria), Invoice.class);
        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder), sortBy);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        List<Invoice> invoices = mongoTemplate.find(Query.query(criteria).with(pageable), Invoice.class);

        // Convert to DTOs
        List<InvoiceDTO> content = invoices.stream()
                .map(invoice -> {
                    InvoiceDTO dto = new InvoiceDTO();
                    // Map all fields from invoice to dto
                    dto.setInvoiceId(invoice.getInvoiceId());
                    dto.setShopId(invoice.getShopId());
                    dto.setItems(invoice.getItems());
                    dto.setTotalAmount(invoice.getTotalAmount());
                    dto.setAdvancePaid(invoice.getAdvancePaid());
                    dto.setBalanceDue(invoice.getBalanceDue());
                    dto.setPaymentMethod(invoice.getPaymentMethod());
                    dto.setPaymentStatus(invoice.getPaymentStatus());
                    dto.setOrderStatus(invoice.getOrderStatus());
                    dto.setExpectedDeliveryDate(invoice.getExpectedDeliveryDate());
                    dto.setPaymentCompletedDate(invoice.getPaymentCompletedDate());
                    dto.setOrderDate(invoice.getOrderDate());
                    dto.setInvoiceDate(invoice.getInvoiceDate());
                    dto.setNotes(invoice.getNotes());
                    dto.setReceiptId(invoice.getReceiptId());
                    // Get customer name from embedded customer document
                    if (invoice.getCustomer() != null) {
                        dto.setCustomerName(invoice.getCustomer().getName());
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        return new PaginatedResponse<>(
                content,
                pageNumber,
                pageSize,
                totalCount,
                totalPages,
                pageNumber >= totalPages - 1
        );
    }

    @Override
    public PaginatedResponse<InvoiceDTO> getAllCompletedOrdersByShopId(
            Integer pageNumber,
            Integer pageSize,
            String sortBy,
            String sortOrder,
            String shopId,
            Date startDate,
            Date endDate,
            String term
    )
    {
        // Validate date range if both dates are provided
        if (startDate != null && endDate != null && endDate.before(startDate)) {
            throw new APIException("End date must be after start date");
        }

        // Validate shop exists
        if (!shopRepository.existsById(shopId)) {
            throw new APIException("Shop Not Found");
        }

        // Create sort direction
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);

        // Create pageable with sorting
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        // Build the MongoDB query
        Query query = new Query();

        // Filter by shop ID
        query.addCriteria(Criteria.where("shopId").is(shopId));

        // Filter by pending status (not delivered)
        query.addCriteria(Criteria.where("orderStatus").is(ORDER_DELIVERED));

        // Add date range filtering if dates are provided
        if (startDate != null && endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("invoiceDate")
                    .gte(startDate)
                    .lt(inclusiveEndDate));
        } else if (startDate != null) {
            query.addCriteria(Criteria.where("invoiceDate").gte(startDate));
        } else if (endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("invoiceDate").lt(inclusiveEndDate));
        }

        // Add search term filtering if provided
        if (term != null && !term.isEmpty()) {
            Pattern pattern = Pattern.compile(term, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("invoiceId").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }

        // Get total count
        long totalCount = mongoTemplate.count(query, Invoice.class);

        // Apply pagination and execute query
        query.with(pageable);
        List<Invoice> invoiceList = mongoTemplate.find(query, Invoice.class);

        if (invoiceList.isEmpty()) {
            throw new APIException("No Completed orders found");
        }

        // Map to DTOs
        List<InvoiceDTO> invoiceDTOS = invoiceList.stream()
                .map(invoice -> {
                    InvoiceDTO dto = modelMapper.map(invoice, InvoiceDTO.class);
                    // Manually set customer name if not automatically mapped
                    if (invoice.getCustomer() != null) {
                        dto.setCustomerName(invoice.getCustomer().getName());
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        boolean lastPage = pageNumber >= totalPages - 1;

        return new PaginatedResponse<>(
                invoiceDTOS,
                pageNumber,
                invoiceList.size(),
                totalCount,
                totalPages,
                lastPage
        );
    }

    @Override
    public List<InvoiceDTO> searchCompletedOrdersByName(String shopId, String term) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("shopId").is(shopId)
                        .and("orderStatus").is(ORDER_DELIVERED)),
                Aggregation.lookup("customers", "customer.$id", "_id", "customerData"),
                Aggregation.unwind("customerData"),
                Aggregation.match(new Criteria().orOperator(
                        Criteria.where("invoiceId").regex(term, "i"),
                        Criteria.where("customerData.name").regex(term, "i")
                )),
                Aggregation.project()
                        .and("invoiceId").as("invoiceId")
                        .and("shopId").as("shopId")
                        .and("items").as("items")
                        .and("totalAmount").as("totalAmount")
                        .and("advancePaid").as("advancePaid")
                        .and("balanceDue").as("balanceDue")
                        .and("paymentMethod").as("paymentMethod")
                        .and("paymentStatus").as("paymentStatus")
                        .and("orderStatus").as("orderStatus")
                        .and("expectedDeliveryDate").as("expectedDeliveryDate")
                        .and("paymentCompletedDate").as("paymentCompletedDate")
                        .and("orderDate").as("orderDate")
                        .and("invoiceDate").as("invoiceDate")
                        .and("notes").as("notes")
                        .and("receiptId").as("receiptId")
                        .and("customerData.name").as("customerName")
        );

        return mongoTemplate.aggregate(aggregation, "invoice", InvoiceDTO.class)
                .getMappedResults();
    }

    @Override
    public PaginatedResponse<ReceiptDTO> getAllReceiptFromParticularShop(
            Integer pageNumber, Integer pageSize,
            String sortBy, String sortOrder,
            String shopId, Date startDate,
            Date endDate, String term)
    {
        // Validate date range if both dates are provided
        if (startDate != null && endDate != null && endDate.before(startDate)) {
            throw new APIException("End date must be after start date");
        }

        // Validate shop exists
        if (!shopRepository.existsById(shopId)) {
            throw new APIException("Shop Not Found");
        }

        // Create sort direction
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);

        // Create pageable with sorting
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        // Build the MongoDB query
        Query query = new Query();

        // Filter by shop ID
        query.addCriteria(Criteria.where("shopId").is(shopId));

        // Add date range filtering if dates are provided
        if (startDate != null && endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("receiptDate")
                    .gte(startDate)
                    .lt(inclusiveEndDate));
        } else if (startDate != null) {
            query.addCriteria(Criteria.where("receiptDate").gte(startDate));
        } else if (endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("receiptDate").lt(inclusiveEndDate));
        }

        // Add search term filtering if provided
        if (term != null && !term.isEmpty()) {
            Pattern pattern = Pattern.compile(term, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("receiptNo").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }

        // Get total count
        long totalCount = mongoTemplate.count(query, Receipt.class);

        // Apply pagination and execute query
        query.with(pageable);
        List<Receipt> receiptList = mongoTemplate.find(query, Receipt.class);

        if (receiptList.isEmpty()) {
            throw new APIException("No receipt found");
        }

        // Map to DTOs
        List<ReceiptDTO> receiptDTOS = receiptList.stream()
                .map(receipt -> {
                    ReceiptDTO dto = modelMapper.map(receipt, ReceiptDTO.class);
                    if (receipt.getCustomer() != null) {
                        dto.setCustomerName(receipt.getCustomer().getName());
                    }
                    return dto;
                })
                .toList();

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        boolean lastPage = pageNumber >= totalPages - 1;

        return new PaginatedResponse<>(
                receiptDTOS,
                pageNumber,
                receiptList.size(),
                totalCount,
                totalPages,
                lastPage
        );
    }

    @Override
    public PaginatedResponse<InvoiceDTO> getAllInvoiceFromParticularShop(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId, Date startDate, Date endDate, String term) {
        // Validate date range if both dates are provided
        if (startDate != null && endDate != null && endDate.before(startDate)) {
            throw new APIException("End date must be after start date");
        }

        // Validate shop exists
        if (!shopRepository.existsById(shopId)) {
            throw new APIException("Shop Not Found");
        }

        // Create sort direction
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);

        // Create pageable with sorting
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        // Build the MongoDB query
        Query query = new Query();

        // Filter by shop ID
        query.addCriteria(Criteria.where("shopId").is(shopId));

        // Add date range filtering if dates are provided
        if (startDate != null && endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("invoiceDate")
                    .gte(startDate)
                    .lt(inclusiveEndDate));
        } else if (startDate != null) {
            query.addCriteria(Criteria.where("invoiceDate").gte(startDate));
        } else if (endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("invoiceDate").lt(inclusiveEndDate));
        }

        // Add search term filtering if provided
        if (term != null && !term.isEmpty()) {
            Pattern pattern = Pattern.compile(term, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("invoiceId").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }

        // Get total count
        long totalCount = mongoTemplate.count(query, Invoice.class);

        // Apply pagination and execute query
        query.with(pageable);
        List<Invoice> invoiceList = mongoTemplate.find(query, Invoice.class);

        if (invoiceList.isEmpty()) {
            throw new APIException("No Invoice found");
        }

        // Map to DTOs
        List<InvoiceDTO> invoiceDTOS = invoiceList.stream()
                .map(invoice -> {
                    InvoiceDTO dto = modelMapper.map(invoice, InvoiceDTO.class);
                    if (invoice.getCustomer() != null) {
                        dto.setCustomerName(invoice.getCustomer().getName());
                    }
                    return dto;
                })
                .toList();

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        boolean lastPage = pageNumber >= totalPages - 1;

        return new PaginatedResponse<>(
                invoiceDTOS,
                pageNumber,
                invoiceList.size(),
                totalCount,
                totalPages,
                lastPage
        );
    }

    @Override
    public PaginatedResponse<PettyCashDTO> getAllPettyCashByShopId(Integer pageNumber, Integer pageSize,
                                                                   String sortBy, String sortOrder, String shopId, Date startDate,
                                                                   Date endDate, String searchTerm) {

        // Validate date range if both dates are provided
        if (startDate != null && endDate != null && endDate.before(startDate)) {
            throw new APIException("End date must be after start date");
        }

        // Validate shop exists
        if (!shopRepository.existsById(shopId)) {
            throw new APIException("Shop Not Found");
        }

        // Create sort direction
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;

        // Build the MongoDB query
        Query query = new Query(Criteria.where("_id").is(shopId));

        // Build array filters for petty cash details
        List<Criteria> pettyCashCriteria = new ArrayList<>();

        // Add date range filtering if dates are provided
        if (startDate != null && endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            pettyCashCriteria.add(Criteria.where("allPettyCashDetails.addingDate")
                    .gte(startDate)
                    .lt(inclusiveEndDate));
        } else if (startDate != null) {
            pettyCashCriteria.add(Criteria.where("allPettyCashDetails.addingDate")
                    .gte(startDate));
        } else if (endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            pettyCashCriteria.add(Criteria.where("allPettyCashDetails.addingDate").lt(inclusiveEndDate));
        }

        // Add date range filtering if dates are provided
//        if (startDate != null && endDate != null) {
//            pettyCashCriteria.add(Criteria.where("allPettyCashDetails.addingDate")
//                    .gte(startDate)
//                    .lte(endDate));
//        } else if (startDate != null) {
//            pettyCashCriteria.add(Criteria.where("allPettyCashDetails.addingDate")
//                    .gte(startDate));
//        } else if (endDate != null) {
//            pettyCashCriteria.add(Criteria.where("allPettyCashDetails.addingDate")
//                    .lte(endDate));
//        }

        // Add search term filtering if provided
        if (searchTerm != null && !searchTerm.isEmpty()) {
            try {
                // Try to parse as number for amount search
                double amount = Double.parseDouble(searchTerm);
                pettyCashCriteria.add(Criteria.where("allPettyCashDetails.amount")
                        .is(amount));
            } catch (NumberFormatException e) {
                // If not a number, search in addedBy field
                Pattern pattern = Pattern.compile(searchTerm, Pattern.CASE_INSENSITIVE);
                pettyCashCriteria.add(Criteria.where("allPettyCashDetails.addedBy")
                        .regex(pattern));
            }
        }

        // Combine all criteria for array elements
        if (!pettyCashCriteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(
                    Criteria.where("allPettyCashDetails").exists(true),
                    new Criteria().orOperator(pettyCashCriteria.toArray(new Criteria[0]))
            ));
        }

        // Get the shop with filtered petty cash details
        Shop shop = mongoTemplate.findOne(query, Shop.class);
        if (shop == null || shop.getAllPettyCashDetails() == null) {
            throw new APIException("No Petty Cash records found");
        }

        // Filter and sort the petty cash details
        List<PettyCashDTO> pettyCashList = shop.getAllPettyCashDetails().stream()
                .filter(pc -> {

                    boolean dateMatch = true;
                    if (startDate != null) {
                        dateMatch = pc.getAddingDate().compareTo(startDate) >= 0;
                    }
                    if (endDate != null) {
                        // Increase endDate by 1 day to make it inclusive
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(endDate);
                        calendar.add(Calendar.DAY_OF_MONTH, 1);
                        Date inclusiveEndDate = calendar.getTime();
                        dateMatch = dateMatch && pc.getAddingDate().compareTo(inclusiveEndDate) <= 0;
                    }

                    boolean termMatch = true;
                    if (searchTerm != null && !searchTerm.isEmpty()) {
                        try {
                            double amount = Double.parseDouble(searchTerm);
                            termMatch = pc.getAmount() == amount;
                        } catch (NumberFormatException e) {
                            termMatch = pc.getAddedBy().toLowerCase()
                                    .contains(searchTerm.toLowerCase());
                        }
                    }

                    return dateMatch && termMatch;
                })
                .sorted((pc1, pc2) -> {
                    int compareResult = switch (sortBy) {
                        case "amount" -> Double.compare(pc1.getAmount(), pc2.getAmount());
                        case "addedBy" -> pc1.getAddedBy().compareTo(pc2.getAddedBy());
                        default -> pc1.getAddingDate().compareTo(pc2.getAddingDate());
                    };
                    return direction == Sort.Direction.ASC ? compareResult : -compareResult;
                })
                .collect(Collectors.toList());

        // Apply pagination
        int totalCount = pettyCashList.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalCount);
        boolean lastPage = (pageNumber >= totalPages - 1);

        if (fromIndex > pettyCashList.size()) {
            throw new APIException("Page number out of range");
        }

        List<PettyCashDTO> paginatedList = pettyCashList.subList(fromIndex, toIndex);

        // Map to DTOs
        List<PettyCashDTO> dtos = paginatedList.stream()
                .map(pc -> {
                    PettyCashDTO dto = modelMapper.map(pc, PettyCashDTO.class);
                    return dto;
                })
                .collect(Collectors.toList());

        return new PaginatedResponse<>(
                dtos,
                pageNumber,
                dtos.size(),
                (long) pettyCashList.size(),
                totalPages,
                lastPage
        );
    }

    @Override
    @Transactional
    public UserDtoForOwner deleteEmployeeByUserId(String shopId, String userId) {
        // 1. Find and validate shop and user
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new APIException("Shop Not Found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new APIException("No user found"));

        // 2. Check if user is actually an employee of this shop (using ID comparison)
        boolean isEmployee = shop.getEmployees().stream()
                .anyMatch(emp -> emp.getUserId().equals(userId));

        if (!isEmployee) {
            throw new APIException("User is not an employee of this shop");
        }

        // 3. Remove user from shop's employee list (using ID comparison)
        shop.setEmployees(shop.getEmployees().stream()
                .filter(emp -> !emp.getUserId().equals(userId))
                .collect(Collectors.toList()));

        shopRepository.save(shop);

        // 4. Unassign all items assigned to this user across all invoices
        List<Invoice> invoices = invoiceRepository.findByShopIdAndAssignedTailorId(shopId, userId);
        for (Invoice invoice : invoices) {
            // Unassign from invoice level
            invoice.setAssignedTailorId(null);
            invoice.setAssignmentStatus("UNASSIGNED");

            // Unassign from item level
            if (invoice.getItems() != null) {
                for (Items item : invoice.getItems()) {
                    if (userId.equals(item.getAssignedTo())) {
                        item.setAssignedTo(null);
                        item.setAssignmentStatus(OrderStatus.ORDER_ACCEPTED);
                        // Add to assignment history
                        item.getAssignmentHistory().add(new Items.AssignmentHistory(
                                userId,
                                OrderStatus.UNASSIGNED_DUE_TO_USER_DELETION,
                                new Date()
                        ));
                    }
                }
            }
            invoiceRepository.save(invoice);
        }

        // 5. Delete the user
        userRepository.delete(user);

        return modelMapper.map(user, UserDtoForOwner.class);
    }

    private InvoiceDTO convertToDto(Invoice invoice) {
        InvoiceDTO dto = modelMapper.map(invoice, InvoiceDTO.class);
        if (invoice.getCustomer() != null) {
            dto.setCustomerName(invoice.getCustomer().getName());
        }
        // Map any other fields that don't automatically map
        dto.setExpectedDeliveryDate(invoice.getExpectedDeliveryDate());
        dto.setOrderStatus(invoice.getOrderStatus());
        // Add other fields as needed
        return dto;
    }

    @NotNull
    private static <T> PaginatedResponse<T> getInvoiceDTOPaginatedResponse(List<T> invoiceDTOS, Page<?> invoicePage) {
        PaginatedResponse<T> invoiceDTOPaginatedResponse = new PaginatedResponse<>();

        invoiceDTOPaginatedResponse.setContent(invoiceDTOS);
        invoiceDTOPaginatedResponse.setPageNumber(invoicePage.getNumber());
        invoiceDTOPaginatedResponse.setPageSize(invoicePage.getSize());
        invoiceDTOPaginatedResponse.setTotalElements(invoicePage.getTotalElements());
        invoiceDTOPaginatedResponse.setTotalPages(invoicePage.getTotalPages());
        invoiceDTOPaginatedResponse.setLastPage(invoicePage.isLast());
        return invoiceDTOPaginatedResponse;
    }

}
