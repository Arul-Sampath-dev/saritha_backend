package com.saritha.fashions.controller;

import com.saritha.fashions.model.FinancialRecord;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.model.Order;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.OrderRepository;
import com.saritha.fashions.service.Interface.FinancialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/dashboard")
public class DashboardController {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FinancialService financialService;

    // Get All Invoices for All Shops (Shop Owner Only)
    @GetMapping("/all-invoices")
    public ResponseEntity<List<Receipt>> getAllInvoices() {
        return ResponseEntity.ok(invoiceRepository.findAll());
    }

    // Get Orders for All Shops (Shop Owner Only)
    @GetMapping("/all-orders")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    // Get Monthly Financial Summary for a Shop
    @GetMapping("/financial-summary/shop/{shopId}")
    public ResponseEntity<Map<String, Double>> getMonthlySummary(@PathVariable String shopId) {
        List<FinancialRecord> records = financialService.getFinancialRecordsByShop(shopId);

        double totalIncome = records.stream().mapToDouble(FinancialRecord::getTotalIncome).sum();
        double totalExpenses = records.stream().mapToDouble(FinancialRecord::getTotalExpenses).sum();
        double totalPettyCash = records.stream().mapToDouble(FinancialRecord::getPettyCash).sum();
        double netProfit = totalIncome - totalExpenses;

        Map<String, Double> summary = new HashMap<>();
        summary.put("totalIncome", totalIncome);
        summary.put("totalExpenses", totalExpenses);
        summary.put("totalPettyCash", totalPettyCash);
        summary.put("netProfit", netProfit);

        return ResponseEntity.ok(summary);
    }

    // Get Combined Financial Summary for All Shops
    @GetMapping("/financial-summary/all")
    public ResponseEntity<Map<String, Double>> getTotalFinancialSummary() {
        double totalIncome = financialService.calculateTotalProfitForAllShops();
        double totalExpenses = financialService.getTotalExpensesForAllShops();
        double totalPettyCash = financialService.getTotalPettyCashForAllShops();
        double netProfit = totalIncome - totalExpenses;

        Map<String, Double> summary = new HashMap<>();
        summary.put("totalIncome", totalIncome);
        summary.put("totalExpenses", totalExpenses);
        summary.put("totalPettyCash", totalPettyCash);
        summary.put("netProfit", netProfit);

        return ResponseEntity.ok(summary);
    }
}
