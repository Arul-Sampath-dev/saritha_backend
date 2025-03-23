package com.saritha.fashions.controller;

import com.saritha.fashions.model.FinancialRecord;
import com.saritha.fashions.service.Interface.FinancialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/api/financials")
public class FinancialController {

    @Autowired
    private FinancialService financialService;

    // Record Daily Financials
    @PostMapping("/record")
    public ResponseEntity<?> recordFinancials(
            @RequestParam String shopId,
            @RequestParam double income,
            @RequestParam double expenses,
            @RequestParam double pettyCash) {
        FinancialRecord record = financialService.recordDailyFinancials(shopId, income, expenses, pettyCash);
        return ResponseEntity.ok(record);
    }

    // Fetch Financial Records by Shop
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<FinancialRecord>> getRecordsByShop(@PathVariable String shopId) {
        return ResponseEntity.ok(financialService.getFinancialRecordsByShop(shopId));
    }

    // Fetch Financial Records by Date
    @GetMapping("/date/{date}")
    public ResponseEntity<List<FinancialRecord>> getRecordsByDate(@PathVariable String date) {
        LocalDate localDate = LocalDate.parse(date);
        return ResponseEntity.ok(financialService.getFinancialRecordsByDate(localDate));
    }

    // Calculate Total Profit for a Shop
    @GetMapping("/profit/shop/{shopId}")
    public ResponseEntity<Double> getTotalProfitForShop(@PathVariable String shopId) {
        return ResponseEntity.ok(financialService.calculateTotalProfitForShop(shopId));
    }

    // Calculate Total Profit for All Shops
    @GetMapping("/profit/all")
    public ResponseEntity<Double> getTotalProfitForAllShops() {
        return ResponseEntity.ok(financialService.calculateTotalProfitForAllShops());
    }
}
