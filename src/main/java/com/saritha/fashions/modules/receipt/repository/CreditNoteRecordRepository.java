package com.saritha.fashions.modules.receipt.repository;

import com.saritha.fashions.modules.receipt.model.CreditNoteRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Credit notes ("credit_notes" collection).
 */
@Repository
public interface CreditNoteRecordRepository extends MongoRepository<CreditNoteRecord, String> {
}
