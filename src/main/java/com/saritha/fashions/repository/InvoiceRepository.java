package com.saritha.fashions.repository;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InvoiceRepository extends MongoRepository<Invoice, String > {
    List<Invoice> findByShopId(String shopId);

    boolean existsByInvoiceId(String invoiceId);
}
