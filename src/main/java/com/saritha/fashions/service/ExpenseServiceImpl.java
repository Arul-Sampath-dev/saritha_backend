package com.saritha.fashions.service;

import com.saritha.fashions.model.Expense;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.TravelDetails;
import com.saritha.fashions.repository.ExpenseRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.service.Interface.ExpenseService;
import com.saritha.fashions.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private ShopRepository shopRepository;

    @Override
    @Transactional
    public Expense addExpense(Expense expense) {
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

            // Summing up all bills
            Optional<Shop> shopOptional = shopRepository.findById(shopId);
            double totalExpense = shopOptional.map(Shop::getTotalExpense).orElse(0.0);
            expense.setExpenseAmount(totalExpense+expense.getExpenseAmount());

            // Set default payment method
            if (expense.getPaymentMethod() == null) {
                expense.setPaymentMethod("CASH");
            }

            expense.setExpenseType(expense.getExpenseType());
            Expense savedExpense = expenseRepository.save(expense);
            if (shopOptional.isPresent()) {
                Shop shop = shopOptional.get();
                List<Expense> allExpenses = shop.getAllExpense() == null ? new ArrayList<Expense>() : shop.getAllExpense();
                allExpenses.add(savedExpense);
                shop.setAllExpense(allExpenses);
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

            // Summing up all bills
            Optional<Shop> shopOptional = shopRepository.findById(shopId);
            double totalExpense = shopOptional.map(Shop::getTotalExpense).orElse(0.0);
            expense.setExpenseAmount(totalExpense+expense.getExpenseAmount());

            // Set default payment method
            if (expense.getPaymentMethod() == null) {
                expense.setPaymentMethod("CASH");
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
                shopRepository.save(shop);
            }
            return savedExpense;

        } catch (Exception e) {
            throw new RuntimeException("Invalid date format", e);
        }
    }
}