package com.saritha.fashions.modules.invoice.repository;

import com.saritha.fashions.modules.invoice.model.InvoiceCustomer;

import java.util.Optional;

/**
 * Custom operations on "shops" and "customers" that invoice creation needs.
 * Implemented by InvoiceRecordRepositoryCustomImpl with MongoTemplate.
 */
public interface InvoiceRecordRepositoryCustom {

    /**
     * Loads ONLY the shop fields an invoice needs (id, name, state, GSTIN) for the shop
     * whose employeeIds contains the user. Read once per invoice.
     */
    Optional<ShopInvoiceProfile> findShopProfileByEmployeeId(String userId);

    /**
     * Finds the customer with this phone, or creates it, in ONE atomic upsert.
     * An existing customer's details are NOT changed (same as the old flow).
     *
     * @param newCustomer values used only if a new customer is created
     */
    CustomerLookupResult findOrCreateCustomerByPhone(InvoiceCustomer newCustomer);

    boolean customerIdExists(String customerId);

    /**
     * Adds the invoice number to the customer's allInvoiceIds ($addToSet, so no duplicates
     * and no read-modify-write of the whole customer).
     */
    void addInvoiceToCustomer(String customerDbId, String invoiceId);

    // ------------------------------------------------------------------ reads

    boolean shopExists(String shopId);

    /**
     * One page of the shop's invoices matching the filter, with their customers loaded in one
     * batched query (not one query per invoice).
     */
    InvoicePage findInvoices(String shopId, InvoiceFilter filter, int page, int size);

    /** The raw invoice document (for edit / delete compare-and-set), if it belongs to the shop. */
    Optional<org.bson.Document> findInvoiceDocument(String invoiceId, String shopId);

    // ------------------------------------------------------------------ edit / delete

    /**
     * Atomically applies the update if the invoice still has no receipt, is not delivered and
     * every expected field still has the value read before the edit.
     *
     * @param expectedFields field → value read before the edit; pass the raw "items" list to
     *                       detect ANY concurrent item change (status, assignment, measurements)
     * @return true if updated; false if the invoice changed meanwhile
     */
    boolean updateInvoiceIfUnchanged(Object invoiceDbId, java.util.Map<String, Object> expectedFields,
                                     org.springframework.data.mongodb.core.query.Update update);

    /** Stores the deleted invoice in "deleted_invoices" (same shape as model.DeletedInvoice); returns its _id. */
    Object archiveDeletedInvoice(org.bson.Document invoice, String deletedBy, String reason);

    void removeArchivedInvoice(Object archiveId);

    /**
     * Deletes the invoice only if it still has no receipt, is not delivered and every expected
     * field is unchanged; true if deleted.
     */
    boolean deleteInvoiceIfOpen(Object invoiceDbId, java.util.Map<String, Object> expectedFields);

    /** Maps a raw invoice document to the model WITHOUT loading its customer (customer stays null). */
    com.saritha.fashions.modules.invoice.model.InvoiceRecord toInvoiceRecord(org.bson.Document raw);

    /** Loads a customer by its database id (the id inside the invoice's customer DBRef). */
    Optional<InvoiceCustomer> findCustomer(Object customerDbId);

    /** Removes the invoice number from the customer's allInvoiceIds. */
    void removeInvoiceFromCustomer(Object customerDbId, String invoiceId);

    /** True if a customer other than the given one already has this phone number. */
    boolean customerPhoneUsedByOther(String phone, Object customerDbId);

    /**
     * Applies the update to the customer record.
     *
     * @return false if the customer no longer exists
     */
    boolean updateCustomer(Object customerDbId, org.springframework.data.mongodb.core.query.Update update);

    /**
     * Invoice list filter (null fields = no filter).
     *
     * @param delivered   true = delivered only, false = not yet delivered, null = both
     * @param search      text in the invoice number, customer name or customer phone (literal, case-insensitive)
     * @param specialOnly only invoices with a special-category item (Embroidery, Lining Material, ...)
     */
    record InvoiceFilter(java.util.Date from, java.util.Date before, Boolean delivered, String paymentStatus,
                         String search, boolean specialOnly, boolean oldestFirst) {
    }

    record InvoicePage(java.util.List<com.saritha.fashions.modules.invoice.model.InvoiceRecord> invoices,
                       java.util.Map<String, InvoiceCustomer> customersByInvoiceDbId, long total) {
    }

    /**
     * Shop fields needed to build an invoice.
     */
    record ShopInvoiceProfile(String shopId, String shopName, String shopState, String gstNumber) {
    }

    /**
     * @param created true if the customer was created by this call
     */
    record CustomerLookupResult(InvoiceCustomer customer, boolean created) {
    }
}
