package com.saritha.fashions.service;

import com.saritha.fashions.model.Expense;
import com.saritha.fashions.repository.ExpenseRepository;
import com.saritha.fashions.service.Interface.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Override
    public Expense addExpense(Expense expense) {
        try {
            if (expense.getExpenseDate() != null) {
                // Convert backend date to IST
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
                formatter.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata")); // Set IST timezone

                String expenseDateStr = formatter.format(expense.getExpenseDate()); // Convert to string
                expense.setExpenseDate(formatter.parse(expenseDateStr)); // Convert back to Date object
            }

            // Summing up all bills
            double totalAmount = expense.getRentBill() + expense.getElectricityBill() + expense.getMaterialExpensesBill();
            expense.setAmount(totalAmount);

            // Set default payment method
            if (expense.getPaymentMethod() == null) {
                expense.setPaymentMethod("CASH");
            }

            // Set description based on non-zero values
            StringBuilder description = new StringBuilder();
            if (expense.getMaterialExpensesBill() > 0) {
                description.append("Material, ");
            }
            if (expense.getRentBill() > 0) {
                description.append("Rent, ");
            }
            if (expense.getElectricityBill() > 0) {
                description.append("Electricity, ");
            }

            if (!description.isEmpty()) {
                description.setLength(description.length() - 2); // Remove last comma
            }

            expense.setDescription(description.toString());

            return expenseRepository.save(expense);

        } catch (Exception e) {
            throw new RuntimeException("Invalid date format", e);
        }

    }


    @Override
    public List<Expense> getAllExpenses() {
        return expenseRepository.findAll();
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
        Optional<Expense> existingExpense = expenseRepository.findById(id);
        if (existingExpense.isPresent()) {
            Expense expense = existingExpense.get();
           
            expense.setAmount(updatedExpense.getAmount());
            expense.setExpenseDate(updatedExpense.getExpenseDate());

            if (expense.getMaterialExpensesBill() > 0) {
                expense.setMaterialExpensesBill(updatedExpense.getMaterialExpensesBill());
            } else if (expense.getRentBill() > 0) {
                expense.setRentBill(updatedExpense.getRentBill());
            } else if (expense.getElectricityBill() > 0) {
                expense.setElectricityBill(updatedExpense.getElectricityBill());
            }

            expense.setPaymentMethod(updatedExpense.getPaymentMethod());
            return expenseRepository.save(expense);
        }
        return null;
    }
}