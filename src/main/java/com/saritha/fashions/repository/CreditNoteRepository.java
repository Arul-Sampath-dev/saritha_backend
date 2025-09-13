package com.saritha.fashions.repository;

import com.saritha.fashions.model.CreditNote;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CreditNoteRepository extends MongoRepository<CreditNote, String> {
    List<CreditNote> findByInvoiceIdOrderByCreatedAtDesc(String invoiceId);
    Optional<CreditNote> findTopByInvoiceIdOrderByCreatedAtDesc(String invoiceId);
}
