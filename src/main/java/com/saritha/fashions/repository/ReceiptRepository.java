package com.saritha.fashions.repository;

import com.saritha.fashions.model.Invoice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends MongoRepository<Invoice, String > {

    List<Invoice> findByShopId(String shopId);

    Optional<Invoice> findByReceiptNo(String id);
//    @NotNull Page<Invoice> findAll(@NotNull Pageable pageable);
}
