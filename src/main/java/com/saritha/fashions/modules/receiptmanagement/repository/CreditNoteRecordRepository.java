package com.saritha.fashions.modules.receiptmanagement.repository;

import com.saritha.fashions.modules.receiptmanagement.model.CreditNoteRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Credit notes ("credit_notes" collection).
 */
@Repository
public interface CreditNoteRecordRepository extends MongoRepository<CreditNoteRecord, String> {
}
