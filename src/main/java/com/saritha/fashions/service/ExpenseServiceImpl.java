package com.saritha.fashions.service;

import com.saritha.fashions.model.Expense;
import com.saritha.fashions.repository.ExpenseRepository;
import com.saritha.fashions.service.Interface.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Override
    public Expense addExpense(Expense expense) {
        expense.setExpenseDate(new Date());  // Set current timestamp
        expense.setPaymentMethod("CASH");
        return expenseRepository.save(expense);
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

            if (updatedExpense.getMaterialExpensesBill() != null) {
                expense.setMaterialExpensesBill(updatedExpense.getMaterialExpensesBill());
            } else if (updatedExpense.getRentBill() != null) {
                expense.setRentBill(updatedExpense.getRentBill());
            } else if (updatedExpense.getElectricityBill() != null) {
                expense.setElectricityBill(updatedExpense.getElectricityBill());
            }

            expense.setPaymentMethod(updatedExpense.getPaymentMethod());
            return expenseRepository.save(expense);
        }
        return null;
    }
}