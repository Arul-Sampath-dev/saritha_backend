package com.saritha.fashions.repository;

import com.saritha.fashions.model.FinancialRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDate;
import java.util.List;

public interface FinancialRecordRepository extends MongoRepository<FinancialRecord, String> {
    List<FinancialRecord> findByShopIdAndDate(String shopId, LocalDate date);
    List<FinancialRecord> findByShopId(String shopId);
    List<FinancialRecord> findByDate(LocalDate date);
}
