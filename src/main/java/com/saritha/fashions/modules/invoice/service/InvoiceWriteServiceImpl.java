package com.saritha.fashions.modules.invoice.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saritha.fashions.config.OrderStatus;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.core.activity.ActivityEntry;
import com.saritha.fashions.modules.core.activity.ActivityLogService;
import com.saritha.fashions.modules.core.activity.ActivityType;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.mongodb.DBRef;
import com.saritha.fashions.modules.invoice.exception.InvoiceConflictException;
import com.saritha.fashions.modules.invoice.exception.InvoiceRequestValidationException;
import com.saritha.fashions.modules.invoice.model.InvoiceCustomer;
import com.saritha.fashions.modules.invoice.model.InvoiceItem;
import com.saritha.fashions.modules.invoice.model.InvoicePayment;
import com.saritha.fashions.modules.invoice.model.InvoiceRecord;
import com.saritha.fashions.modules.invoice.model.ItemMeasurement;
import com.saritha.fashions.modules.invoice.payload.CreateInvoiceRequest;
import com.saritha.fashions.modules.invoice.payload.CustomerDetailsRequest;
import com.saritha.fashions.modules.invoice.payload.DeleteInvoiceResponse;
import com.saritha.fashions.modules.invoice.payload.EditInvoiceCustomerRequest;
import com.saritha.fashions.modules.invoice.payload.EditInvoiceItemRequest;
import com.saritha.fashions.modules.invoice.payload.EditInvoiceRequest;
import com.saritha.fashions.modules.invoice.payload.InvoiceItemRequest;
import com.saritha.fashions.modules.invoice.payload.InvoiceResponse;
import com.saritha.fashions.modules.invoice.repository.InvoiceRecordRepository;
import com.saritha.fashions.modules.invoice.repository.InvoiceRecordRepositoryCustom.CustomerLookupResult;
import com.saritha.fashions.modules.invoice.repository.InvoiceRecordRepositoryCustom.ShopInvoiceProfile;
import com.saritha.fashions.modules.invoice.service.InvoiceAmountCalculator.InvoiceAmounts;
import com.saritha.fashions.modules.invoice.service.InvoiceNotificationService.InvoiceCreatedEvent;
import com.saritha.fashions.modules.core.date.DeliveryDates;
import com.saritha.fashions.modules.core.numbering.DocumentNumberService;
import com.saritha.fashions.modules.core.numbering.DocumentType;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Invoice creation, ported from InvoiceServiceImpl.createInvoiceWithImages +
 * generateInvoice and restructured so the steps run in a safe order:
 *
 *  1. Auth: logged-in SHOP_OWNER.
 *  2. Validate EVERYTHING that can be checked without side effects: the JSON, the
 *     images (names, types, sizes) and the amounts (discount, advance, client total).
 *  3. Load the shop ONCE with the fields an invoice needs (name, state, GSTIN).
 *     (The old flow loaded only the shop id, so every invoice got GSTIN = null and
 *     the default state, and it then overwrote the shop's invoice / receipt lists.)
 *  4. Find or create the customer by phone (atomic upsert).
 *  5. Take the next invoice number (only now, so validation errors do not burn numbers).
 *  6. Upload item images, then save the invoice. If either fails, the uploaded images
 *     are deleted and the error is returned (no more swallowed exceptions / null).
 *  7. After the invoice is saved: link it to the customer and record the employee
 *     activity. These are follow-ups; a failure is logged, not returned, because the
 *     invoice already exists and a retry would create a duplicate.
 *  8. Queue WhatsApp / email in the background (see InvoiceNotificationService).
 *
 * Not done any more: copying the whole invoice into shops.allInvoice. That embedded
 * copy grows the shop document without limit (MongoDB documents max out at 16 MB) and
 * the old code overwrote it with only the latest invoice anyway. Invoices are read from
 * the "invoice" collection by shopId.
 */
@Service
public class InvoiceWriteServiceImpl implements InvoiceWriteService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceWriteServiceImpl.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String DEFAULT_NOTES = "Thank you for your order";
    private static final String DEFAULT_PAYMENT_METHOD = "Cash";
    private static final String SAC_CODE = "9988";
    private static final String HSN_CODE = "998821";
    private static final String DEFAULT_DELETE_REASON = "Deleted by the shop owner";
    private static final int MAX_REASON_LENGTH = 300;

    /** Items that skip ORDER_ACCEPTED and go straight to CUTTING (same list as the old flow). */
    private static final Set<String> START_IN_CUTTING = Set.of(
            "Embroidery", "Lining Material", "Saree Fall", "Saree Pico", "Designing");

    private static final char[] CUSTOMER_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private final InvoiceRecordRepository invoiceRepository;
    private final InvoiceAmountCalculator amountCalculator;
    private final InvoiceImageService imageService;
    private final InvoiceNotificationService notificationService;
    private final DocumentNumberService documentNumberService;
    private final ActivityLogService activityLog;
    private final AuthUtil authUtil;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public InvoiceWriteServiceImpl(InvoiceRecordRepository invoiceRepository,
                                             InvoiceAmountCalculator amountCalculator,
                                             InvoiceImageService imageService,
                                             InvoiceNotificationService notificationService,
                                             DocumentNumberService documentNumberService,
                                             ActivityLogService activityLog,
                                             AuthUtil authUtil,
                                             ObjectMapper objectMapper,
                                             Validator validator) {
        this.invoiceRepository = invoiceRepository;
        this.amountCalculator = amountCalculator;
        this.imageService = imageService;
        this.notificationService = notificationService;
        this.documentNumberService = documentNumberService;
        this.activityLog = activityLog;
        this.authUtil = authUtil;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public InvoiceResponse createInvoice(String invoiceJson, List<MultipartFile> images) {
        // 1. Auth
        User shopOwner = requireShopOwner("create");

        // 2. Validate request, images and amounts (no side effects yet)
        CreateInvoiceRequest request = parseAndValidate(invoiceJson, CreateInvoiceRequest.class, "invoiceJson");
        request.setExpectedDeliveryDate(normaliseDeliveryDate(request.getExpectedDeliveryDate()));
        Map<Integer, MultipartFile> imagesByItemIndex =
                imageService.mapImagesToItems(images, request.getItems().size());
        Date invoiceDate = resolveInvoiceDate(request);

        // 3. Shop (read once, with the fields we need)
        ShopInvoiceProfile shop = invoiceRepository.findShopProfileByEmployeeId(shopOwner.getUserId())
                .orElseThrow(() -> new APIException("User is not assigned to any shop"));

        InvoiceAmounts amounts = amountCalculator.calculate(
                request.getItems(), request.getTotalAmount(), request.getDiscount(),
                request.getAdvancePaid(), request.getCustomerState(), shop.shopState());

        // 4. Customer
        CustomerLookupResult customerResult = findOrCreateCustomer(request.getCustomer());
        InvoiceCustomer customer = customerResult.customer();

        // 5. Invoice number
        String invoiceId = documentNumberService.nextNumber(DocumentType.INVOICE, shop.shopId(), invoiceNumberPrefix(shop.shopName()));

        // 6. Images + save, with clean-up on failure
        List<String> uploadedImageUrls = new ArrayList<>();
        InvoiceRecord savedInvoice;
        try {
            List<InvoiceItem> items = buildItems(request.getItems(), imagesByItemIndex, invoiceId, uploadedImageUrls);
            InvoiceRecord invoice = buildInvoice(request, invoiceId, invoiceDate, shop, customer, items, amounts);
            savedInvoice = invoiceRepository.insert(invoice);
        } catch (RuntimeException exception) {
            log.error("Invoice {} could not be created; removing {} uploaded image(s)",
                    invoiceId, uploadedImageUrls.size(), exception);
            imageService.deleteUploadedImages(uploadedImageUrls);
            throw exception;
        }

        // 7. Follow-ups (invoice already saved: log failures, do not fail the request)
        runFollowUp("link invoice to customer", invoiceId,
                () -> invoiceRepository.addInvoiceToCustomer(customer.getId(), invoiceId));
        runFollowUp("record employee activity", invoiceId,
                () -> activityLog.record(ActivityEntry.of(shop.shopId(), invoiceId, savedInvoice.getId(),
                        shopOwner.getUserId(), shopOwner.getName(), ActivityType.INVOICE_CREATED, "Invoice created")));

        // 8. Notifications (background)
        String email = firstNonBlank(request.getCustomer().getEmail(), customer.getEmail());
        InvoiceResponse.NotificationStatus notifications = notificationService.notifyInvoiceCreated(
                new InvoiceCreatedEvent(invoiceId, shop.shopName(), customer.getName(), customer.getPhone(), email,
                        amounts.totalAmountWithGST(), amounts.advancePaid(), amounts.balanceDue(),
                        savedInvoice.getExpectedDeliveryDate()));

        log.info("Invoice {} created for shop {} (customer {}, total with GST {})",
                invoiceId, shop.shopId(), customer.getCustomerId(), amounts.totalAmountWithGST());

        return InvoiceResponseMapper.toResponse(savedInvoice, customer, customerResult.created(), notifications);
    }

    // ---------------------------------------------------------------- validation

    private <T> T parseAndValidate(String json, Class<T> type, String partName) {
        if (json == null || json.isBlank()) {
            throw InvoiceRequestValidationException.of(partName, partName + " part is required");
        }

        T request;
        try {
            request = objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw InvoiceRequestValidationException.of(partName,
                    partName + " is not valid JSON for this request: " + exception.getOriginalMessage());
        }
        if (request == null) {
            throw InvoiceRequestValidationException.of(partName, partName + " part is required");
        }

        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> errors = new LinkedHashMap<>();
            violations.forEach(violation ->
                    errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
            throw new InvoiceRequestValidationException(errors);
        }
        return request;
    }

    /**
     * Back-dated invoices use backDate (strict yyyy-MM-dd, not in the future);
     * otherwise now (IST).
     */
    private static Date resolveInvoiceDate(CreateInvoiceRequest request) {
        if (!Boolean.TRUE.equals(request.getIsBackDate())) {
            return DateTimeUtils.getNowInIST();
        }
        LocalDate backDate;
        try {
            backDate = LocalDate.parse(request.getBackDate());
        } catch (DateTimeParseException exception) {
            throw InvoiceRequestValidationException.of("backDate", "backDate is not a valid date");
        }
        if (backDate.isAfter(LocalDate.now(IST))) {
            throw InvoiceRequestValidationException.of("backDate", "backDate must not be in the future");
        }
        return Date.from(backDate.atStartOfDay(IST).toInstant());
    }

    // ---------------------------------------------------------------- building

    private CustomerLookupResult findOrCreateCustomer(CustomerDetailsRequest details) {
        InvoiceCustomer newCustomer = InvoiceCustomer.builder()
                .customerId(newCustomerId())
                .name(details.getName().trim())
                .phone(details.getPhone().trim())
                .email(blankToNull(details.getEmail()))
                .alternatePhone(blankToNull(details.getAlternatePhone()))
                .address(blankToNull(details.getAddress()))
                .build();

        CustomerLookupResult result = invoiceRepository.findOrCreateCustomerByPhone(newCustomer);
        if (result.customer() == null) {
            throw new APIException("Could not find or create the customer");
        }
        return result;
    }

    private String newCustomerId() {
        String customerId;
        do {
            customerId = "CUS-" + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, CUSTOMER_ID_ALPHABET, 10);
        } while (invoiceRepository.customerIdExists(customerId));
        return customerId;
    }

    private List<InvoiceItem> buildItems(List<InvoiceItemRequest> requestItems,
                                         Map<Integer, MultipartFile> imagesByItemIndex,
                                         String invoiceId,
                                         List<String> uploadedImageUrls) {
        long timestamp = System.currentTimeMillis();
        List<InvoiceItem> items = new ArrayList<>();

        for (int index = 0; index < requestItems.size(); index++) {
            InvoiceItemRequest requestItem = requestItems.get(index);
            String itemName = requestItem.getItemName().trim();
            String itemUniqueId = String.format("%s_%d_%d", itemName.replaceAll("[^a-zA-Z0-9]", "_"), timestamp, index);

            MultipartFile image = imagesByItemIndex.get(index);
            String imageUrl = image == null
                    ? null
                    : imageService.upload(invoiceId, itemUniqueId, image, uploadedImageUrls);

            items.add(InvoiceItem.builder()
                    .itemUniqueId(itemUniqueId)
                    .itemName(itemName)
                    .itemQuantity(requestItem.getItemQuantity())
                    .itemPrice(requestItem.getItemPrice())
                    .imageUrl(imageUrl)
                    .itemOrderStatus(START_IN_CUTTING.contains(itemName) ? OrderStatus.CUTTING : OrderStatus.ORDER_ACCEPTED)
                    .measurements(requestItem.getMeasurements())
                    .measurementList(requestItem.getMeasurementList())
                    .build());
        }
        return items;
    }

    private static InvoiceRecord buildInvoice(CreateInvoiceRequest request,
                                              String invoiceId,
                                              Date invoiceDate,
                                              ShopInvoiceProfile shop,
                                              InvoiceCustomer customer,
                                              List<InvoiceItem> items,
                                              InvoiceAmounts amounts) {
        String paymentMethod = firstNonBlank(request.getPaymentMethod(),
                amounts.advancePaid() > 0 ? DEFAULT_PAYMENT_METHOD : null);

        List<InvoicePayment> payments = new ArrayList<>();
        if (amounts.advancePaid() > 0) {
            payments.add(InvoicePayment.builder()
                    .amount(amounts.advancePaid())
                    .paidVia(paymentMethod)
                    .paidAt(Instant.now())
                    .build());
        }

        return InvoiceRecord.builder()
                .invoiceId(invoiceId)
                .shopId(shop.shopId())
                .customer(customer)
                .customerState(request.getCustomerState().trim())
                .items(items)
                .transactionId(blankToNull(request.getTransactionId()))
                .totalAmount(amounts.totalAmount())
                .discount(amounts.discount())
                .taxableAmount(amounts.taxableAmount())
                .cgstRate(amounts.cgstRate())
                .sgstRate(amounts.sgstRate())
                .igstRate(amounts.igstRate())
                .cgstAmount(amounts.cgstAmount())
                .sgstAmount(amounts.sgstAmount())
                .igstAmount(amounts.igstAmount())
                .totalAmountWithGST(amounts.totalAmountWithGST())
                .advancePaid(amounts.advancePaid())
                .balanceDue(amounts.balanceDue())
                .gstin(shop.gstNumber())
                .sacCode(SAC_CODE)
                .hsnCode(HSN_CODE)
                .paymentMethod(paymentMethod)
                .paymentStatus(amounts.paymentStatus())
                .orderStatus(OrderStatus.ORDER_ACCEPTED)
                .paymentHistories(payments)
                .isBackDate(Boolean.TRUE.equals(request.getIsBackDate()))
                .backDate(Boolean.TRUE.equals(request.getIsBackDate()) ? request.getBackDate() : null)
                .expectedDeliveryDate(blankToNull(request.getExpectedDeliveryDate()))
                .invoiceDate(invoiceDate)
                .orderDate(request.getOrderDate() != null ? request.getOrderDate() : invoiceDate)
                .notes(firstNonBlank(request.getNotes(), DEFAULT_NOTES))
                .build();
    }

    /**
     * First 4 letters/digits of the shop name, upper-case ("Saritha Fashions" → "SARI").
     * Safe for short or unusual names (the old substring(0, 4) crashed on names under 4 chars).
     */
    static String invoiceNumberPrefix(String shopName) {
        String cleaned = shopName == null ? "" : shopName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (cleaned.isEmpty()) {
            return "SHOP";
        }
        return cleaned.length() > 4 ? cleaned.substring(0, 4) : cleaned;
    }

    // ================================================================ edit

    /**
     * Edits an invoice. A customer correction (request.customer) is allowed at any time and changes
     * the customer record (as the legacy "Edit Order" did); all other changes need an invoice that
     * has no receipt yet. Steps for the invoice part:
     *  1. Auth + validate the JSON and images (no side effects).
     *  2. Read the stored invoice document of the owner's shop; refuse receipted / delivered ones.
     *  3. Plan the item changes and validate ALL of them (unknown / duplicate ids, items already in
     *     production that would be changed or removed) before uploading anything.
     *  4. Recalculate amounts only if items or discount change (old invoices keep their stored amounts
     *     otherwise). The advance already paid must still fit in the new total.
     *  5. Upload new images, then write with compare-and-set on the stored items, advance and
     *     discount: if anything changed meanwhile (payment, receipt, production update) nothing is
     *     written, uploaded images are removed and 409 is returned.
     *  6. After success, replaced / removed images are deleted (best effort).
     *
     * Kept items are copied from the STORED document and only the edited fields are changed, so
     * production fields the module model does not know (e.g. ShopAssistantStatus, history) survive.
     */
    @Override
    public InvoiceResponse editInvoice(String invoiceId, String editJson, List<MultipartFile> images) {
        User shopOwner = requireShopOwner("edit");
        EditInvoiceRequest request = parseAndValidate(editJson, EditInvoiceRequest.class, "editJson");
        request.setExpectedDeliveryDate(normaliseDeliveryDate(request.getExpectedDeliveryDate()));
        boolean hasImages = images != null && images.stream().anyMatch(file -> file != null && !file.isEmpty());
        if (hasImages && request.getItems() == null) {
            throw InvoiceRequestValidationException.of("images", "images can only be sent together with items");
        }
        Map<Integer, MultipartFile> imagesByItemIndex = request.getItems() == null
                ? Map.of()
                : imageService.mapImagesToItems(images, request.getItems().size());

        EditInvoiceCustomerRequest customerChange =
                request.getCustomer() != null && request.getCustomer().hasChanges() ? request.getCustomer() : null;
        boolean invoiceChanges = request.hasInvoiceChanges();
        if (!invoiceChanges && customerChange == null) {
            throw new APIException("Nothing to change: send expectedDeliveryDate, notes, discount, items or customer");
        }

        ShopInvoiceProfile shop = invoiceRepository.findShopProfileByEmployeeId(shopOwner.getUserId())
                .orElseThrow(() -> new APIException("User is not assigned to any shop"));
        Document stored = invoiceRepository.findInvoiceDocument(invoiceId, shop.shopId())
                .orElseThrow(() -> new APIException("Invoice not found"));
        // A customer correction is allowed at any time (also after the receipt); everything else
        // needs an invoice that is not yet receipted / delivered.
        if (invoiceChanges) {
            requireOpen(stored, invoiceId, "edited");
        }

        // Validate the customer correction before anything is written.
        Object customerDbId = null;
        Update customerUpdate = null;
        if (customerChange != null) {
            customerDbId = stored.get("customer") instanceof DBRef ref ? ref.getId() : null;
            if (customerDbId == null) {
                throw new APIException("This invoice has no customer record to correct");
            }
            customerUpdate = customerUpdate(customerChange, customerDbId);
        }

        if (invoiceChanges) {
            applyInvoiceChanges(invoiceId, request, imagesByItemIndex, stored, shop);
        }
        if (customerUpdate != null) {
            if (!invoiceRepository.updateCustomer(customerDbId, customerUpdate)) {
                throw new APIException("The customer record of this invoice no longer exists");
            }
            log.info("Customer {} of invoice {} corrected by {}: {}", customerDbId, invoiceId,
                    shopOwner.getUserId(), customerUpdate.getUpdateObject().keySet());
        }
        log.info("Invoice {} edited by {} (items {}, discount {}, customer {})", invoiceId, shopOwner.getUserId(),
                request.getItems() != null ? "changed" : "unchanged",
                request.getDiscount() != null ? "changed" : "unchanged",
                customerChange != null ? "corrected" : "unchanged");

        InvoiceRecord updated = invoiceRepository.findByInvoiceIdAndShopId(invoiceId, shop.shopId())
                .orElseThrow(() -> new APIException("Invoice not found"));
        return InvoiceResponseMapper.toResponse(updated, updated.getCustomer(), false, null);
    }

    /**
     * $set / $unset for the customer record. Name and phone can only be replaced; email,
     * alternatePhone and address are removed with "". A phone of another customer is refused,
     * because customers are found by phone when invoices are created.
     */
    private Update customerUpdate(EditInvoiceCustomerRequest change, Object customerDbId) {
        Update update = new Update();
        if (change.getName() != null) {
            update.set("name", change.getName().trim());
        }
        if (change.getPhone() != null) {
            String phone = change.getPhone().trim();
            if (invoiceRepository.customerPhoneUsedByOther(phone, customerDbId)) {
                throw InvoiceRequestValidationException.of("customer.phone",
                        "another customer already uses the phone number " + phone);
            }
            update.set("phone", phone);
        }
        setOrClear(update, "email", change.getEmail());
        setOrClear(update, "alternatePhone", change.getAlternatePhone());
        setOrClear(update, "address", change.getAddress());
        return update;
    }

    private static void setOrClear(Update update, String field, String value) {
        if (value == null) {
            return;
        }
        if (value.isBlank()) {
            update.unset(field);
        } else {
            update.set(field, value.trim());
        }
    }

    /** The invoice part of an edit (items, discount, delivery date, notes); see editInvoice. */
    private void applyInvoiceChanges(String invoiceId, EditInvoiceRequest request,
                                     Map<Integer, MultipartFile> imagesByItemIndex, Document stored,
                                     ShopInvoiceProfile shop) {
        InvoiceRecord current = invoiceRepository.toInvoiceRecord(stored);

        ItemPlan plan = request.getItems() == null
                ? null
                : planItems(request.getItems(), imagesByItemIndex, current, storedItems(stored));

        Update update = new Update();
        if (plan != null || request.getDiscount() != null) {
            setAmounts(update, recalculate(request, current, shop));
        }
        if (request.getExpectedDeliveryDate() != null) {
            String date = blankToNull(request.getExpectedDeliveryDate());
            if (date == null) {
                update.unset("expectedDeliveryDate");
            } else {
                update.set("expectedDeliveryDate", date);
            }
        }
        if (request.getNotes() != null) {
            update.set("notes", firstNonBlank(request.getNotes(), DEFAULT_NOTES));
        }
        if (plan == null && update.getUpdateObject().isEmpty()) {
            throw new APIException("Nothing to change: send expectedDeliveryDate, notes, discount or items");
        }

        // Compare-and-set: the stored items (exact), advance and discount must be unchanged.
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("items", stored.get("items"));
        expected.put("advancePaid", stored.get("advancePaid"));
        expected.put("discount", stored.get("discount"));

        List<String> uploadedImageUrls = new ArrayList<>();
        try {
            if (plan != null) {
                update.set("items", buildEditedItems(plan, invoiceId, uploadedImageUrls));
            }
            if (!invoiceRepository.updateInvoiceIfUnchanged(stored.get("_id"), expected, update)) {
                throw new InvoiceConflictException("The invoice was changed by someone else (payment, receipt or "
                        + "production update) while you were editing it. Reload it and try again.");
            }
        } catch (RuntimeException exception) {
            imageService.deleteUploadedImages(uploadedImageUrls);
            throw exception;
        }

        if (plan != null && !plan.imagesToDelete().isEmpty()) {
            imageService.deleteUploadedImages(plan.imagesToDelete());
        }
    }

    /** An item of the edit request, with the stored item it updates (null = new item). */
    private record PlannedItem(int index, EditInvoiceItemRequest request, Document stored, boolean changed,
                               MultipartFile image) {
    }

    /** @param imagesToDelete images of removed / replaced items, deleted after a successful update */
    private record ItemPlan(List<PlannedItem> items, List<String> imagesToDelete) {
    }

    private ItemPlan planItems(List<EditInvoiceItemRequest> requestItems,
                               Map<Integer, MultipartFile> imagesByItemIndex,
                               InvoiceRecord current,
                               List<Document> storedItems) {
        Map<String, Document> storedById = new LinkedHashMap<>();
        for (Document item : storedItems) {
            String id = item.getString("itemUniqueId");
            if (id == null || id.isBlank()) {
                throw new APIException("This invoice has items without an id, so its items cannot be edited");
            }
            storedById.put(id, item);
        }
        Map<String, InvoiceItem> currentById = new HashMap<>();
        current.getItems().forEach(item -> currentById.put(item.getItemUniqueId(), item));

        Map<String, String> errors = new LinkedHashMap<>();
        List<PlannedItem> planned = new ArrayList<>();
        List<String> imagesToDelete = new ArrayList<>();
        Set<String> referenced = new HashSet<>();

        for (int index = 0; index < requestItems.size(); index++) {
            EditInvoiceItemRequest requestItem = requestItems.get(index);
            String key = "items[" + index + "]";
            MultipartFile image = imagesByItemIndex.get(index);
            String id = blankToNull(requestItem.getItemUniqueId());

            if (id == null) {
                planned.add(new PlannedItem(index, requestItem, null, true, image));
                continue;
            }
            if (!referenced.add(id)) {
                errors.put(key + ".itemUniqueId", "item " + id + " is listed more than once");
                continue;
            }
            Document stored = storedById.get(id);
            InvoiceItem existing = currentById.get(id);
            if (stored == null || existing == null) {
                errors.put(key + ".itemUniqueId", "this invoice has no item " + id);
                continue;
            }

            boolean removeImage = Boolean.TRUE.equals(requestItem.getRemoveImage()) && existing.getImageUrl() != null;
            boolean changed = !requestItem.getItemName().trim().equals(existing.getItemName())
                    || Double.compare(requestItem.getItemQuantity(), existing.getItemQuantity()) != 0
                    || Double.compare(requestItem.getItemPrice(), existing.getItemPrice()) != 0
                    || (requestItem.getMeasurements() != null && !requestItem.getMeasurements().equals(existing.getMeasurements()))
                    || (requestItem.getMeasurementList() != null && !requestItem.getMeasurementList().equals(existing.getMeasurementList()))
                    || image != null
                    || removeImage;
            if (changed && inProduction(existing)) {
                errors.put(key, "item " + id + " is already in production (" + existing.getItemOrderStatus()
                        + ") and cannot be changed");
                continue;
            }
            if (changed && existing.getImageUrl() != null && (image != null || removeImage)) {
                imagesToDelete.add(existing.getImageUrl());
            }
            planned.add(new PlannedItem(index, requestItem, stored, changed, image));
        }

        for (Map.Entry<String, Document> entry : storedById.entrySet()) {
            if (referenced.contains(entry.getKey())) {
                continue;
            }
            InvoiceItem removed = currentById.get(entry.getKey());
            if (removed != null && inProduction(removed)) {
                errors.put("items", "item " + entry.getKey() + " is already in production ("
                        + removed.getItemOrderStatus() + ") and cannot be removed");
            } else if (removed != null && removed.getImageUrl() != null) {
                imagesToDelete.add(removed.getImageUrl());
            }
        }

        if (!errors.isEmpty()) {
            throw new InvoiceRequestValidationException(errors);
        }
        return new ItemPlan(planned, imagesToDelete);
    }

    /** Builds the new stored items; uploads images (added to uploadedImageUrls for rollback). */
    private List<Document> buildEditedItems(ItemPlan plan, String invoiceId, List<String> uploadedImageUrls) {
        long timestamp = System.currentTimeMillis();
        List<Document> items = new ArrayList<>();
        for (PlannedItem planned : plan.items()) {
            EditInvoiceItemRequest request = planned.request();
            if (planned.stored() != null && !planned.changed()) {
                items.add(planned.stored());
                continue;
            }
            String itemName = request.getItemName().trim();
            Document item;
            if (planned.stored() == null) {
                String itemUniqueId = String.format("%s_%d_%d",
                        itemName.replaceAll("[^a-zA-Z0-9]", "_"), timestamp, planned.index());
                item = new Document("itemUniqueId", itemUniqueId)
                        .append("itemOrderStatus", startStatus(itemName))
                        .append("assignmentHistory", new ArrayList<>());
            } else {
                item = new Document(planned.stored());
                if (!itemName.equals(item.getString("itemName"))) {
                    // Not in production yet (checked in planItems), so the start status follows the name.
                    item.put("itemOrderStatus", startStatus(itemName));
                }
                if (Boolean.TRUE.equals(request.getRemoveImage()) && planned.image() == null) {
                    item.remove("imageUrl");
                }
            }
            item.put("itemName", itemName);
            item.put("itemQuantity", request.getItemQuantity());
            item.put("itemPrice", request.getItemPrice());
            if (request.getMeasurements() != null) {
                item.put("measurements", new Document(new LinkedHashMap<>(request.getMeasurements())));
            }
            if (request.getMeasurementList() != null) {
                item.put("measurementList", request.getMeasurementList().stream()
                        .filter(Objects::nonNull).map(InvoiceWriteServiceImpl::measurementDocument).toList());
            }
            if (planned.image() != null) {
                item.put("imageUrl", imageService.upload(invoiceId, item.getString("itemUniqueId"),
                        planned.image(), uploadedImageUrls));
            }
            items.add(item);
        }
        return items;
    }

    private InvoiceAmounts recalculate(EditInvoiceRequest request, InvoiceRecord current, ShopInvoiceProfile shop) {
        List<InvoiceItemRequest> items = request.getItems() != null
                ? request.getItems().stream()
                    .map(item -> new InvoiceItemRequest(item.getItemName().trim(), item.getItemQuantity(), item.getItemPrice(), null, null))
                    .toList()
                : current.getItems().stream()
                    .map(item -> new InvoiceItemRequest(item.getItemName(), item.getItemQuantity(), item.getItemPrice(), null, null))
                    .toList();
        // Old invoices may have no customerState: keep their tax type (IGST stays IGST).
        String customerState = current.getCustomerState() != null && !current.getCustomerState().isBlank()
                ? current.getCustomerState()
                : (current.getIgstRate() > 0 ? "__OTHER_STATE__" : shop.shopState());
        try {
            return amountCalculator.calculate(items, null,
                    request.getDiscount() != null ? request.getDiscount() : current.getDiscount(),
                    current.getAdvancePaid(), customerState, shop.shopState());
        } catch (InvoiceRequestValidationException exception) {
            if (exception.getErrors().containsKey("advancePaid")) {
                throw InvoiceRequestValidationException.of("items", "the new total with GST would be less than the "
                        + current.getAdvancePaid() + " already paid");
            }
            throw exception;
        }
    }

    private static void setAmounts(Update update, InvoiceAmounts amounts) {
        update.set("totalAmount", amounts.totalAmount())
                .set("discount", amounts.discount())
                .set("taxableAmount", amounts.taxableAmount())
                .set("cgstRate", amounts.cgstRate())
                .set("sgstRate", amounts.sgstRate())
                .set("igstRate", amounts.igstRate())
                .set("cgstAmount", amounts.cgstAmount())
                .set("sgstAmount", amounts.sgstAmount())
                .set("igstAmount", amounts.igstAmount())
                .set("totalAmountWithGST", amounts.totalAmountWithGST())
                .set("balanceDue", amounts.balanceDue())
                .set("paymentStatus", amounts.paymentStatus());
    }

    // ================================================================ delete

    /**
     * Deletes an invoice that has no receipt yet:
     *  1. archive the stored document in "deleted_invoices" (same shape as model.DeletedInvoice);
     *  2. delete it with compare-and-set (no receipt, not delivered, items / advance unchanged);
     *     if that fails the archive copy is removed again and 409 is returned;
     *  3. if it was the shop's latest invoice, its number is released, so the next invoice re-uses
     *     it and the bill numbers stay in sequence (client requirement; same as the legacy delete);
     *  4. follow-ups (logged, not returned): unlink from the customer, record INVOICE_DELETED,
     *     delete item images.
     */
    @Override
    public DeleteInvoiceResponse deleteInvoice(String invoiceId, String reason) {
        User shopOwner = requireShopOwner("delete");
        String deleteReason = blankToNull(reason);
        if (deleteReason != null && deleteReason.length() > MAX_REASON_LENGTH) {
            throw InvoiceRequestValidationException.of("reason", "reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
        if (deleteReason == null) {
            deleteReason = DEFAULT_DELETE_REASON;
        }

        ShopInvoiceProfile shop = invoiceRepository.findShopProfileByEmployeeId(shopOwner.getUserId())
                .orElseThrow(() -> new APIException("User is not assigned to any shop"));
        Document stored = invoiceRepository.findInvoiceDocument(invoiceId, shop.shopId())
                .orElseThrow(() -> new APIException("Invoice not found"));
        requireOpen(stored, invoiceId, "deleted");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("items", stored.get("items"));
        expected.put("advancePaid", stored.get("advancePaid"));

        Object archiveId = invoiceRepository.archiveDeletedInvoice(stored, shopOwner.getUserId(), deleteReason);
        boolean deleted;
        try {
            deleted = invoiceRepository.deleteInvoiceIfOpen(stored.get("_id"), expected);
        } catch (RuntimeException exception) {
            removeArchiveQuietly(archiveId, invoiceId);
            throw exception;
        }
        if (!deleted) {
            removeArchiveQuietly(archiveId, invoiceId);
            throw new InvoiceConflictException("The invoice was changed by someone else (payment, receipt or "
                    + "production update) while you were deleting it. Reload it and try again.");
        }

        boolean numberReleased = false;
        try {
            numberReleased = documentNumberService.releaseIfLatest(DocumentType.INVOICE, shop.shopId(), invoiceId);
        } catch (Exception exception) {
            log.error("Invoice {} was deleted, but its number could not be released: {}", invoiceId,
                    exception.getMessage(), exception);
        }

        Object customerRef = stored.get("customer");
        if (customerRef instanceof DBRef ref) {
            runFollowUp("unlink invoice from customer", invoiceId,
                    () -> invoiceRepository.removeInvoiceFromCustomer(ref.getId(), invoiceId));
        }
        String activityReason = deleteReason;
        runFollowUp("record employee activity", invoiceId,
                () -> activityLog.record(ActivityEntry.of(shop.shopId(), invoiceId, String.valueOf(stored.get("_id")),
                        shopOwner.getUserId(), shopOwner.getName(), ActivityType.INVOICE_DELETED,
                        "Invoice deleted: " + activityReason)));
        List<String> imageUrls = storedItems(stored).stream()
                .map(item -> item.get("imageUrl"))
                .filter(url -> url instanceof String text && !text.isBlank())
                .map(String.class::cast)
                .toList();
        if (!imageUrls.isEmpty()) {
            imageService.deleteUploadedImages(imageUrls);
        }

        log.info("Invoice {} of shop {} deleted by {} (archive {})", invoiceId, shop.shopId(), shopOwner.getUserId(), archiveId);
        return new DeleteInvoiceResponse(invoiceId, String.valueOf(archiveId), deleteReason,
                DateTimeUtils.getNowInIST(),
                numberReleased ? "Invoice deleted. Its number will be used for the next invoice." : "Invoice deleted");
    }

    private void removeArchiveQuietly(Object archiveId, String invoiceId) {
        try {
            invoiceRepository.removeArchivedInvoice(archiveId);
        } catch (Exception exception) {
            log.error("Invoice {} was not deleted, but its archive copy {} could not be removed: {}",
                    invoiceId, archiveId, exception.getMessage(), exception);
        }
    }

    // ---------------------------------------------------------------- edit / delete helpers

    /** Receipted or delivered invoices are final. */
    private static void requireOpen(Document invoice, String invoiceId, String action) {
        Object receiptId = invoice.get("receiptId");
        if (receiptId != null) {
            throw new APIException("Invoice " + invoiceId + " already has receipt " + receiptId
                    + " and can no longer be " + action);
        }
        if (OrderStatus.ORDER_DELIVERED.equals(invoice.getString("orderStatus"))) {
            throw new APIException("Invoice " + invoiceId + " is already delivered and can no longer be " + action);
        }
    }

    /**
     * True once production has started: assigned to a tailor / shop assistant, or moved past the
     * status the item starts in.
     */
    private static boolean inProduction(InvoiceItem item) {
        return (item.getAssignedTo() != null && !item.getAssignedTo().isBlank())
                || (item.getAssignedToWhichShopAssistant() != null && !item.getAssignedToWhichShopAssistant().isBlank())
                || !startStatus(item.getItemName()).equals(item.getItemOrderStatus());
    }

    private static String startStatus(String itemName) {
        return itemName != null && START_IN_CUTTING.contains(itemName.trim()) ? OrderStatus.CUTTING : OrderStatus.ORDER_ACCEPTED;
    }

    @SuppressWarnings("unchecked")
    private static List<Document> storedItems(Document invoice) {
        Object items = invoice.get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Document.class::isInstance).map(Document.class::cast).toList();
    }

    private static Document measurementDocument(ItemMeasurement measurement) {
        Document document = new Document();
        if (measurement.getName() != null) {
            document.put("name", measurement.getName());
        }
        if (measurement.getValue() != null) {
            document.put("value", measurement.getValue());
        }
        if (measurement.getUnit() != null) {
            document.put("unit", measurement.getUnit());
        }
        return document;
    }

    // ---------------------------------------------------------------- helpers

    private User requireShopOwner(String action) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required to " + action + " an invoice");
        }
        User user = authUtil.loggedInUser();
        boolean isShopOwner = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.SHOP_OWNER);
        if (!isShopOwner) {
            throw new AccessDeniedException("Only the shop owner can " + action + " invoices");
        }
        return user;
    }

    private static void runFollowUp(String description, String invoiceId, Runnable action) {
        try {
            action.run();
        } catch (Exception exception) {
            log.error("Invoice {} was saved, but could not {}: {}", invoiceId, description, exception.getMessage(), exception);
        }
    }

    /**
     * Delivery dates are stored as yyyy-MM-dd so the delivery dashboard can find them. Common
     * day-first formats (25-11-2026, 25/11/2026) are accepted and converted; anything that is not a
     * real date is rejected. null / blank are passed through unchanged (blank clears it on edit).
     */
    private static String normaliseDeliveryDate(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return DeliveryDates.parse(value).map(DeliveryDates::format).orElseThrow(() ->
                InvoiceRequestValidationException.of("expectedDeliveryDate",
                        "expectedDeliveryDate must be a date, e.g. 2026-11-25"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first.trim() : fallback;
    }
}
