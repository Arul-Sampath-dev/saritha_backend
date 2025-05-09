package com.saritha.fashions.repository;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends MongoRepository<Receipt, String > {

    List<Receipt> findByShopId(String shopId);

    Optional<Receipt> findByReceiptNo(String id);

//    Optional<Receipt> findByInvoice(Invoice invoice);

//    Optional<Receipt> findByReceiptId(String receiptId);
//    @NotNull Page<Receipt> findAll(@NotNull Pageable pageable);
}
