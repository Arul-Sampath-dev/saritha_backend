package com.saritha.fashions.repository;

import com.saritha.fashions.model.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends MongoRepository<Receipt, String > {

    List<Receipt> findByShopId(String shopId);

    Optional<Receipt> findByReceiptNo(String id);

    Page<Receipt> findByShopId(String shopId, Pageable pageable);

    Optional<Receipt> findByInvoiceId(String invoiceId);

//    Optional<Receipt> findByInvoice(Invoice invoice);

//    Optional<Receipt> findByReceiptId(String receiptId);
//    @NotNull Page<Receipt> findAll(@NotNull Pageable pageable);
}
