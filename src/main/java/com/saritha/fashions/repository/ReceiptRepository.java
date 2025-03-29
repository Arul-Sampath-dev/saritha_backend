package com.saritha.fashions.repository;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends MongoRepository<Receipt, String > {

    List<Receipt> findByShopId(String shopId);

    Optional<Receipt> findByReceiptNo(String id);
//    @NotNull Page<Receipt> findAll(@NotNull Pageable pageable);
}
