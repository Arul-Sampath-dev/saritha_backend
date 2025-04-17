package com.saritha.fashions.repository;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends MongoRepository<Invoice, String > {
    List<Invoice> findByShopId(String shopId);

    boolean existsByInvoiceId(String invoiceId);

    Optional<Invoice> findByReceipt(Invoice invoice);

    Optional<Invoice> findByInvoiceId(String invoiceId);
    Page<Invoice> findByShopId(String shopId, Pageable pageable);
}
