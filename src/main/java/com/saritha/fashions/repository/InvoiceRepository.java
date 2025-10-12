package com.saritha.fashions.repository;

import com.saritha.fashions.model.Invoice;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends MongoRepository<Invoice, String > {

    @Query(value = "{ 'shopId': ?0, 'items.itemName': { $nin: ?1 }, 'orderStatus': { $in: ?2 } }")
    Page<Invoice> findByShopIdAndItemsItemNameNotInAndOrderStatusIn(String shopId, List<String> excludedItems, List<String> allowedStatuses, Pageable pageable);

    @Query(value = "{ 'shopId': ?0, " +
            "'items.itemName': { $nin: ?1 }, " +
            "'orderStatus': { $in: ?2 }, " +
            "'items': { $elemMatch: { 'itemOrderStatus': 'ORDER_ACCEPTED' } } }")
    Page<Invoice> findByShopIdAndItemsItemNameNotInAndOrderStatusInWithAtLeastOneOrderAcceptedItem(
            String shopId,
            List<String> excludedItems,
            List<String> allowedStatuses,
            Pageable pageable);

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

    // Count method
    @Aggregation(pipeline = {
            "{ '$match': { " +
                    "'items.assignedTo': ?0, " +
                    "'orderStatus': { '$ne': 'ORDER_DELIVERED' } " +
                    "} }",
            "{ '$addFields': { " +
                    "'filteredItems': { " +
                    "'$filter': { " +
                    "'input': '$items', " +
                    "'as': 'item', " +
                    "'cond': { '$eq': ['$$item.assignedTo', ?0] } " +
                    "} " +
                    "} " +
                    "} }",
            "{ '$match': { 'filteredItems': { '$not': { '$size': 0 } } } }",
            "{ '$count': 'total' }"
    })
    Long countAssignedInvoicesExcludingDelivered(String tailorId);

    // Paginated query with sorting
    @Aggregation(pipeline = {
            "{ '$match': { " +
                    "'items.assignedTo': ?0, " +
                    "'orderStatus': { '$ne': 'ORDER_DELIVERED' } " +
                    "} }",
            "{ '$addFields': { " +
                    "'filteredItems': { " +
                    "'$filter': { " +
                    "'input': '$items', " +
                    "'as': 'item', " +
                    "'cond': { '$eq': ['$$item.assignedTo', ?0] } " +
                    "} " +
                    "} " +
                    "} }",
            "{ '$match': { 'filteredItems': { '$not': { '$size': 0 } } } }",
            "{ '$addFields': { 'items': '$filteredItems' } }",
            "{ '$project': { 'filteredItems': 0 } }",
            "{ '$sort': { ?1: ?2 } }",
            "{ '$skip': ?3 }",
            "{ '$limit': ?4 }"
    })
    List<Invoice> findAssignedInvoicesExcludingDelivered(
            String tailorId,
            String sortField,
            int sortDirection,
            long skip,
            int limit);

    @Aggregation(pipeline = {
            "{ '$match': { " +
                    "'invoiceId': ?0, " +
                    "'orderStatus': { '$ne': 'ORDER_DELIVERED' }, " +
                    "'items.assignedTo': ?1 " +
                    "} }",
            "{ '$addFields': { " +
                    "'filteredItems': { " +
                    "'$filter': { " +
                    "'input': '$items', " +
                    "'as': 'item', " +
                    "'cond': { '$eq': ['$$item.assignedTo', ?1] } " +
                    "} " +
                    "} " +
                    "} }",
            "{ '$match': { 'filteredItems': { '$not': { '$size': 0 } } } }",
            "{ '$addFields': { 'items': '$filteredItems' } }",
            "{ '$project': { 'filteredItems': 0 } }"
    })
    Optional<Invoice> findInvoiceWithAssignedItemsExcludingDelivered(
            String invoiceId,
            String tailorId
    );

    @Aggregation(pipeline = {
            "{ '$match': { " +
                    "'invoiceId': ?0, " +
                    "'orderStatus': { '$ne': 'ORDER_DELIVERED' }, " +
                    "'items.assignedToWhichShopAssistant': ?1 " +
                    "} }",
            "{ '$addFields': { " +
                    "'filteredItems': { " +
                    "'$filter': { " +
                    "'input': '$items', " +
                    "'as': 'item', " +
                    "'cond': { '$eq': ['$$item.assignedToWhichShopAssistant', ?1] } " +
                    "} " +
                    "} " +
                    "} }",
            "{ '$match': { 'filteredItems': { '$not': { '$size': 0 } } } }",
            "{ '$addFields': { 'items': '$filteredItems' } }",
            "{ '$project': { 'filteredItems': 0 } }"
    })
    Optional<Invoice> findInvoiceWithAssignedItemsExcludingDeliveredForShopAssistant(
            String invoiceId,
            String tailorId
    );

    @Aggregation(pipeline = {
            "{ '$match': { " +
                    "'invoiceId': ?0, " +
                    "'orderStatus': { '$ne': 'ORDER_DELIVERED' }, " +
                    "'shopId': ?1 " +
                    "} }"
    })
    Optional<Invoice> findInvoiceWithAssignedItemsExcludingDeliveredForTailorMaster(
            String invoiceId,
            String shopId
    );

    @Aggregation(pipeline = {
            "{ '$match': { " +
                    "'items.assignedToWhichShopAssistant': ?0, " +
                    "'orderStatus': { '$ne': 'ORDER_DELIVERED' } " +
                    "} }",
            "{ '$addFields': { " +
                    "'filteredItems': { " +
                    "'$filter': { " +
                    "'input': '$items', " +
                    "'as': 'item', " +
                    "'cond': { '$eq': ['$$item.assignedToWhichShopAssistant', ?0] } " +
                    "} " +
                    "} " +
                    "} }",
            "{ '$match': { 'filteredItems': { '$not': { '$size': 0 } } } }",
            "{ '$count': 'total' }"
    })
    Long countAssignedInvoicesExcludingDeliveredForShopAssistant(String tailorId);

    @Aggregation(pipeline = {
            "{ '$match': { " +
                    "'items.assignedToWhichShopAssistant': ?0, " +
                    "'orderStatus': { '$ne': 'ORDER_DELIVERED' } " +
                    "} }",
            "{ '$addFields': { " +
                    "'filteredItems': { " +
                    "'$filter': { " +
                    "'input': '$items', " +
                    "'as': 'item', " +
                    "'cond': { '$eq': ['$$item.assignedToWhichShopAssistant', ?0] } " +
                    "} " +
                    "} " +
                    "} }",
            "{ '$match': { 'filteredItems': { '$not': { '$size': 0 } } } }",
            "{ '$addFields': { 'items': '$filteredItems' } }",
            "{ '$project': { 'filteredItems': 0 } }",
            "{ '$sort': { ?1: ?2 } }",
            "{ '$skip': ?3 }",
            "{ '$limit': ?4 }"
    })
    List<Invoice> findAssignedInvoicesExcludingDeliveredForShopAssistant(
            String tailorId,
            String sortField,
            int sortDirection,
            long skip,
            int limit);

//    Testing the new version data

    @Query(value = "{ 'shopId': ?0, 'items.itemName': { $nin: ?1 }, 'items.itemOrderStatus': { $in: ?2 } }")
    Page<Invoice> findByShopIdAndItemsItemNameNotInAndItemsItemOrderStatusIn(
            String shopId,
            List<String> excludedItems,
            List<String> allowedItemStatuses,
            Pageable pageable);

}
