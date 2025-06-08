package com.saritha.fashions.service;

import com.saritha.fashions.controller.AuthController;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Expense;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.expense.ExpenseDTO;
import com.saritha.fashions.payload.shop.AllShopDTO;
import com.saritha.fashions.payload.shop.PettyCashDTO;
import com.saritha.fashions.payload.shop.ShopRegisterDTO;
import com.saritha.fashions.payload.shop.ShopUpdateDTO;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.security.request.SignupRequest;
import com.saritha.fashions.service.Interface.ShopService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import java.util.List;

import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;


@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private AuthController authController;
    @Autowired
    private AuthUtil authUtil;
    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    @Transactional
    public Shop createShop(ShopRegisterDTO shop) {

        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        Shop shopConverted = modelMapper.map(shop, Shop.class);
        shopConverted.setShopContactNumber(shop.getOwnerNumber());

        Shop shopSaved = shopRepository.save(shopConverted);

        SignupRequest signupRequest = new SignupRequest();

        // Explicitly setting the correct name field
        signupRequest.setName(shop.getOwnerName());
        signupRequest.setPassword(shop.getPassword());
        signupRequest.setAssignedShops(shopSaved.getId());
        signupRequest.setPhone(shop.getOwnerNumber());
        signupRequest.setEmail(shop.getOwnerEmailId());
        Set<String> roles = new HashSet<>();
        roles.add("shopOwner");
        signupRequest.setRoles(roles);
        signupRequest.setSalary(shop.getSalary());
        signupRequest.setAddress(shop.getShopAddress());

        try {
            ResponseEntity<?> response = authController.registerUser(signupRequest);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new APIException("User creation failed: " + response.getBody());
            }
        } catch (Exception e) {
            // Manually delete the shop if user registration fails
            shopRepository.deleteById(shopSaved.getId());
//            throw new APIException(e.getMessage());

            Matcher matcher = Pattern.compile("message=(.*?)\\)").matcher(e.getMessage());
            if (matcher.find()) {
                throw new APIException(matcher.group(1));
            } else {
                throw new APIException(e.getMessage());
            }

        }

        return shopSaved;
    }

    @Override
    public List<Shop> getAllShops() {
        return shopRepository.findAll();
    }

    @Override
    public Shop getShopById(String id) {
        return shopRepository.findById(id).orElseThrow(() -> new APIException("No Shop Found"));
    }

    @Override
    public Shop updateShop(String id, ShopUpdateDTO updatedFields) {
//        Optional<Shop> existingShop = shopRepository.findById(id);
//        if (existingShop.isPresent()) {
//            Shop shop = existingShop.get();
//            shop.setShopName(updatedShop.getShopName());
//            shop.setOwnerName(updatedShop.getOwnerName());
//            shop.setShopAddress(updatedShop.getShopAddress());
//            shop.setShopContactNumber(updatedShop.getShopContactNumber());
//            shop.setGstNumber(updatedShop.getGstNumber());
//            shop.setShopType(updatedShop.getShopType());
//            return shopRepository.save(shop);
//        }
        return shopRepository.findById(id).map(existingShop -> {
            modelMapper.map(updatedFields, existingShop);
            return shopRepository.save(existingShop);
        }).orElse(null);
    }

    @Override
    public void deleteShop(String id) {
        shopRepository.deleteById(id);
    }

    @Override
    public List<User> getAllEmployeeFromShops() {
        User user = authUtil.loggedInUser();
        String shopId = user.getAssignedShops().get(0).getId();
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop != null) {
            return shop.getEmployees() != null ? shop.getEmployees() : new ArrayList<User>();
        }else {
            return null;
        }
    }

    @Override
    public Shop getPettyCashOfShop() {
        User user = authUtil.loggedInUser();
        String shopId = user.getAssignedShops().get(0).getId();

        return shopRepository.findById(shopId).orElse(null);
    }

    @Override
    public Shop setPettyCashToShop(double amount, String shopId) {
        Shop shop = shopRepository.findById(shopId).orElse(null);

        if (shop != null) {
            double previousPettyCashAmount = shop.getPettyCash();
            shop.setPettyCash(previousPettyCashAmount+amount);

            PettyCashDTO pettyCashDTO = new PettyCashDTO();
            pettyCashDTO.setAddingDate(DateTimeUtils.getNowInIST());
            pettyCashDTO.setAmount(amount);
            pettyCashDTO.setAddedBy(authUtil.loggedInUserId());

            List<PettyCashDTO> pettyCashDTOS = shop.getAllPettyCashDetails();
            pettyCashDTOS.add(pettyCashDTO);
            shop.setAllPettyCashDetails(pettyCashDTOS);

            return shopRepository.save(shop);
        } else {
            throw new APIException("Shop Not Found");
        }
    }

    @Override
    public PaginatedResponse<ExpenseDTO> searchAllExpensesByShopId(
            Integer pageNumber,
            Integer pageSize,
            String sortBy,
            String sortOrder,
            String shopId,
            Date startDate,
            Date endDate,
            String term)
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

            query.addCriteria(Criteria.where("expenseDate")
                    .gte(startDate)
                    .lt(inclusiveEndDate));
        } else if (startDate != null) {
            query.addCriteria(Criteria.where("expenseDate").gte(startDate));
        } else if (endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("expenseDate").lt(inclusiveEndDate));
        }

        // Add search term filtering if provided
        if (term != null && !term.isEmpty()) {
            Pattern pattern = Pattern.compile(term, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("expenseType").regex(pattern),
                    Criteria.where("expenseAmount").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }

        // Get total count
        long totalCount = mongoTemplate.count(query, Expense.class);

        // Apply pagination and execute query
        query.with(pageable);
        List<Expense> expenseList = mongoTemplate.find(query, Expense.class);

        if (expenseList.isEmpty()) {
            throw new APIException("No expenses found");
        }

        // Map to DTOs
        List<ExpenseDTO> expenseDTOS = expenseList.stream()
                .map(expense -> {
                    ExpenseDTO dto = modelMapper.map(expense, ExpenseDTO.class);
                    return dto;
                })
                .collect(Collectors.toList());

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        boolean lastPage = pageNumber >= totalPages - 1;

        return new PaginatedResponse<>(
                expenseDTOS,
                pageNumber,
                expenseList.size(),
                totalCount,
                totalPages,
                lastPage
        );
    }

    @Override
    public PaginatedResponse<ExpenseDTO> searchAllExpensesByShopIdWithPaymentMethod(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId, Date startDate, Date endDate, String term) {
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

            query.addCriteria(Criteria.where("expenseDate")
                    .gte(startDate)
                    .lt(inclusiveEndDate));
        } else if (startDate != null) {
            query.addCriteria(Criteria.where("expenseDate").gte(startDate));
        } else if (endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("expenseDate").lt(inclusiveEndDate));
        }

        // Add search term filtering if provided
        if (term != null && !term.isEmpty()) {
            Pattern pattern = Pattern.compile(term, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("paymentMethod").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }

        // Get total count
        long totalCount = mongoTemplate.count(query, Expense.class);

        // Apply pagination and execute query
        query.with(pageable);
        List<Expense> expenseList = mongoTemplate.find(query, Expense.class);

        if (expenseList.isEmpty()) {
            throw new APIException("No expenses found");
        }

        // Map to DTOs
        List<ExpenseDTO> expenseDTOS = expenseList.stream()
                .map(expense -> {
                    ExpenseDTO dto = modelMapper.map(expense, ExpenseDTO.class);
                    return dto;
                })
                .collect(Collectors.toList());

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        boolean lastPage = pageNumber >= totalPages - 1;

        return new PaginatedResponse<>(
                expenseDTOS,
                pageNumber,
                expenseList.size(),
                totalCount,
                totalPages,
                lastPage
        );
    }

    @Override
    public List<AllShopDTO> findAllShopSummaries() {
        return shopRepository.findAllShopSummaries();
    }

}
