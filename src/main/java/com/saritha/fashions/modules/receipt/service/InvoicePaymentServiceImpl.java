package com.saritha.fashions.modules.receipt.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.core.numbering.DocumentNumberService;
import com.saritha.fashions.modules.core.numbering.DocumentType;
import com.saritha.fashions.modules.core.pdf.SalesDocumentPdfService;
import com.saritha.fashions.modules.receipt.exception.ReceiptConflictException;
import com.saritha.fashions.modules.receipt.model.CreditNoteRecord;
import com.saritha.fashions.modules.receipt.payload.ApplyDiscountRequest;
import com.saritha.fashions.modules.receipt.payload.InvoiceAccountResponse;
import com.saritha.fashions.modules.receipt.payload.PaymentReceiptResponse;
import com.saritha.fashions.modules.receipt.payload.ReceiptResponse;
import com.saritha.fashions.modules.receipt.payload.RecordPaymentRequest;
import com.saritha.fashions.modules.receipt.repository.CreditNoteRecordRepository;
import com.saritha.fashions.modules.receipt.repository.ReceiptRecordRepository;
import com.saritha.fashions.modules.receipt.repository.ReceiptRecordRepositoryCustom;
import com.saritha.fashions.util.AmountUtils;
import com.saritha.fashions.util.DateTimeUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payments and discounts, ported from InvoiceServiceImpl.updatePaymentAmountInInvoiceAndReceipt
 * (which did both in one call) and split into two clear operations.
 *
 * Fixes vs the old update-payment:
 * - balance is always totalAmountWithGST − amount paid. The old formula subtracted the
 *   pre-GST discount from the OLD total with GST, so any discount produced a wrong balance;
 * - a payment must be > 0 and may not exceed the balance due (old: negative and over-payments
 *   were accepted);
 * - payment status uses the same values as invoice creation: Dues / Partial / Paid
 *   (old: PAID / PARTIAL in capitals);
 * - only invoices of the shop owner's own shop (old: any invoice of any shop);
 * - atomic compare-and-set update: two payments recorded at the same moment cannot
 *   overwrite each other (old: whole invoice re-saved) — the second gets 409;
 * - no changes after the receipt is generated (old: still allowed).
 */
@Service
public class InvoicePaymentServiceImpl implements InvoicePaymentService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePaymentServiceImpl.class);

    private static final String CONFLICT =
            "The invoice was changed by someone else in the meantime. Please reload it and try again.";

    private static final String PAYMENT_RECEIPT_PDF_URL = "/v1/api/payment-receipts/%s/pdf";

    private final ReceiptActorResolver actorResolver;
    private final ReceiptRecordRepository repository;
    private final CreditNoteRecordRepository creditNoteRepository;
    private final DocumentNumberService documentNumberService;
    private final ReceiptNotificationService notificationService;
    private final SalesDocumentPdfService pdfService;

    public InvoicePaymentServiceImpl(ReceiptActorResolver actorResolver,
                                     ReceiptRecordRepository repository,
                                     CreditNoteRecordRepository creditNoteRepository,
                                     DocumentNumberService documentNumberService,
                                     ReceiptNotificationService notificationService,
                                     SalesDocumentPdfService pdfService) {
        this.actorResolver = actorResolver;
        this.documentNumberService = documentNumberService;
        this.notificationService = notificationService;
        this.pdfService = pdfService;
        this.repository = repository;
        this.creditNoteRepository = creditNoteRepository;
    }

    @Override
    public InvoiceAccountResponse getAccount(String invoiceId) {
        ReceiptActor actor = actorResolver.requireShopOwner();
        return toAccount(loadInvoice(actor, invoiceId), null);
    }

    @Override
    public InvoiceAccountResponse recordPayment(String invoiceId, RecordPaymentRequest request) {
        ReceiptActor actor = actorResolver.requireShopOwner();
        InvoiceView invoice = loadOpenInvoice(actor, invoiceId);

        double amount = AmountUtils.round(request.getAmount());
        double balanceDue = invoice.balanceDue();
        if (balanceDue <= InvoiceView.EPSILON) {
            throw new APIException("This invoice is already fully paid");
        }
        if (amount > balanceDue + InvoiceView.EPSILON) {
            throw new APIException("Payment " + amount + " exceeds the balance due " + balanceDue);
        }

        double newAdvance = AmountUtils.round(invoice.advancePaid() + amount);
        double newBalance = Math.max(0.0, AmountUtils.round(invoice.totalAmountWithGST() - newAdvance));
        String status = InvoiceView.paymentStatus(newAdvance, newBalance);
        Date now = DateTimeUtils.getNowInIST();
        String paidVia = request.getPaymentMethod().trim();
        String transactionId = request.getTransactionId() == null || request.getTransactionId().isBlank()
                ? null : request.getTransactionId().trim();

        // Payment receipt: numbered and stored BEFORE the invoice is updated, so every recorded payment has
        // one. If the invoice update then fails, the payment receipt is removed and its number released.
        String shopId = invoice.shopId();
        ReceiptRecordRepositoryCustom.ShopDetails shop = repository.findShop(shopId)
                .orElseThrow(() -> new APIException("Shop not found"));
        String paymentReceiptNo = documentNumberService.nextNumber(DocumentType.PAYMENT_RECEIPT, shopId,
                ReceiptWriteServiceImpl.numberPrefix(shop.shopName()) + "-PR");
        Object customerRef = invoice.raw().get("customer");
        Document slip = new Document("paymentReceiptNo", paymentReceiptNo)
                .append("shopId", shopId)
                .append("invoiceId", invoice.invoiceId())
                .append("invoiceDbId", invoice.dbIdString())
                .append("paidAt", now)
                .append("amount", amount)
                .append("paidVia", paidVia)
                .append("invoiceTotalWithGST", invoice.totalAmountWithGST())
                .append("totalPaid", newAdvance)
                .append("balanceDue", newBalance)
                .append("paymentStatus", status)
                .append("createdBy", actor.userId())
                .append("createdByName", actor.name());
        if (customerRef != null) {
            slip.append("customer", customerRef);
        }
        if (transactionId != null) {
            slip.append("transactionId", transactionId);
        }
        Object slipDbId = repository.insertPaymentReceipt(slip);

        Document payment = new Document("amount", amount)
                .append("paidAt", now)
                .append("paidVia", paidVia)
                .append("discountApplied", false)
                .append("paymentReceiptNo", paymentReceiptNo);
        if (transactionId != null) {
            payment.append("transactionId", transactionId);
        }

        Update update = new Update()
                .set("advancePaid", newAdvance)
                .set("balanceDue", newBalance)
                .set("paymentStatus", status)
                .set("paymentMethod", request.getPaymentMethod().trim());
        appendToList(update, invoice, "paymentHistories", payment);
        if (newBalance <= InvoiceView.EPSILON) {
            update.set("paymentCompletedDate", now);
        }

        // Guard: amounts unchanged since we read them. (HashMap, not Map.of: old invoices may have
        // no stored advancePaid, and Map.of rejects null values.)
        Map<String, Object> expected = new HashMap<>();
        expected.put("advancePaid", invoice.rawAdvancePaid());
        expected.put("totalAmountWithGST", invoice.rawTotalAmountWithGST());
        boolean updated;
        try {
            updated = repository.updateInvoiceIfUnchanged(invoice.dbId(), expected, update);
        } catch (RuntimeException exception) {
            discardPaymentReceipt(slipDbId, shopId, paymentReceiptNo);
            throw exception;
        }
        if (!updated) {
            discardPaymentReceipt(slipDbId, shopId, paymentReceiptNo);
            throw new ReceiptConflictException(CONFLICT);
        }

        log.info("Payment {} via {} recorded on invoice {} by {}; balance {} -> {}; payment receipt {}",
                amount, paidVia, invoiceId, actor.userId(), balanceDue, newBalance, paymentReceiptNo);

        // Customer notification (background; a failure never affects the payment).
        ReceiptRecordRepositoryCustom.CustomerSummary customer = repository.findCustomer(
                customerRef instanceof com.mongodb.DBRef ref ? ref.getId() : null).orElse(null);
        ReceiptResponse.Notifications notifications = customer == null
                ? new ReceiptResponse.Notifications(ReceiptNotificationService.SKIPPED, ReceiptNotificationService.SKIPPED)
                : notificationService.notifyPaymentReceived(paymentReceiptNo, shop.shopName(), customer.name(),
                customer.phone(), customer.email(), amount, newBalance);

        InvoiceAccountResponse account = toAccount(loadInvoice(actor, invoiceId), null);
        PaymentReceiptResponse receipt = toPaymentReceipt(slip, customer);
        receipt.setNotifications(notifications);
        account.setPaymentReceipt(receipt);
        return account;
    }

    private void discardPaymentReceipt(Object slipDbId, String shopId, String paymentReceiptNo) {
        try {
            repository.deletePaymentReceipt(slipDbId);
            documentNumberService.releaseIfLatest(DocumentType.PAYMENT_RECEIPT, shopId, paymentReceiptNo);
        } catch (Exception exception) {
            log.error("Payment was not recorded, but payment receipt {} could not be removed: {}",
                    paymentReceiptNo, exception.getMessage(), exception);
        }
    }

    // ================================================================ payment receipts (read)

    @Override
    public List<PaymentReceiptResponse> listPaymentReceipts(String invoiceId, String shopId) {
        ReceiptActor actor = actorResolver.requireReader();
        String scope = actor.isPlatformOwner() ? blankToNull(shopId) : actor.requireShopId();
        List<Document> slips = repository.findPaymentReceiptsOfInvoice(invoiceId, scope);
        Map<Object, ReceiptRecordRepositoryCustom.CustomerSummary> customers = new HashMap<>();
        List<PaymentReceiptResponse> responses = new ArrayList<>();
        for (Document slip : slips) {
            Object customerId = slip.get("customer") instanceof com.mongodb.DBRef ref ? ref.getId() : null;
            ReceiptRecordRepositoryCustom.CustomerSummary customer = customerId == null ? null
                    : customers.computeIfAbsent(customerId, id -> repository.findCustomer(id).orElse(null));
            responses.add(toPaymentReceipt(slip, customer));
        }
        return responses;
    }

    @Override
    public PaymentReceiptResponse getPaymentReceipt(String paymentReceiptNo, String shopId) {
        ReceiptActor actor = actorResolver.requireReader();
        String scope = actor.isPlatformOwner() ? blankToNull(shopId) : actor.requireShopId();
        Document slip = repository.findPaymentReceipt(paymentReceiptNo, scope)
                .orElseThrow(() -> new APIException("Payment receipt not found"));
        Object customerId = slip.get("customer") instanceof com.mongodb.DBRef ref ? ref.getId() : null;
        return toPaymentReceipt(slip, repository.findCustomer(customerId).orElse(null));
    }

    @Override
    public byte[] getPaymentReceiptPdf(String paymentReceiptNo, String shopId) {
        ReceiptActor actor = actorResolver.requireReader();
        String scope = actor.isPlatformOwner() ? blankToNull(shopId) : actor.requireShopId();
        return pdfService.paymentReceiptPdf(paymentReceiptNo, scope);
    }

    private static PaymentReceiptResponse toPaymentReceipt(Document slip,
                                                           ReceiptRecordRepositoryCustom.CustomerSummary customer) {
        String no = slip.getString("paymentReceiptNo");
        return PaymentReceiptResponse.builder()
                .paymentReceiptNo(no)
                .invoiceId(slip.getString("invoiceId"))
                .shopId(slip.getString("shopId"))
                .customerName(customer == null ? null : customer.name())
                .customerPhone(customer == null ? null : customer.phone())
                .paidAt(slip.get("paidAt") instanceof Date date ? date : null)
                .amount(num(slip.get("amount")))
                .paidVia(slip.getString("paidVia"))
                .transactionId(slip.getString("transactionId"))
                .invoiceTotalWithGST(num(slip.get("invoiceTotalWithGST")))
                .totalPaid(num(slip.get("totalPaid")))
                .balanceDue(num(slip.get("balanceDue")))
                .paymentStatus(slip.getString("paymentStatus"))
                .receivedBy(slip.getString("createdByName"))
                .pdfUrl(String.format(PAYMENT_RECEIPT_PDF_URL, no))
                .build();
    }

    private static double num(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public InvoiceAccountResponse applyDiscount(String invoiceId, ApplyDiscountRequest request) {
        ReceiptActor actor = actorResolver.requireShopOwner();
        InvoiceView invoice = loadOpenInvoice(actor, invoiceId);

        if (invoice.discount() > 0) {
            throw new APIException("A discount of " + invoice.discount()
                    + " is already applied to this invoice; only one discount is allowed");
        }

        BigDecimal discount = AmountUtils.round(BigDecimal.valueOf(request.getAmount()));
        BigDecimal taxableBefore = AmountUtils.round(BigDecimal.valueOf(invoice.totalAmount()));
        if (discount.compareTo(taxableBefore) > 0) {
            throw new APIException("Discount " + discount + " exceeds the invoice amount " + taxableBefore);
        }
        BigDecimal taxableAfter = taxableBefore.subtract(discount);

        // Keep the invoice's own GST type (intra-state CGST+SGST, or IGST).
        BigDecimal cgstRate = BigDecimal.valueOf(invoice.cgstRate());
        BigDecimal sgstRate = BigDecimal.valueOf(invoice.sgstRate());
        BigDecimal igstRate = BigDecimal.valueOf(invoice.igstRate());
        BigDecimal cgstAmount = tax(taxableAfter, cgstRate);
        BigDecimal sgstAmount = tax(taxableAfter, sgstRate);
        BigDecimal igstAmount = tax(taxableAfter, igstRate);
        BigDecimal totalWithGst = AmountUtils.round(taxableAfter.add(cgstAmount).add(sgstAmount).add(igstAmount));

        double advance = invoice.advancePaid();
        double newBalance = AmountUtils.round(totalWithGst.doubleValue() - advance);
        if (newBalance < -InvoiceView.EPSILON) {
            throw new APIException("The customer has already paid " + advance + ", more than the discounted total "
                    + totalWithGst + ". Refunds are not supported; reduce the discount.");
        }
        newBalance = Math.max(0.0, newBalance);
        String status = InvoiceView.paymentStatus(advance, newBalance);

        CreditNoteRecord creditNote = creditNoteRepository.save(CreditNoteRecord.builder()
                .invoiceId(invoice.invoiceId())
                .invoiceDbId(invoice.dbIdString())
                .discountAmount(discount)
                .taxableBefore(taxableBefore)
                .taxableAfter(taxableAfter)
                .cgstRate(cgstRate).sgstRate(sgstRate).igstRate(igstRate)
                .cgstAmount(cgstAmount).sgstAmount(sgstAmount).igstAmount(igstAmount)
                .totalAmountWithGST(totalWithGst)
                .reason(request.getReason() == null || request.getReason().isBlank()
                        ? "Owner discount" : request.getReason().trim())
                .createdAt(DateTimeUtils.getNowInIST())
                .createdBy(actor.userId())
                .build());

        Update update = new Update()
                .set("discount", discount.doubleValue())
                .set("taxableAmount", taxableAfter.doubleValue())
                .set("cgstAmount", cgstAmount.doubleValue())
                .set("sgstAmount", sgstAmount.doubleValue())
                .set("igstAmount", igstAmount.doubleValue())
                .set("totalAmountWithGST", totalWithGst.doubleValue())
                .set("balanceDue", newBalance)
                .set("paymentStatus", status);
        appendToList(update, invoice, "creditNoteIds", creditNote.getId());

        // Guard: amounts AND the (zero / missing) discount unchanged, so two discounts at the same
        // moment cannot both apply. HashMap: the stored discount may be null / missing.
        Map<String, Object> expected = new HashMap<>();
        expected.put("advancePaid", invoice.rawAdvancePaid());
        expected.put("totalAmountWithGST", invoice.rawTotalAmountWithGST());
        expected.put("discount", invoice.rawDiscount());
        boolean updated = repository.updateInvoiceIfUnchanged(invoice.dbId(), expected, update);
        if (!updated) {
            creditNoteRepository.deleteById(creditNote.getId());
            throw new ReceiptConflictException(CONFLICT);
        }

        log.info("Discount {} applied to invoice {} by {} (credit note {})", discount, invoiceId, actor.userId(), creditNote.getId());
        return toAccount(loadInvoice(actor, invoiceId), creditNote.getId());
    }

    // ------------------------------------------------------------------ helpers

    private InvoiceView loadInvoice(ReceiptActor actor, String invoiceId) {
        return repository.findInvoice(invoiceId, actor.requireShopId())
                .map(InvoiceView::new)
                .orElseThrow(() -> new APIException("Invoice not found"));
    }

    private InvoiceView loadOpenInvoice(ReceiptActor actor, String invoiceId) {
        InvoiceView invoice = loadInvoice(actor, invoiceId);
        if (invoice.receiptId() != null || "ORDER_DELIVERED".equals(invoice.orderStatus())) {
            throw new APIException("The receipt for this invoice has already been generated; it can no longer change");
        }
        return invoice;
    }

    /** $push, or replace the field with a new list when it is missing as array / null (old data). */
    private static void appendToList(Update update, InvoiceView invoice, String field, Object value) {
        if (invoice.isListField(field) || !invoice.hasField(field)) {
            update.push(field, value);
        } else {
            update.set(field, new ArrayList<>(List.of(value)));
        }
    }

    private static BigDecimal tax(BigDecimal taxable, BigDecimal rate) {
        return rate.signum() == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(AmountUtils.calculateTax(taxable.doubleValue(), rate.doubleValue()));
    }

    static InvoiceAccountResponse toAccount(InvoiceView invoice, String creditNoteId) {
        List<InvoiceAccountResponse.Payment> payments = new ArrayList<>();
        for (Object entry : invoice.list("paymentHistories")) {
            if (entry instanceof Document payment) {
                Object paidAt = payment.get("paidAt");
                payments.add(InvoiceAccountResponse.Payment.builder()
                        .amount(payment.get("amount") instanceof Number number ? number.doubleValue() : null)
                        .paidVia(payment.get("paidVia") == null ? null : payment.get("paidVia").toString())
                        .paidAt(paidAt instanceof Date date ? date : null)
                        .transactionId(payment.get("transactionId") == null ? null : payment.get("transactionId").toString())
                        .paymentReceiptNo(payment.get("paymentReceiptNo") == null ? null : payment.get("paymentReceiptNo").toString())
                        .build());
            }
        }
        double balance = invoice.balanceDue();
        return InvoiceAccountResponse.builder()
                .invoiceId(invoice.invoiceId())
                .totalAmount(invoice.totalAmount())
                .discount(invoice.discount())
                .taxableAmount(invoice.taxableAmount())
                .cgstAmount(invoice.cgstAmount())
                .sgstAmount(invoice.sgstAmount())
                .igstAmount(invoice.igstAmount())
                .totalAmountWithGST(invoice.totalAmountWithGST())
                .advancePaid(invoice.advancePaid())
                .balanceDue(balance)
                .paymentStatus(InvoiceView.paymentStatus(invoice.advancePaid(), balance))
                .creditNoteId(creditNoteId)
                .payments(payments)
                .build();
    }
}
