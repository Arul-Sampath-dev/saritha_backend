package com.saritha.fashions.controller;

import com.saritha.fashions.model.Expense;
import com.saritha.fashions.service.Interface.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    @PostMapping("/add")
    public ResponseEntity<Expense> addExpense(@RequestBody Expense expense) {
        return ResponseEntity.ok(expenseService.addExpense(expense));
    }

    @PostMapping("/add-employee-expense")
    public ResponseEntity<Expense> addEmployeeExpense(@RequestBody Expense expense) {
        return ResponseEntity.ok(expenseService.addEmployeeExpense(expense));
    }

    @Autowired
    private final ExpenseService expenseService;

    @GetMapping("/all")
    public ResponseEntity<List<Expense>> getAllExpenses() {
        return ResponseEntity.ok(expenseService.getAllExpenses());
    }

    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<Expense>> getExpensesByShop(@PathVariable String shopId) {
        return ResponseEntity.ok(expenseService.getExpensesByShopId(shopId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable String id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }
}
