package com.saritha.fashions.repository;

import com.saritha.fashions.model.Invoice;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends MongoRepository<Invoice, String > {

//    @Query(value = "{ 'shopId': ?0, 'items.itemName': { $nin: ?1 } }")
//    Page<Invoice> findByShopIdAndItemsItemNameNotIn(String shopId, List<String> excludedItems, Pageable pageable);

    @Query(value = "{ 'shopId': ?0, 'items.itemName': { $nin: ?1 }, 'orderStatus': { $in: ?2 } }")
    Page<Invoice> findByShopIdAndItemsItemNameNotInAndOrderStatusIn(String shopId, List<String> excludedItems, List<String> allowedStatuses, Pageable pageable);

    @Query(value = "{ 'shopId': ?0, 'items.itemName': { $in: ?1 } }")
    Page<Invoice> findByShopIdAndItemsItemNameIn(String shopId, List<String> includedItems, Pageable pageable);

    @Query(value = "{ 'shopId': ?0, 'items.itemName': { $in: ?1 } }")
    Page<Invoice> findInvoicesWithItems(
            String shopId,
            List<String> itemNames,
            Pageable pageable);

    List<Invoice> findByShopId(String shopId);

    @NotNull Optional<Invoice> findById(String id);

    boolean existsByInvoiceId(String invoiceId);

    Optional<Invoice> findByInvoiceId(String invoiceId);

    Page<Invoice> findByShopId(String shopId, Pageable pageable);

    boolean existsByInvoiceIdForCloudBucket(String invoiceId);

    Page<Invoice> findByAssignedTailorId(String tailorId, Pageable pageable);


    Invoice deleteByInvoiceId(String invoiceId);

    List<Invoice> findByShopIdAndAssignedTailorId(String shopId, String userId);
}
