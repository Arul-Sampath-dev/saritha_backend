package com.saritha.fashions.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Expense;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.expense.ExpenseDTO;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.repository.ExpenseRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.service.Interface.ExpenseService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
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

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    @Transactional
    public Expense addExpense(Expense expense) {
        try {
            User user = authUtil.loggedInUser();
            Shop shopFromDb = user.getAssignedShops().get(0);
            String shopId = user.getAssignedShops().get(0).getId();
            expense.setShopId(shopId);

//            if(!(shopFromDb.getPettyCash()-shopFromDb.getTotalExpense() > expense.getExpenseAmount())) {
//                throw new APIException("You do not have enough petty cash to spend, please contact your owner");
//            }
            if (expense.getExpenseDate() != null) {
                // Convert backend date to IST
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
                formatter.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata")); // Set IST timezone

                String expenseDateStr = formatter.format(expense.getExpenseDate()); // Convert to string
                expense.setExpenseDate(formatter.parse(expenseDateStr)); // Convert back to Date object
            }

            // Summing up all bills
            Optional<Shop> shopOptional = shopRepository.findById(shopId);
            expense.setExpenseAmount(expense.getExpenseAmount());
            double totalExpense = shopOptional.map(Shop::getTotalExpense).orElse(0.0);
            totalExpense += expense.getExpenseAmount();

            // Set default payment method
            if (expense.getPaymentMethod() == null) {
                expense.setPaymentMethod("CASH-ByDefault");
            } else {
                expense.setPaymentMethod(expense.getPaymentMethod());
            }

            expense.setExpenseType(expense.getExpenseType());
            Expense savedExpense = expenseRepository.save(expense);
            if (shopOptional.isPresent()) {
                Shop shop = shopOptional.get();
                List<Expense> allExpenses = shop.getAllExpense() == null ? new ArrayList<Expense>() : shop.getAllExpense();
                allExpenses.add(savedExpense);
                shop.setAllExpense(allExpenses);
                shop.setTotalExpense(totalExpense);
                shopRepository.save(shop);
            }
            return savedExpense;

        } catch (Exception e) {
            throw new RuntimeException("Invalid date format", e);
        }

    }


    @Override
    public List<Expense> getAllExpenses() {
        User user = authUtil.loggedInUser();
        String shopId = user.getAssignedShops().get(0).getId();

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

    @Override
    @Transactional
    public Expense addEmployeeExpense(Expense expense) {
        try {
            User user = authUtil.loggedInUser();
            String shopId = user.getAssignedShops().get(0).getId();
            expense.setShopId(shopId);
            if (expense.getExpenseDate() != null) {
                // Convert backend date to IST
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
                formatter.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata")); // Set IST timezone

                String expenseDateStr = formatter.format(expense.getExpenseDate()); // Convert to string
                expense.setExpenseDate(formatter.parse(expenseDateStr)); // Convert back to Date object
            }

            // Summing up all bill
            Optional<Shop> shopOptional = shopRepository.findById(shopId);
            expense.setExpenseAmount(expense.getExpenseAmount());
            double totalExpense = shopOptional.map(Shop::getTotalExpense).orElse(0.0);
            totalExpense += expense.getExpenseAmount();

            // Set default payment method
            if (expense.getPaymentMethod() == null) {
                expense.setPaymentMethod("CASH-ByDefault");
            } else {
                expense.setPaymentMethod(expense.getPaymentMethod());
            }

            expense.setExpenseType(expense.getExpenseType());
            expense.setRefreshmentDetails(expense.getRefreshmentDetails());
            expense.setMiscellaneousDetails(expense.getMiscellaneousDetails());
            expense.setTravelDetails(expense.getTravelDetails());

            Expense savedExpense = expenseRepository.save(expense);
            if (shopOptional.isPresent()) {
                Shop shop = shopOptional.get();
                List<Expense> allExpenses = shop.getAllExpense() == null ? new ArrayList<Expense>() : shop.getAllExpense();
                allExpenses.add(savedExpense);
                shop.setAllExpense(allExpenses);
                shop.setTotalExpense(totalExpense);
                shopRepository.save(shop);
            }
            return savedExpense;

        } catch (Exception e) {
            throw new RuntimeException("Invalid date format", e);
        }
    }

    @Override
    public PaginatedResponse<ExpenseDTO> getAllExpenseFromParticularShop(Integer pageNumber, Integer pageSize,
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
        Sort.Direction direction = sortOrder.equalsIgnoreCase("desc") ?
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
                    Criteria.where("invoiceId").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }

        // Get total count
        long totalCount = mongoTemplate.count(query, Expense.class);

        // Apply pagination and execute query
        query.with(pageable);
        List<Expense> expenseList = mongoTemplate.find(query, Expense.class);

        if (expenseList.isEmpty()) {
            throw new APIException("No Expense found");
        }

        // Map to DTOs
        List<ExpenseDTO> expenseDTOS = expenseList.stream()
                .map(expense -> {
                    ExpenseDTO dto = modelMapper.map(expense, ExpenseDTO.class);
//                    if (expense.get() != null) {
//                        dto.setCustomerName(expense.getCustomer().getName());
//                    }
                    return dto;
                })
                .toList();

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

}