package com.saritha.fashions.repository;

import com.saritha.fashions.model.Invoice;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends MongoRepository<Invoice, String > {

    List<Invoice> findByShopId(String shopId);

    @NotNull Optional<Invoice> findById(String id);

    boolean existsByInvoiceId(String invoiceId);

    Optional<Invoice> findByInvoiceId(String invoiceId);

    Page<Invoice> findByShopId(String shopId, Pageable pageable);

    boolean existsByInvoiceIdForCloudBucket(String invoiceId);

    Page<Invoice> findByAssignedTailorId(String tailorId, Pageable pageable);


}
