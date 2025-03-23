package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.FinancialRecord;
import java.util.List;
import java.time.LocalDate;

public interface FinancialService {
    FinancialRecord recordDailyFinancials(String shopId, double income, double expenses, double pettyCash);
    List<FinancialRecord> getFinancialRecordsByShop(String shopId);
    List<FinancialRecord> getFinancialRecordsByDate(LocalDate date);
    double calculateTotalProfitForShop(String shopId);
    double calculateTotalProfitForAllShops();
    double getTotalExpensesForAllShops();
    double getTotalPettyCashForAllShops();
}
