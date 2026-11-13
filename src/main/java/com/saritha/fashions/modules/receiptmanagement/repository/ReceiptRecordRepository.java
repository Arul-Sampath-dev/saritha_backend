package com.saritha.fashions.modules.receiptmanagement.repository;

import com.saritha.fashions.modules.receiptmanagement.model.ReceiptRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Mongo repository for the ReceiptManagement module ("receipt" collection), plus the
 * invoice / shop / customer operations receipts need (ReceiptRecordRepositoryCustom).
 *
 * Named differently from com.saritha.fashions.repository.ReceiptRepository so the two
 * Spring beans do not clash.
 */
@Repository
public interface ReceiptRecordRepository
        extends MongoRepository<ReceiptRecord, String>, ReceiptRecordRepositoryCustom {
}
