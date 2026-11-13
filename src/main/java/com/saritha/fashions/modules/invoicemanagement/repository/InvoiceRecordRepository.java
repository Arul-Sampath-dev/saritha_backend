package com.saritha.fashions.modules.invoicemanagement.repository;

import com.saritha.fashions.modules.invoicemanagement.model.InvoiceRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Mongo repository for the InvoiceManagement module ("invoice" collection).
 *
 * Named differently from com.saritha.fashions.repository.InvoiceRepository so the two
 * Spring beans do not clash. Shop / customer operations needed by invoice creation come
 * from InvoiceRecordRepositoryCustom.
 */
@Repository
public interface InvoiceRecordRepository
        extends MongoRepository<InvoiceRecord, String>, InvoiceRecordRepositoryCustom {

    Optional<InvoiceRecord> findByInvoiceIdAndShopId(String invoiceId, String shopId);
}
