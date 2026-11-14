package com.saritha.fashions.modules.receipt.repository;

import com.saritha.fashions.modules.receipt.model.ReceiptRecord;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Raw-document operations on "invoice", "receipt", "shops" and "customers" (no dependency
 * on other modules' models). Implemented with MongoTemplate.
 */
public interface ReceiptRecordRepositoryCustom {

    Optional<String> findShopIdByEmployeeId(String userId);

    /** Shop name and GSTIN. */
    Optional<ShopDetails> findShop(String shopId);

    /** The invoice with this invoice number in this shop. */
    Optional<Document> findInvoice(String invoiceId, String shopId);

    /**
     * Atomically applies the update to the invoice, only if it has no receipt yet, is not
     * delivered, and the expected fields still have the values read before (compare-and-set).
     *
     * @return true if the invoice was updated; false if it changed in the meantime
     */
    boolean updateInvoiceIfUnchanged(Object invoiceDbId, Map<String, Object> expectedFields, Update update);

    /** Inserts a raw receipt document (unique receiptNo); returns its _id. */
    Object insertReceipt(Document receipt);

    void deleteReceipt(Object receiptDbId);

    /** Adds the receipt number to customer.allReceiptIds ($addToSet). */
    void addReceiptToCustomer(Object customerDbId, String receiptNo);

    /** The receipt with this number; restricted to the shop when shopId is not null. */
    Optional<ReceiptRecord> findReceipt(String receiptNo, String shopId);

    /** The receipt linked to this invoice database id, if any. */
    Optional<ReceiptRecord> findReceiptByInvoiceDbId(String invoiceDbId);

    /** A page of receipts (all shops when shopId is null), newest first. */
    PageResult findReceipts(String shopId, int page, int size);

    /** Invoice number (invoiceId) for each invoice database id. */
    Map<String, String> findInvoiceNumbers(Collection<String> invoiceDbIds);

    /** Customer name / phone / email for the receipts' customer references, keyed by receipt id. */
    Map<String, CustomerSummary> findCustomersOfReceipts(Collection<String> receiptIds);

    record ShopDetails(String shopId, String shopName, String gstNumber) {
    }

    record CustomerSummary(String name, String phone, String email) {
    }

    record PageResult(List<ReceiptRecord> receipts, long total) {
    }
}
