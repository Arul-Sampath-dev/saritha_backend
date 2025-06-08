package com.saritha.fashions.controller;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.model.Expense;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.expense.ExpenseDTO;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.service.Interface.ExpenseService;
import com.saritha.fashions.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/v1/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    @Autowired
    private final ExpenseService expenseService;

    @Autowired
    private final AuthUtil authUtil;

    @PostMapping("/add")
    public ResponseEntity<Expense> addExpense(@RequestBody Expense expense) {
        return ResponseEntity.ok(expenseService.addExpense(expense));
    }

    @PostMapping("/add-employee-expense")
    public ResponseEntity<Expense> addEmployeeExpense(@RequestBody Expense expense) {
        return ResponseEntity.ok(expenseService.addEmployeeExpense(expense));
    }

//    @GetMapping("/all")
//    public ResponseEntity<List<Expense>> getAllExpenses() {
//        return ResponseEntity.ok(expenseService.getAllExpenses());
//    }

    @GetMapping("/all")
    public ResponseEntity<PaginatedResponse<ExpenseDTO>> getAllExpenseFromParticularShop(
            @RequestParam(name = "term", required = false) String term,
            @RequestParam(name = "pageNumber" , defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_DATA_BY_EXPENSE_DATE, required = false) String sortBy,
            @RequestParam(name = "sortOrder" , defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate
    ) {
        String shopId = authUtil.loggedInUser().getAssignedShops().get(0).getId();
        PaginatedResponse<ExpenseDTO> expenseDTOPaginatedResponse = expenseService.getAllExpenseFromParticularShop(
                pageNumber, pageSize, sortBy, sortOrder, shopId, startDate, endDate, term);
        return new ResponseEntity<>(expenseDTOPaginatedResponse, HttpStatus.OK);
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
