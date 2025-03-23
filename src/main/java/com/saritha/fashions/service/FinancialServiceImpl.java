package com.saritha.fashions.service;

import com.saritha.fashions.model.FinancialRecord;
import com.saritha.fashions.repository.FinancialRecordRepository;
import com.saritha.fashions.service.Interface.FinancialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class FinancialServiceImpl implements FinancialService {

    @Autowired
    private FinancialRecordRepository financialRecordRepository;

    @Override
    public FinancialRecord recordDailyFinancials(String shopId, double income, double expenses, double pettyCash) {
        LocalDate today = LocalDate.now();
        List<FinancialRecord> existingRecords = financialRecordRepository.findByShopIdAndDate(shopId, today);

        FinancialRecord record;
        if (!existingRecords.isEmpty()) {
            record = existingRecords.get(0); // Update existing record
            record.setTotalIncome(record.getTotalIncome() + income);
            record.setTotalExpenses(record.getTotalExpenses() + expenses);
            record.setPettyCash(record.getPettyCash() + pettyCash);
        } else {
            // Create a new financial record
            record = new FinancialRecord(null, shopId, today, income, expenses, pettyCash, income - expenses);
        }

        record.setNetProfit(record.getTotalIncome() - record.getTotalExpenses());
        return financialRecordRepository.save(record);
    }

    @Override
    public List<FinancialRecord> getFinancialRecordsByShop(String shopId) {
        return financialRecordRepository.findByShopId(shopId);
    }

    @Override
    public List<FinancialRecord> getFinancialRecordsByDate(LocalDate date) {
        return financialRecordRepository.findByDate(date);
    }

    @Override
    public double calculateTotalProfitForShop(String shopId) {
        List<FinancialRecord> records = financialRecordRepository.findByShopId(shopId);
        return records.stream().mapToDouble(FinancialRecord::getNetProfit).sum();
    }

    @Override
    public double calculateTotalProfitForAllShops() {
        List<FinancialRecord> records = financialRecordRepository.findAll();
        return records.stream().mapToDouble(FinancialRecord::getNetProfit).sum();
    }

    @Override
    public double getTotalExpensesForAllShops() {
        List<FinancialRecord> records = financialRecordRepository.findAll();
        return records.stream().mapToDouble(FinancialRecord::getTotalExpenses).sum();
    }

    @Override
    public double getTotalPettyCashForAllShops() {
        List<FinancialRecord> records = financialRecordRepository.findAll();
        return records.stream().mapToDouble(FinancialRecord::getPettyCash).sum();
    }

}
