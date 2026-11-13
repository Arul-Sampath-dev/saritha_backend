package com.saritha.fashions.modules.receiptmanagement.service;

import com.mongodb.DBRef;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.core.activity.ActivityEntry;
import com.saritha.fashions.modules.core.activity.ActivityLogService;
import com.saritha.fashions.modules.core.activity.ActivityType;
import com.saritha.fashions.modules.receiptmanagement.exception.ReceiptConflictException;
import com.saritha.fashions.modules.receiptmanagement.model.ReceiptRecord;
import com.saritha.fashions.modules.receiptmanagement.payload.ReceiptResponse;
import com.saritha.fashions.modules.receiptmanagement.repository.ReceiptRecordRepository;
import com.saritha.fashions.modules.receiptmanagement.repository.ReceiptRecordRepositoryCustom.CustomerSummary;
import com.saritha.fashions.modules.receiptmanagement.repository.ReceiptRecordRepositoryCustom.ShopDetails;
import com.saritha.fashions.modules.core.numbering.DocumentNumberService;
import com.saritha.fashions.modules.core.numbering.DocumentType;
import com.saritha.fashions.util.DateTimeUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Receipt generation, ported from InvoiceServiceImpl.generateReceiptByInvoice
 * (POST /v1/api/receipt/download/receipt).
 *
 * Steps:
 *  1. Shop owner only; the invoice must belong to their shop (old: any shop).
 *  2. If the invoice already has a receipt, return it unchanged (idempotent, as before).
 *  3. The invoice must be FULLY PAID (old: receipt and invoice were marked "Paid" even with a
 *     balance due) and every item ORDER_COMPLETED (old: no check).
 *  4. Take the next receipt number, insert the receipt (a full copy of the invoice items and
 *     amounts, same document layout as before).
 *  5. Atomically link it to the invoice and mark the invoice delivered, only if the invoice
 *     still has no receipt and unchanged amounts. If another request generated a receipt at
 *     the same moment, ours is deleted and theirs is returned (old: two receipts possible).
 *  6. Follow-ups (logged, never failing the request): customer.allReceiptIds ($addToSet),
 *     employee activity ORDER_DELIVERED, customer WhatsApp / email with the receipt link.
 *
 * Not done any more: copying the receipt into shops.allReceipts and re-saving the whole shop
 * and customer documents (unbounded growth; overwrote concurrent changes).
 */
@Service
public class ReceiptManagementWriteServiceImpl implements ReceiptManagementWriteService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptManagementWriteServiceImpl.class);

    private static final String DELIVERED = "ORDER_DELIVERED";
    private static final String COMPLETED = "ORDER_COMPLETED";
    private static final String PAID = "Paid";

    private final ReceiptActorResolver actorResolver;
    private final ReceiptRecordRepository repository;
    private final DocumentNumberService documentNumberService;
    private final ActivityLogService activityLog;
    private final ReceiptNotificationService notificationService;
    private final ReceiptManagementReadServiceImpl readService;

    public ReceiptManagementWriteServiceImpl(ReceiptActorResolver actorResolver,
                                             ReceiptRecordRepository repository,
                                             DocumentNumberService documentNumberService,
                                             ActivityLogService activityLog,
                                             ReceiptNotificationService notificationService,
                                             ReceiptManagementReadServiceImpl readService) {
        this.actorResolver = actorResolver;
        this.repository = repository;
        this.documentNumberService = documentNumberService;
        this.activityLog = activityLog;
        this.notificationService = notificationService;
        this.readService = readService;
    }

    @Override
    public ReceiptResponse generateReceipt(String invoiceId) {
        ReceiptActor actor = actorResolver.requireShopOwner();
        String shopId = actor.requireShopId();

        InvoiceView invoice = repository.findInvoice(invoiceId, shopId)
                .map(InvoiceView::new)
                .orElseThrow(() -> new APIException("Invoice not found"));

        // 2. Idempotent
        Optional<ReceiptRecord> existing = existingReceipt(invoice);
        if (existing.isPresent()) {
            ReceiptResponse response = readService.toResponse(existing.get(), null);
            response.setCreated(false);
            return response;
        }
        if (DELIVERED.equals(invoice.orderStatus())) {
            throw new APIException("The invoice is marked delivered but has no receipt; please contact support");
        }

        // 3. Fully paid + all items completed
        double balanceDue = invoice.balanceDue();
        if (balanceDue > InvoiceView.EPSILON) {
            throw new APIException("Collect the remaining balance of " + balanceDue
                    + " before generating the receipt (POST .../payments)");
        }
        List<String> unfinished = new ArrayList<>();
        for (Document item : invoice.items()) {
            Object status = item.get("itemOrderStatus");
            if (!COMPLETED.equals(status)) {
                unfinished.add(item.get("itemName") + " (" + status + ")");
            }
        }
        if (invoice.items().isEmpty() || !unfinished.isEmpty()) {
            throw new APIException("All items must be ORDER_COMPLETED before delivery. Not finished: "
                    + (unfinished.isEmpty() ? "the invoice has no items" : String.join(", ", unfinished)));
        }

        // 4. Receipt
        ShopDetails shop = repository.findShop(shopId).orElseThrow(() -> new APIException("Shop not found"));
        String receiptNo = documentNumberService.nextNumber(DocumentType.RECEIPT, shopId, numberPrefix(shop.shopName()));
        Date now = DateTimeUtils.getNowInIST();
        Document receipt = buildReceipt(invoice, receiptNo, shop, now);
        Object receiptDbId = repository.insertReceipt(receipt);

        // 5. Link + deliver, atomically and only once
        Map<String, Object> expected = new HashMap<>();
        expected.put("advancePaid", invoice.rawAdvancePaid());
        expected.put("totalAmountWithGST", invoice.rawTotalAmountWithGST());
        boolean linked;
        try {
            linked = repository.updateInvoiceIfUnchanged(invoice.dbId(), expected, new Update()
                    .set("receiptId", receiptDbId.toString())
                    .set("orderStatus", DELIVERED)
                    .set("paymentStatus", PAID)
                    .set("balanceDue", 0.0)
                    .set("paymentCompletedDate", now));
        } catch (RuntimeException exception) {
            repository.deleteReceipt(receiptDbId);
            throw exception;
        }
        if (!linked) {
            repository.deleteReceipt(receiptDbId);
            Optional<ReceiptRecord> concurrent = repository.findInvoice(invoiceId, shopId)
                    .map(InvoiceView::new).flatMap(this::existingReceipt);
            if (concurrent.isPresent()) {
                ReceiptResponse response = readService.toResponse(concurrent.get(), null);
                response.setCreated(false);
                return response;
            }
            throw new ReceiptConflictException(
                    "The invoice was changed by someone else in the meantime. Please reload it and try again.");
        }

        // 6. Follow-ups
        Object customerId = invoice.raw().get("customer") instanceof DBRef ref ? ref.getId() : null;
        if (customerId != null) {
            followUp("link receipt to customer", receiptNo, () -> repository.addReceiptToCustomer(customerId, receiptNo));
        }
        followUp("record activity", receiptNo, () -> activityLog.record(ActivityEntry.of(
                shopId, invoice.invoiceId(), invoice.dbIdString(), actor.userId(), actor.name(),
                ActivityType.ORDER_DELIVERED, "Receipt " + receiptNo + " generated, order delivered")));

        ReceiptRecord saved = repository.findReceipt(receiptNo, shopId)
                .orElseThrow(() -> new APIException("Receipt not found after saving"));
        CustomerSummary customer = repository.findCustomersOfReceipts(List.of(saved.getId())).get(saved.getId());
        ReceiptResponse.Notifications notifications = customer == null
                ? new ReceiptResponse.Notifications(ReceiptNotificationService.SKIPPED, ReceiptNotificationService.SKIPPED)
                : notificationService.notifyReceiptCreated(receiptNo, shop.shopName(), customer.name(),
                customer.phone(), customer.email(), saved.getTotalAmountWithGST());

        log.info("Receipt {} generated for invoice {} by {}", receiptNo, invoiceId, actor.userId());
        ReceiptResponse response = readService.toResponse(saved, customer);
        response.setNotifications(notifications);
        response.setCreated(true);
        return response;
    }

    // ------------------------------------------------------------------ helpers

    private Optional<ReceiptRecord> existingReceipt(InvoiceView invoice) {
        if (invoice.receiptId() == null) {
            return Optional.empty();
        }
        Optional<ReceiptRecord> byInvoice = repository.findReceiptByInvoiceDbId(invoice.dbIdString());
        return byInvoice.isPresent() ? byInvoice : repository.findById(invoice.receiptId());
    }

    /** Same document layout as the old Receipt (so the PDF and public download keep working). */
    private static Document buildReceipt(InvoiceView invoice, String receiptNo, ShopDetails shop, Date now) {
        Document raw = invoice.raw();
        return new Document("_id", new ObjectId())
                .append("receiptNo", receiptNo)
                .append("invoiceId", invoice.dbIdString())
                .append("customer", raw.get("customer"))
                .append("customerState", raw.get("customerState"))
                .append("shopId", invoice.shopId())
                .append("discount", invoice.discount())
                .append("items", invoice.items())
                .append("transactionId", raw.get("transactionId"))
                .append("totalAmount", invoice.totalAmount())
                .append("advancePaid", invoice.advancePaid())
                .append("balanceDue", 0.0)
                .append("paymentMethod", raw.get("paymentMethod"))
                .append("paymentStatus", PAID)
                .append("orderStatus", DELIVERED)
                .append("expectedDeliveryDate", raw.get("expectedDeliveryDate"))
                .append("receiptNotes", raw.get("notes"))
                .append("receiptDate", now)
                .append("orderDate", raw.get("orderDate"))
                .append("paymentCompletedDate", now)
                .append("taxableAmount", invoice.taxableAmount())
                .append("cgstRate", invoice.cgstRate())
                .append("sgstRate", invoice.sgstRate())
                .append("igstRate", invoice.igstRate())
                .append("cgstAmount", invoice.cgstAmount())
                .append("sgstAmount", invoice.sgstAmount())
                .append("igstAmount", invoice.igstAmount())
                .append("totalAmountWithGST", invoice.totalAmountWithGST())
                .append("gstin", shop.gstNumber() != null ? shop.gstNumber() : raw.get("gstin"))
                .append("sacCode", raw.get("sacCode") != null ? raw.get("sacCode") : "9988")
                .append("hsnCode", raw.get("hsnCode"));
    }

    /** First 4 letters/digits of the shop name (safe for short names; old code crashed under 4). */
    static String numberPrefix(String shopName) {
        String cleaned = shopName == null ? "" : shopName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return cleaned.isEmpty() ? "SHOP" : cleaned.substring(0, Math.min(4, cleaned.length()));
    }

    private static void followUp(String description, String receiptNo, Runnable action) {
        try {
            action.run();
        } catch (Exception exception) {
            log.error("Receipt {} saved, but could not {}: {}", receiptNo, description, exception.getMessage(), exception);
        }
    }
}
