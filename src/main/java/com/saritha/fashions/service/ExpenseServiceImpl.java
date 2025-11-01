package com.saritha.fashions.service;

import com.saritha.fashions.interceptor.ExecutionTimeInterceptor;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Expense;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.expense.ExpenseDTO;
import com.saritha.fashions.repository.ExpenseRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.service.Interface.ExpenseService;
import com.saritha.fashions.util.AuthUtil;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExecutionTimeInterceptor executionTimeInterceptor;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ModelMapper modelMapper;

//    @Override
//    @Transactional
//    public Expense addExpense(Expense expense) {
//        try {
//
//            String userId = authUtil.loggedInUser().getUserId();
//            Query query = Query.query(Criteria.where("employeeIds").is(userId));
//            query.fields().include("id");
//            List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);
//
//            if (shops.isEmpty()) {
//                throw new APIException("User is not assigned to any shop");
//            }
//
//            String shopId = shops.get(0).getId();
//            expense.setShopId(shopId);
//
////            if(!(shopFromDb.getPettyCash()-shopFromDb.getTotalExpense() > expense.getExpenseAmount())) {
////                throw new APIException("You do not have enough petty cash to spend, please contact your owner");
////            }
//            if (expense.getExpenseDate() != null) {
//                // Convert backend date to IST
//                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
//                formatter.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
//
//                String expenseDateStr = formatter.format(expense.getExpenseDate());
//                expense.setExpenseDate(formatter.parse(expenseDateStr));
//            }
//
//            // Summing up all bills
//            Optional<Shop> shopOptional = shopRepository.findById(shopId);
//            expense.setExpenseAmount(expense.getExpenseAmount());
//            double totalExpense = shopOptional.map(Shop::getTotalExpense).orElse(0.0);
//            totalExpense += expense.getExpenseAmount();
//
//            // Set default payment method
//            if (expense.getPaymentMethod() == null) {
//                expense.setPaymentMethod("Cash");
//            } else {
//                expense.setPaymentMethod(expense.getPaymentMethod());
//            }
//
//            expense.setExpenseType(expense.getExpenseType());
//            Expense savedExpense = expenseRepository.save(expense);
//            if (shopOptional.isPresent()) {
//                Shop shop = shopOptional.get();
//                List<Expense> allExpenses = shop.getAllExpense() == null ? new ArrayList<Expense>() : shop.getAllExpense();
//                allExpenses.add(savedExpense);
//                shop.setAllExpense(allExpenses);
//                shop.setTotalExpense(totalExpense);
//                shopRepository.save(shop);
//            }
//            return savedExpense;
//
//        } catch (Exception e) {
//            throw new RuntimeException("Invalid date format", e);
//        }
//
//    }

    @Override
    @Transactional
    public Expense addExpense(Expense expense) {
        try {
            User user = authUtil.loggedInUser();
            String userId = user.getUserId();

            // Find user's shop
            Query query = Query.query(Criteria.where("employeeIds").is(userId));
            query.fields().include("id");
            List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

            if (shops.isEmpty()) {
                throw new APIException("User is not assigned to any shop");
            }

            String shopId = shops.get(0).getId();
            expense.setShopId(shopId);

            // Get shop with fresh read
            Shop shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new APIException("Shop Not Found"));

            // Validate payment method for cash expenses and stop expenses when there is no petty cash
            if ("Cash".equalsIgnoreCase(expense.getPaymentMethod())) {
                double currentPettyCash = shop.getPettyCash();
                if (currentPettyCash < expense.getExpenseAmount()) {
                    throw new APIException("You do not have sufficient petty cash to spend, please contact owner to provide petty cash");
                }
            }

            // Set default payment method if null
            if (expense.getPaymentMethod() == null) {
                expense.setPaymentMethod("Cash");
            }

            // Save expense first
            Expense savedExpense = expenseRepository.save(expense);

            // Update shop expenses and total
            List<Expense> allExpenses = shop.getAllExpense() == null ?
                    new ArrayList<>() : shop.getAllExpense();
            allExpenses.add(savedExpense);

            double newTotalExpense = shop.getTotalExpense() + savedExpense.getExpenseAmount();

            shop.setAllExpense(allExpenses);
            shop.setTotalExpense(newTotalExpense);

            // If paid by cash, deduct from petty cash
            if ("Cash".equalsIgnoreCase(expense.getPaymentMethod())) {
                double newPettyCash = shop.getPettyCash() - savedExpense.getExpenseAmount();
                shop.setPettyCash(newPettyCash);
            }

            shopRepository.save(shop);

            return savedExpense;

        } catch (APIException e) {
            throw e; // Re-throw API exceptions
        } catch (Exception e) {
            throw new RuntimeException("Failed to add expense", e);
        }
    }

    @Override
    public List<Expense> getAllExpenses() {
        User user = authUtil.loggedInUser();
        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        return expenseRepository.findByShopId(shopId);
    }

    @Override
    public List<Expense> getExpensesByShopId(String shopId) {
        return expenseRepository.findByShopId(shopId);
    }

    @Override
    public void deleteExpense(String id) {
        expenseRepository.deleteById(id);
    }

    @Override
    public Expense getExpenseById(String id) {
        return expenseRepository.findById(id).orElse(null);
    }

    @Override
    public Expense updateExpense(String id, Expense updatedExpense) {
//        Optional<Expense> existingExpense = expenseRepository.findById(id);
//        if (existingExpense.isPresent()) {
//            Expense expense = existingExpense.get();
//
//            expense.setAmount(updatedExpense.getAmount());
//            expense.setExpenseDate(updatedExpense.getExpenseDate());
//
//            if (expense.getMaterialExpensesBill() > 0) {
//                expense.setMaterialExpensesBill(updatedExpense.getMaterialExpensesBill());
//            } else if (expense.getRentBill() > 0) {
//                expense.setRentBill(updatedExpense.getRentBill());
//            } else if (expense.getElectricityBill() > 0) {
//                expense.setElectricityBill(updatedExpense.getElectricityBill());
//            }
//
//            expense.setPaymentMethod(updatedExpense.getPaymentMethod());
//            return expenseRepository.save(expense);
//        return null;
//        }
        return null;
    }

//    @Override
//    @Transactional
//    public Expense addEmployeeExpense(Expense expense) {
//        try {
//            User user = authUtil.loggedInUser();
//
//            String userId = user.getUserId();
//            Query query = Query.query(Criteria.where("employeeIds").is(userId));
//            query.fields().include("id");
//            List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);
//
//            if (shops.isEmpty()) {
//                throw new APIException("User is not assigned to any shop");
//            }
//
//            String shopId = shops.get(0).getId();
//
//            expense.setShopId(shopId);
//            if (expense.getExpenseDate() != null) {
//                // Convert backend date to IST
//                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
//                formatter.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
//
//                String expenseDateStr = formatter.format(expense.getExpenseDate());
//                expense.setExpenseDate(formatter.parse(expenseDateStr));
//            }
//
//            // Summing up all bill
//            Optional<Shop> shopOptional = shopRepository.findById(shopId);
//
//            if (shopOptional.isEmpty()) {
//                throw new APIException("Shop Not Found");
//            }
//
//            expense.setExpenseAmount(expense.getExpenseAmount());
//            double totalExpense = shopOptional.map(Shop::getTotalExpense).orElse(0.0);
//            totalExpense += expense.getExpenseAmount();
//
//            if (expense.getPaymentMethod().equalsIgnoreCase("Cash")) {
//                if(shopOptional.get().getPettyCash() < totalExpense){
//                    throw new APIException("You do not have sufficient petty cash to spend, please contact owner to provide petty cash");
//                }
//            }
//
//            // Set default payment method
//            expense.setPaymentMethod(expense.getPaymentMethod());
//
//            expense.setExpenseType(expense.getExpenseType());
//            expense.setRefreshmentDetails(expense.getRefreshmentDetails());
//            expense.setMiscellaneousDetails(expense.getMiscellaneousDetails());
//            expense.setTravelDetails(expense.getTravelDetails());
//
//            Expense savedExpense = expenseRepository.save(expense);
//
//            Shop shop = shopOptional.get();
//            List<Expense> allExpenses = shop.getAllExpense() == null ? new ArrayList<Expense>() : shop.getAllExpense();
//            allExpenses.add(savedExpense);
//            shop.setAllExpense(allExpenses);
//            shop.setTotalExpense(totalExpense);
//            shopRepository.save(shop);
//
//            return savedExpense;
//
//        } catch (Exception e) {
//            throw new RuntimeException("Invalid date format", e);
//        }
//    }

    @Override
    @Transactional
    public Expense addEmployeeExpense(Expense expense) {
        try {
            User user = authUtil.loggedInUser();
            String userId = user.getUserId();

            // Find user's shop
            Query query = Query.query(Criteria.where("employeeIds").is(userId));
            query.fields().include("id");
            List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

            if (shops.isEmpty()) {
                throw new APIException("User is not assigned to any shop");
            }

            String shopId = shops.get(0).getId();
            expense.setShopId(shopId);

            // Get shop with proper locking or fresh read
            Shop shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new APIException("Shop Not Found"));

            // Validate payment method for cash expenses
            if ("Cash".equalsIgnoreCase(expense.getPaymentMethod())) {
                double currentPettyCash = shop.getPettyCash();
                if (currentPettyCash < expense.getExpenseAmount()) {
                    throw new APIException("You do not have sufficient petty cash to spend, please contact owner to provide petty cash");
                }
            }

            // Save expense first
            Expense savedExpense = expenseRepository.save(expense);

            // Update shop expenses and total
            List<Expense> allExpenses = shop.getAllExpense() == null ?
                    new ArrayList<>() : shop.getAllExpense();
            allExpenses.add(savedExpense);

            // Calculate new total expense - THIS WAS THE MAIN ISSUE
            double newTotalExpense = shop.getTotalExpense() + savedExpense.getExpenseAmount();

            shop.setAllExpense(allExpenses);
            shop.setTotalExpense(newTotalExpense);

            // If paid by cash, deduct from petty cash
            if ("Cash".equalsIgnoreCase(expense.getPaymentMethod())) {
                double newPettyCash = shop.getPettyCash() - savedExpense.getExpenseAmount();
                shop.setPettyCash(newPettyCash);
            }

            shopRepository.save(shop);

            return savedExpense;

        } catch (APIException e) {
            throw e; // Re-throw API exceptions
        } catch (Exception e) {
            throw new RuntimeException("Failed to add employee expense", e);
        }
    }

    @Override
    public PaginatedResponse<ExpenseDTO> getAllExpenseFromParticularShop(
            Integer pageNumber, Integer pageSize,
            String sortBy, String sortOrder,
            String shopId, Date startDate,
            Date endDate, String term) {

        // Validate date range
        if (startDate != null && endDate != null && endDate.before(startDate)) {
            throw new APIException("End date must be after start date");
        }

        // Create sort direction
        Sort.Direction direction = sortOrder.equalsIgnoreCase("desc") ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        // Build the base query
        Criteria criteria = Criteria.where("shopId").is(shopId);

        // Date range filtering - fixed implementation
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = new Criteria();

            if (startDate != null) {
                dateCriteria = dateCriteria.and("expenseDate").gte(startDate);
            }

            if (endDate != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(endDate);
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                Date inclusiveEndDate = calendar.getTime();

                if (startDate != null) {
                    // Both dates provided - use andOperator to combine
                    criteria.andOperator(
                            Criteria.where("expenseDate").gte(startDate),
                            Criteria.where("expenseDate").lt(inclusiveEndDate)
                    );
                } else {
                    // Only end date provided
                    criteria.and("expenseDate").lt(inclusiveEndDate);
                }
            } else if (startDate != null) {
                // Only start date provided
                criteria.and("expenseDate").gte(startDate);
            }
        }

        // Search term filtering
        if (StringUtils.hasText(term)) {
            Pattern pattern = Pattern.compile(term, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("expenseType").regex(pattern),
                    Criteria.where("notes").regex(pattern),
                    Criteria.where("paymentMethod").regex(pattern)
            );
            criteria.andOperator(searchCriteria);
        }

        // Build final query
        Query query = new Query(criteria).with(pageable);

        // Get paginated results
        List<Expense> expenses = mongoTemplate.find(query, Expense.class);

        // Convert to DTOs
        List<ExpenseDTO> dtos = expenses.stream()
                .map(expense -> modelMapper.map(expense, ExpenseDTO.class))
                .toList();

        // Get total count
        long totalCount = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Expense.class);

        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        boolean lastPage = pageNumber >= totalPages - 1;

        return new PaginatedResponse<>(
                dtos,
                pageNumber,
                expenses.size(),
                totalCount,
                totalPages,
                lastPage
        );
    }

//    @Override
//    public PaginatedResponse<ExpenseDTO> getAllExpenseFromParticularShop(Integer pageNumber, Integer pageSize,
//                                                                         String sortBy, String sortOrder,
//                                                                         String shopId, Date startDate,
//                                                                         Date endDate, String term)
//    {
//
//        // Validate date range if both dates are provided
//        if (startDate != null && endDate != null && endDate.before(startDate)) {
//            throw new APIException("End date must be after start date");
//        }
//
//        // Validate shop exists
//        if (!shopRepository.existsById(shopId)) {
//            throw new APIException("Shop Not Found");
//        }
//
//        // Create sort direction
//        Sort.Direction direction = sortOrder.equalsIgnoreCase("desc") ?
//                Sort.Direction.ASC : Sort.Direction.DESC;
//        Sort sort = Sort.by(direction, sortBy);
//
//        // Create pageable with sorting
//        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
//
//        // Build the MongoDB query
//        Query query = new Query();
//
//        // Filter by shop ID
//        query.addCriteria(Criteria.where("shopId").is(shopId));
//
//        // Add date range filtering if dates are provided
//        if (startDate != null && endDate != null) {
//            // Increase endDate by 1 day to make it inclusive
//            Calendar calendar = Calendar.getInstance();
//            calendar.setTime(endDate);
//            calendar.add(Calendar.DAY_OF_MONTH, 1);
//            Date inclusiveEndDate = calendar.getTime();
//
//            query.addCriteria(Criteria.where("expenseDate")
//                    .gte(startDate)
//                    .lt(inclusiveEndDate));
//        } else if (startDate != null) {
//            query.addCriteria(Criteria.where("expenseDate").gte(startDate));
//        } else if (endDate != null) {
//            // Increase endDate by 1 day to make it inclusive
//            Calendar calendar = Calendar.getInstance();
//            calendar.setTime(endDate);
//            calendar.add(Calendar.DAY_OF_MONTH, 1);
//            Date inclusiveEndDate = calendar.getTime();
//
//            query.addCriteria(Criteria.where("expenseDate").lt(inclusiveEndDate));
//        }
//
//        // Add search term filtering if provided
//        if (term != null && !term.isEmpty()) {
//            Pattern pattern = Pattern.compile(term, Pattern.CASE_INSENSITIVE);
//            Criteria searchCriteria = new Criteria().orOperator(
//                    Criteria.where("invoiceId").regex(pattern)
//            );
//            query.addCriteria(searchCriteria);
//        }
//
//        // Get total count
//        long totalCount = mongoTemplate.count(query, Expense.class);
//
//        // Apply pagination and execute query
//        query.with(pageable);
//        List<Expense> expenseList = mongoTemplate.find(query, Expense.class);
//
//        if (expenseList.isEmpty()) {
//            throw new APIException("No Expense found");
//        }
//
//        // Map to DTOs
//        List<ExpenseDTO> expenseDTOS = expenseList.stream()
//                .map(expense -> {
//                    ExpenseDTO dto = modelMapper.map(expense, ExpenseDTO.class);
////                    if (expense.get() != null) {
////                        dto.setCustomerName(expense.getCustomer().getName());
////                    }
//                    return dto;
//                })
//                .toList();
//
//        // Calculate total pages
//        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
//        boolean lastPage = pageNumber >= totalPages - 1;
//
//        return new PaginatedResponse<>(
//                expenseDTOS,
//                pageNumber,
//                expenseList.size(),
//                totalCount,
//                totalPages,
//                lastPage
//        );
//    }

}