package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.mongodb.DBRef;
import com.mongodb.client.result.UpdateResult;
import com.saritha.fashions.config.OrderStatus;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.payload.invoice.*;
import com.saritha.fashions.payload.items.Items;
import com.saritha.fashions.payload.shop.ShopUpdateDTO;
import com.saritha.fashions.repository.*;
import com.saritha.fashions.service.Custom.TwilioWhatsAppSender;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.ReceiptService;
import com.saritha.fashions.service.Interface.ShopService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import com.saritha.fashions.util.GcsImageUploader;
import com.saritha.fashions.util.TaxUtils;
import org.bson.types.ObjectId;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Map.entry;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final GcsImageUploader gcsImageUploader;
    private final InvoiceSequenceService invoiceSequenceService;
    private final ReceiptSequenceService receiptSequenceService;
    private final WhatsAppService whatsAppService;
    private final ShopService shopService;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;
    private final ReceiptService receiptService;
    private final MongoTemplate mongoTemplate;
    private final CreditNoteRepository creditNoteRepository;

    private final TwilioWhatsAppSender twilioWhatsAppSender;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                              GcsImageUploader gcsImageUploader,
                              CustomerRepository customerRepository,
                              ReceiptRepository receiptRepository,
                              InvoiceSequenceService invoiceSequenceService,
                              ReceiptSequenceService receiptSequenceService,
                              WhatsAppService whatsAppService,
                              ShopService shopService,
                              ModelMapper modelMapper,
                              MongoTemplate mongoTemplate,
                              CreditNoteRepository creditNoteRepository,
                              AuthUtil authUtil, UserRepository userRepository, @Lazy ReceiptService receiptService, TwilioWhatsAppSender twilioWhatsAppSender) {
        this.invoiceRepository = invoiceRepository;
        this.gcsImageUploader = gcsImageUploader;
        this.customerRepository = customerRepository;
        this.receiptRepository = receiptRepository;
        this.invoiceSequenceService = invoiceSequenceService;
        this.receiptSequenceService = receiptSequenceService;
        this.whatsAppService = whatsAppService;
        this.shopService = shopService;
        this.modelMapper = modelMapper;
        this.authUtil = authUtil;
        this.receiptService = receiptService;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.twilioWhatsAppSender = twilioWhatsAppSender;
        this.creditNoteRepository = creditNoteRepository;
    }

    @Override
    @Transactional
    public InvoiceResponseDTO assignTailorToInvoice(InvoiceAssignmentRequestDTO requestDTO) {
        // Validate input
        if (requestDTO.getItemUniqueId() == null) {
            throw new APIException("ItemUniqueId cannot be null");
        }

        // Create and execute update
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("invoiceId").is(requestDTO.getInvoiceId())),
                new Update()
                        .set("items.$[elem].assignedTo", requestDTO.getTailorId())
                        .set("items.$[elem].itemOrderStatus", OrderStatus.TAILOR_ASSIGNED)
                        .set("items.$[elem].assignmentStatus", OrderStatus.ASSIGNED)
                        .push("items.$[elem].assignmentHistory", new Items.AssignmentHistory(
                                requestDTO.getTailorId(),
                                OrderStatus.ASSIGNED,
                                DateTimeUtils.getNowInIST()
                        ))
                        .filterArray("elem.itemUniqueId", requestDTO.getItemUniqueId()),
                Invoice.class
        );

        if (result.getModifiedCount() == 0) {
            throw new APIException("No matching invoice/item found");
        }

        return mapToResponseDTO(
                invoiceRepository.findByInvoiceId(requestDTO.getInvoiceId())
                        .orElseThrow(() -> new APIException("Invoice not found after update"))
        );
    }

    @Override
    public InvoiceResponseDTO unassignTailorFromInvoice(String invoiceId) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findById(invoiceId);
        if (optionalInvoice.isPresent()) {
            Invoice invoice = optionalInvoice.get();

            if (invoice.getAssignedTailorId() == null) {
                throw new RuntimeException("No tailor assigned to unassign!");
            }

            String oldTailorId = invoice.getAssignedTailorId();

            invoice.setAssignedTailorId(null);
            invoice.setAssignmentStatus("UNASSIGNED");
            invoice.getAssignmentHistory().add(new Invoice.AssignmentHistory(
                    oldTailorId,
                    "UNASSIGNED",
                    DateTimeUtils.getNowInIST()
            ));

            Invoice updated = invoiceRepository.save(invoice);
            return mapToResponseDTO(updated);
        } else {
            throw new APIException("Invoice not found with ID: " + invoiceId);
        }
    }

    @Override
    @Transactional
    public Invoice updatePaymentAmountInInvoiceAndReceipt(String id, String paymentAmountStr, String paymentMethod, String discountStr) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findByInvoiceId(id);
        if (optionalInvoice.isEmpty()) {
            throw new APIException("Invoice not found with ID: " + id);
        }

        // Parse payment amount
        BigDecimal paymentAmount;
        try {
            paymentAmount = new BigDecimal(paymentAmountStr).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw new APIException("Invalid payment amount: " + paymentAmountStr);
        }

        Invoice invoice = optionalInvoice.get();

        // Original taxable base (assume invoice.totalAmount is pre-tax taxable price)
        BigDecimal taxableBefore = BigDecimal.valueOf(invoice.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableBeforeWithGSTWithoutDiscount = BigDecimal.valueOf(invoice.getTotalAmountWithGST()).setScale(2, RoundingMode.HALF_UP);

        // Parse discount if provided
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discountStr != null && !discountStr.isBlank()) {
            try {
                discountAmount = new BigDecimal(discountStr).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception e) {
                throw new APIException("Invalid discount amount: " + discountStr);
            }
        }

        // If discount is already set on invoice, prefer existing (avoid double applying)
        BigDecimal existingDiscount = BigDecimal.valueOf(invoice.getDiscount()).setScale(2, RoundingMode.HALF_UP);
        if (existingDiscount.compareTo(BigDecimal.ZERO) > 0) {
            // already had discount; ignore extra discountStr or you may choose to add
            discountAmount = existingDiscount;
        }

        // taxable after discount cannot be negative
        BigDecimal taxableAfter = taxableBefore.subtract(discountAmount);
        if (taxableAfter.compareTo(BigDecimal.ZERO) < 0) {
            taxableAfter = BigDecimal.ZERO;
        }

        // Fetch shop/customer state for tax decision
        Shop shop = shopService.getShopById(invoice.getShopId());
        String shopState = (shop != null && shop.getShopState() != null) ? shop.getShopState().trim() : "TamilNadu";
        String customerState = invoice.getCustomerState();

        // Prepare rates (as BigDecimal)
        BigDecimal cgstRate = BigDecimal.ZERO, sgstRate = BigDecimal.ZERO, igstRate = BigDecimal.ZERO;
        BigDecimal cgstAmount = BigDecimal.ZERO, sgstAmount = BigDecimal.ZERO, igstAmount = BigDecimal.ZERO;

        if (customerState != null && customerState.equalsIgnoreCase(shopState)) {
            cgstRate = BigDecimal.valueOf(2.5);
            sgstRate = BigDecimal.valueOf(2.5);
            cgstAmount = TaxUtils.calcPercent(taxableAfter, cgstRate);
            sgstAmount = TaxUtils.calcPercent(taxableAfter, sgstRate);
        } else {
            igstRate = BigDecimal.valueOf(5.0);
            igstAmount = TaxUtils.calcPercent(taxableAfter, igstRate);
        }

        BigDecimal totalWithGst = taxableAfter.add(cgstAmount).add(sgstAmount).add(igstAmount).setScale(2, RoundingMode.HALF_UP);

        // If discount is being applied now (and wasn't applied before) -> create Credit Note (recommended)
        CreditNote createdCN = null;
        if (discountAmount.compareTo(BigDecimal.ZERO) > 0 && existingDiscount.compareTo(BigDecimal.ZERO) == 0) {
            CreditNote cn = new CreditNote();
            cn.setInvoiceId(invoice.getInvoiceId());
            cn.setInvoiceDbId(invoice.getId());
            cn.setDiscountAmount(discountAmount);
            cn.setTaxableBefore(taxableBefore);
            cn.setTaxableAfter(taxableAfter);
            cn.setCgstRate(cgstRate);
            cn.setSgstRate(sgstRate);
            cn.setIgstRate(igstRate);
            cn.setCgstAmount(cgstAmount);
            cn.setSgstAmount(sgstAmount);
            cn.setIgstAmount(igstAmount);
            cn.setTotalAmountWithGST(totalWithGst);
            cn.setReason("Owner discount");
            cn.setCreatedAt(DateTimeUtils.getNowInIST());
            cn.setCreatedBy(authUtil.loggedInUser().getUserId());
            createdCN = creditNoteRepository.save(cn);

            // Optionally add CN id to invoice (persist linkage)
            List<String> cnIds = invoice.getCreditNoteIds() != null ? invoice.getCreditNoteIds() : new ArrayList<>();
            cnIds.add(createdCN.getId());
            invoice.setCreditNoteIds(cnIds);
        }

        // Update invoice fields (store discount and recalculated tax fields for convenience/audit)
        invoice.setDiscount(discountAmount.doubleValue());
        invoice.setTaxableAmount(taxableAfter.doubleValue());
        invoice.setCgstRate(cgstRate.doubleValue());
        invoice.setSgstRate(sgstRate.doubleValue());
        invoice.setIgstRate(igstRate.doubleValue());
        invoice.setCgstAmount(cgstAmount.doubleValue());
        invoice.setSgstAmount(sgstAmount.doubleValue());
        invoice.setIgstAmount(igstAmount.doubleValue());
        invoice.setTotalAmountWithGST(totalWithGst.doubleValue());

        // Update payments: add payment
        BigDecimal currentAdvance = BigDecimal.valueOf(invoice.getAdvancePaid()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newAdvancePaid = currentAdvance.add(paymentAmount);
        BigDecimal newBalanceDue = taxableBeforeWithGSTWithoutDiscount.subtract(newAdvancePaid.add(discountAmount));
        invoice.setAdvancePaid(newAdvancePaid.doubleValue());
        invoice.setBalanceDue(newBalanceDue.doubleValue());
        invoice.setPaymentMethod(paymentMethod);
        invoice.setPaymentCompletedDate(DateTimeUtils.getNowInIST());
        invoice.setPaymentStatus(newBalanceDue.compareTo(BigDecimal.ZERO) <= 0 ? "PAID" : "PARTIAL");

        invoiceRepository.save(invoice);

        // Update corresponding Receipt (found by human invoiceId)
        Optional<Receipt> optionalReceipt = receiptRepository.findByInvoiceId(invoice.getInvoiceId());
        if (optionalReceipt.isPresent()) {
            Receipt receipt = optionalReceipt.get();

            receipt.setDiscount(discountAmount.doubleValue());
            receipt.setTaxableAmount(taxableAfter.doubleValue());
            receipt.setCgstRate(cgstRate.doubleValue());
            receipt.setSgstRate(sgstRate.doubleValue());
            receipt.setIgstRate(igstRate.doubleValue());
            receipt.setCgstAmount(cgstAmount.doubleValue());
            receipt.setSgstAmount(sgstAmount.doubleValue());
            receipt.setIgstAmount(igstAmount.doubleValue());
            receipt.setTotalAmountWithGST(totalWithGst.doubleValue());

            receipt.setAdvancePaid(newAdvancePaid.doubleValue());
            receipt.setBalanceDue(newBalanceDue.doubleValue());
            receipt.setPaymentMethod(paymentMethod);
            receipt.setPaymentCompletedDate(new Date());
            receipt.setPaymentStatus(newBalanceDue.compareTo(BigDecimal.ZERO) <= 0 ? "PAID" : "PARTIALLY_PAID");

            // Optionally store credit note id in receipt too
            if (createdCN != null) {
                receipt.setCreditNoteId(createdCN.getId());
            }

            receiptRepository.save(receipt);
        }

        return invoice;
    }


//    @Override
//    @Transactional
//    public Invoice updatePaymentAmountInInvoiceAndReceipt(String id, String paymentAmountStr, String paymentMethod, String discount) {
//        Optional<Invoice> optionalInvoice = invoiceRepository.findByInvoiceId(id);
//        if (optionalInvoice.isEmpty()) {
//            throw new APIException("Invoice not found with ID: " + id);
//        }
//
//        double paymentAmount;
//        double discountAmount = 0.0;
//
//        try {
//            paymentAmount = Double.parseDouble(paymentAmountStr);
//        } catch (NumberFormatException e) {
//            throw new APIException("Invalid payment amount: " + paymentAmountStr);
//        }
//
//        // Parse discount string if provided
//        if (discount != null && !discount.isEmpty()) {
//            try {
//                discountAmount = Double.parseDouble(discount);
//            } catch (NumberFormatException e) {
//                throw new APIException("Invalid discount amount: " + discount);
//            }
//        }
//
//        Invoice invoice = optionalInvoice.get();
//
//        // Apply discount only once (when first given)
//        double existingDiscount = invoice.getDiscount() > 0 ? invoice.getDiscount() : 0;
//        if (existingDiscount == 0 && discountAmount > 0) {
//            invoice.setDiscount(discountAmount);
//        } else {
//            discountAmount = existingDiscount;
//        }
//
//        // Final taxable amount after discount
//        double finalTaxableAmount = invoice.getTotalAmount() - discountAmount;
//        invoice.setTaxableAmount(finalTaxableAmount);
//
//        invoice.setDiscount(discountAmount);
//
//
//        double newAdvancePaid = invoice.getAdvancePaid() + paymentAmount;
//        double newBalanceDue = finalTaxableAmount - newAdvancePaid;
//
//        invoice.setAdvancePaid(newAdvancePaid);
//        invoice.setBalanceDue(newBalanceDue);
//        invoice.setPaymentMethod(paymentMethod);
//        invoice.setPaymentCompletedDate(DateTimeUtils.getNowInIST());
//        invoice.setPaymentStatus(newBalanceDue <= 0 ? "PAID" : "PARTIAL");
//
//        invoiceRepository.save(invoice);
//
//        // Update the corresponding Receipt
//        Optional<Receipt> optionalReceipt = receiptRepository.findByInvoiceId(invoice.getInvoiceId());
//        if (optionalReceipt.isPresent()) {
//            Receipt receipt = optionalReceipt.get();
//
//            receipt.setAdvancePaid(newAdvancePaid);
//            receipt.setBalanceDue(newBalanceDue);
//            receipt.setPaymentMethod(paymentMethod);
//            receipt.setPaymentCompletedDate(new Date());
//            receipt.setPaymentStatus(newBalanceDue <= 0 ? "PAID" : "PARTIALLY_PAID");
//            receipt.setDiscount(discountAmount);
//            receipt.setTaxableAmount(finalTaxableAmount);
//
//            receiptRepository.save(receipt);
//        }
//
//        return invoice;
//    }

    @Override
    public InvoiceResponseDTO assignShopAssistantToInvoice(InvoiceAssignmentRequestDTO requestDTO) {
        // Validate input
        if (requestDTO.getItemUniqueId() == null) {
            throw new APIException("ItemUniqueId cannot be null");
        }

        // Create and execute update
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("invoiceId").is(requestDTO.getInvoiceId())),
                new Update()
                        .set("items.$[elem].assignedToWhichShopAssistant", requestDTO.getTailorId())
                        .set("items.$[elem].itemOrderStatus", OrderStatus.SHOP_ASSISTANT_ASSIGNED)
                        .set("items.$[elem].ShopAssistantStatus", OrderStatus.ASSIGNED)
                        .push("items.$[elem].assignmentHistory", new Items.AssignmentHistory(
                                requestDTO.getTailorId(),
                                OrderStatus.SHOP_ASSISTANT_ASSIGNED,
                                DateTimeUtils.getNowInIST()
                        ))
                        .filterArray("elem.itemUniqueId", requestDTO.getItemUniqueId()),
                Invoice.class
        );

        if (result.getModifiedCount() == 0) {
            throw new APIException("No matching invoice/item found");
        }

        return mapToResponseDTO(
                invoiceRepository.findByInvoiceId(requestDTO.getInvoiceId())
                        .orElseThrow(() -> new APIException("Invoice not found after update"))
        );
    }

    @Override
    public ResponseEntity<byte[]> downloadInvoiceByCustomer(String invoiceId) {
        return receiptService.downloadInvoiceByCustomerPdf(invoiceId);
    }

    private InvoiceResponseDTO mapToResponseDTO(Invoice invoice) {
        InvoiceResponseDTO dto = new InvoiceResponseDTO();
        dto.setInvoiceId(invoice.getId());
        dto.setCustomerName(invoice.getCustomer().getName());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setAssignedTailorId(invoice.getAssignedTailorId());
        Optional<User> user = userRepository.findByUserId(invoice.getAssignedTailorId());
        dto.setAssignmentStatus(invoice.getAssignmentStatus());
        dto.setTailorName(user.isPresent() ? user.get().getName() : "Tailor");

        dto.setAssignmentHistory(
                invoice.getAssignmentHistory().stream().map(history -> {
                    InvoiceResponseDTO.AssignmentHistoryDTO historyDTO = new InvoiceResponseDTO.AssignmentHistoryDTO();
                    historyDTO.setTailorId(history.getTailorId());
                    historyDTO.setAction(history.getAction());
                    historyDTO.setTimestamp(history.getTimestamp());
                    return historyDTO;
                }).collect(Collectors.toList())
        );
        return dto;
    }

    @Override
    public Invoice updateInvoiceItems(UpdateItemsRequest request) throws IOException {
        logger.info("Request passed: " + request);
        Optional<Invoice> invoice = invoiceRepository.findByInvoiceId(request.getInvoiceId());

        if (invoice.isEmpty()) {
            throw new APIException("Invoice not found with ID: " + request.getInvoiceId());
        }

        // Update images in GCS if there are new ones
        Invoice invoiceFromDb = invoice.get();
        if (request.getImageFiles() != null && !request.getImageFiles().isEmpty()) {
            updateImagesInGcs(invoiceFromDb, request);
        }

        // Update the items
        invoiceFromDb.setItems(request.getItems());

        return invoiceRepository.save(invoiceFromDb);
    }

    private void updateImagesInGcs(Invoice invoice, UpdateItemsRequest request) throws IOException {
        List<MultipartFile> imageFiles = request.getImageFiles();
        List<String> imageFileNames = request.getImageFileNames();

        // Create a map of item indices to their files
        Map<Integer, MultipartFile> indexToFileMap = new HashMap<>();
        for (int i = 0; i < imageFileNames.size(); i++) {
            try {
                int itemIndex = Integer.parseInt(imageFileNames.get(i));
                indexToFileMap.put(itemIndex, imageFiles.get(i));
            } catch (NumberFormatException e) {
                logger.error("Invalid item index in imageFileNames: {}", imageFileNames.get(i), e);
            }
        }

        // Process each item that has a new image
        for (Map.Entry<Integer, MultipartFile> entry : indexToFileMap.entrySet()) {
            int itemIndex = entry.getKey();
            MultipartFile file = entry.getValue();

            if (itemIndex >= 0 && itemIndex < invoice.getItems().size()) {
                Items item = invoice.getItems().get(itemIndex);

                // Delete old image if exists
                if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                    try {
                        String objectPath = gcsImageUploader.extractGcsObjectPath(item.getImageUrl());
                        if (objectPath != null) {
                            logger.info("Deleting old image for item {} at path: {}", itemIndex, objectPath);
                            gcsImageUploader.deleteFile(objectPath);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to delete old image for item {}: {}", itemIndex, e.getMessage());
                        // Continue with upload despite delete failure
                    }
                }

                // Upload new image with unique filename
                String timestamp = String.valueOf(System.currentTimeMillis());
                String safeItemName = item.getItemName().replaceAll("[^a-zA-Z0-9]", "_");
                String uniqueFilename = String.format("%s_%s_%d", safeItemName, timestamp, itemIndex);

                String newImageUrl = gcsImageUploader.uploadFile(
                        invoice.getInvoiceId(),
                        uniqueFilename,
                        file
                );

                // Update the specific item's image URL in the request
                request.getItems().get(itemIndex).setImageUrl(newImageUrl);
            } else {
                logger.error("Invalid item index {} for invoice {}", itemIndex, invoice.getInvoiceId());
            }
        }
    }

    @Override
    @Transactional
    public Invoice generateInvoice(Invoice invoice) {

        User user = authUtil.loggedInUser();

        try {

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

            Date istDate = new Date();
            String formattedDate = sdf.format(istDate);

            if (invoice.getCustomer() != null) {
                Customer customer = invoice.getCustomer();
                if (customer.getId() == null) {
                    Customer customerExist = customerRepository.findByPhone(customer.getPhone());
                    if (customerExist == null) {

                        customer.setCustomerId(generateUniqueCustomerId("CUS-"));
                        customer = customerRepository.save(customer);
                        invoice.setCustomer(customer);

                    }else {
                        invoice.setCustomer(customerExist);
                    }
                }
            }

            Invoice finalInvoice = modelMapper.map(invoice, Invoice.class);
//            Shop shopss = user.getAssignedShops().get(0);

            String userId = user.getUserId();
            Query query = Query.query(Criteria.where("employeeIds").is(userId));
            query.fields().include("id");
            List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

            if (shops.isEmpty()) {
                throw new APIException("User is not assigned to any shop");
            }

            Shop shop = shops.get(0);
            String shopId = shops.get(0).getId();
            finalInvoice.setShopId(shopId);
//            finalInvoice.setInvoiceId(generateUniqueCustomerId("INV-"));

//            String invoiceId = invoiceSequenceService.generateInvoiceId(invoice.getTransactionId());
//            finalInvoice.setInvoiceId(invoiceId);

            finalInvoice.setInvoiceDate(DateTimeUtils.getNowInIST());

            // Set additional fields for Invoice
            finalInvoice.setAdvancePaid(invoice.getAdvancePaid());
            finalInvoice.setOrderStatus(OrderStatus.ORDER_ACCEPTED);
            finalInvoice.setExpectedDeliveryDate(invoice.getExpectedDeliveryDate());
            finalInvoice.setPaymentMethod(invoice.getPaymentMethod());
            if (invoice.getAdvancePaid() == 0) {
                finalInvoice.setPaymentStatus("Dues");
            } else if (invoice.getTotalAmount() - invoice.getAdvancePaid() > 0) {
                finalInvoice.setPaymentStatus("Partial");
            } else {
                finalInvoice.setPaymentStatus("Paid");
            }

            if (invoice.getNotes().isEmpty()) {
                finalInvoice.setNotes("Thank you for your order");
            }

            double taxableAmount = invoice.getTotalAmount() - invoice.getDiscount();
            finalInvoice.setTaxableAmount(taxableAmount);

            // Fetch shop GSTIN
            finalInvoice.setGstin(shop.getGstNumber());
            finalInvoice.setSacCode("9988");
            finalInvoice.setHsnCode("998821");


            logger.info("Invoice after GST Breakdown{}", invoice);

            String shopState = shop.getShopState() != null ? shop.getShopState() : "TamilNadu";
            String customerState = invoice.getCustomerState();

            if (customerState == null) {
                throw new APIException("Customer state is required to generate invoice");
            }

            if (customerState.equalsIgnoreCase(shopState)) {
                // Intra-state → Apply CGST + SGST
                double cgstRate = 2.5;
                double sgstRate = 2.5;
                double cgstAmount = taxableAmount * (cgstRate / 100);
                double sgstAmount = taxableAmount * (sgstRate / 100);

                finalInvoice.setCgstRate(cgstRate);
                finalInvoice.setSgstRate(sgstRate);
                finalInvoice.setCgstAmount(cgstAmount);
                finalInvoice.setSgstAmount(sgstAmount);
                finalInvoice.setIgstRate(0);
                finalInvoice.setIgstAmount(0);

                double totalAmountWithTax = taxableAmount + cgstAmount + sgstAmount;
                finalInvoice.setTotalAmountWithGST(totalAmountWithTax);
                finalInvoice.setBalanceDue(totalAmountWithTax - invoice.getAdvancePaid());
            } else {
                // Inter-state (or customerState missing) → Apply IGST
                double igstRate = 5.0;
                double igstAmount = taxableAmount * (igstRate / 100);

                finalInvoice.setIgstRate(igstRate);
                finalInvoice.setIgstAmount(igstAmount);
                finalInvoice.setCgstRate(0);
                finalInvoice.setSgstRate(0);
                finalInvoice.setCgstAmount(0);
                finalInvoice.setSgstAmount(0);

                double totalAmountWithTax = taxableAmount + igstAmount;
                finalInvoice.setTotalAmountWithGST(totalAmountWithTax);
                finalInvoice.setBalanceDue(totalAmountWithTax - invoice.getAdvancePaid());
            }


            Invoice savedInvoice = invoiceRepository.save(finalInvoice);

            List<Invoice> allInvoiceFromShop = shop.getAllInvoice();
            allInvoiceFromShop.add(savedInvoice);
            shop.setAllInvoice(allInvoiceFromShop);
            shopService.updateShop(shop.getId(), modelMapper.map(shop, ShopUpdateDTO.class));

            Customer customerUpdated = invoice.getCustomer();
//            List<Invoice> allInvoiceFromCustomer = customerUpdated.getAllInvoices();
//            allInvoiceFromCustomer.add(savedInvoice);
//            customerUpdated.setAllInvoices(allInvoiceFromCustomer);
            List<String> allInvoiceIds = customerUpdated.getAllInvoiceIds() != null ? customerUpdated.getAllInvoiceIds() : new ArrayList<>();
            allInvoiceIds.add(savedInvoice.getInvoiceId());
            customerUpdated.setAllInvoiceIds(allInvoiceIds);

            customerRepository.save(customerUpdated);
//            twilioWhatsAppSender.sendWhatsAppMessage("+91"+customerUpdated.getPhone(),"Invoice",savedInvoice.getInvoiceId());
            return savedInvoice;

        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    @Transactional
    public Receipt generateReceiptByInvoice(Invoice invoice) {

        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoice.getId());

        if (invoiceOpt.isEmpty()) {
            throw new APIException("No Invoice Found with ID: " + invoice.getId());
        }

        Invoice invoiceData = invoiceOpt.get();

        // If receipt already generated
        if (invoiceData.getReceiptId() != null) {
            return receiptRepository.findById(invoiceData.getReceiptId())
                    .orElseThrow(() -> new APIException("Receipt ID present in invoice but not found in DB"));
        }

        // Generate new Receipt
        User user = authUtil.loggedInUser();

        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();


        Shop shopFromDb = shopService.getShopById(shopId);
        String shopName = shopFromDb.getShopName().toUpperCase().trim().substring(0,Math.min(4, shopFromDb.getShopName().length()));
        String receiptNo = receiptSequenceService.generateReceiptId(shopId, shopName);

        Receipt receipt = new Receipt();
        receipt.setShopId(invoiceData.getShopId());
        receipt.setReceiptDate(DateTimeUtils.getNowInIST());
        receipt.setReceiptNo(receiptNo);

        // Basic fields copied from invoice
        receipt.setInvoiceId(invoiceData.getId());
        receipt.setPaymentStatus("Paid");
        receipt.setTotalAmount(invoiceData.getTotalAmount());
        receipt.setAdvancePaid(invoiceData.getAdvancePaid());
        receipt.setExpectedDeliveryDate(invoiceData.getExpectedDeliveryDate());
        receipt.setPaymentMethod(invoiceData.getPaymentMethod());
        receipt.setReceiptNotes(invoiceData.getNotes());
        // Keep the invoice's computed balanceDue (invoice should have been updated when discount/payment applied)
        receipt.setBalanceDue(invoiceData.getBalanceDue());
        receipt.setOrderStatus(OrderStatus.ORDER_DELIVERED);
        receipt.setCustomer(invoiceData.getCustomer());
        receipt.setItems(invoiceData.getItems());
        receipt.setCustomerState(invoiceData.getCustomerState());

        // IMPORTANT: copy discount + tax fields from invoice (invoice must contain final computed values)
        receipt.setDiscount(invoiceData.getDiscount());
        receipt.setTaxableAmount(invoiceData.getTaxableAmount());
        receipt.setCgstRate(invoiceData.getCgstRate());
        receipt.setSgstRate(invoiceData.getSgstRate());
        receipt.setIgstRate(invoiceData.getIgstRate());
        receipt.setCgstAmount(invoiceData.getCgstAmount());
        receipt.setSgstAmount(invoiceData.getSgstAmount());
        receipt.setIgstAmount(invoiceData.getIgstAmount());
        receipt.setTotalAmountWithGST(invoiceData.getTotalAmountWithGST());

        // copy GSTIN / SAC / HSN from shop or invoice
        receipt.setGstin(shopFromDb.getGstNumber());
        receipt.setSacCode(invoiceData.getSacCode() != null ? invoiceData.getSacCode() : "9988");
        receipt.setHsnCode(invoiceData.getHsnCode());

        logger.info("Receipt after GST Breakdown: {}", receipt);

        // Save Receipt
        Receipt receiptSaved = receiptRepository.save(receipt);

        // Add receipt to the Shop (be mindful of large arrays — you may prefer store ids only)
        Shop shopDataFromDb = shopService.getShopById(invoiceData.getShopId());
        List<Receipt> allReceiptFromShop = shopDataFromDb.getAllReceipts();
        allReceiptFromShop.add(receiptSaved);
        shopDataFromDb.setAllReceipts(allReceiptFromShop);
        shopService.updateShop(shopDataFromDb.getId(), modelMapper.map(shopDataFromDb, ShopUpdateDTO.class));

        // Update Invoice: mark delivered / paid, link receipt id
        invoiceData.setPaymentStatus("Paid");
        invoiceData.setOrderStatus(OrderStatus.ORDER_DELIVERED);
        invoiceData.setBalanceDue(receiptSaved.getBalanceDue()); // keep consistent
        invoiceData.setPaymentCompletedDate(DateTimeUtils.getNowInIST());
        invoiceData.setReceiptId(receiptSaved.getId());

        invoiceRepository.save(invoiceData);

        // Update Customer with new receipt id reference
        Customer customer = invoiceData.getCustomer();
        List<String> allReceiptIds = customer.getAllReceiptIds() != null ? customer.getAllReceiptIds() : new ArrayList<>();
        allReceiptIds.add(receiptSaved.getReceiptNo());
        customer.setAllReceiptIds(allReceiptIds);
        customerRepository.save(customer);

        return receiptSaved;
    }

//    @Override
//    @Transactional
//    public Receipt generateReceiptByInvoice(Invoice invoice) {
//
//        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoice.getId());
//
//        if (invoiceOpt.isEmpty()) {
//            throw new APIException("No Invoice Found with ID: " + invoice.getId());
//        }
//
//        Invoice invoiceData = invoiceOpt.get();
//
//        // If receipt already generated
//        if (invoiceData.getReceiptId() != null) {
//            return receiptRepository.findById(invoiceData.getReceiptId())
//                    .orElseThrow(() -> new APIException("Receipt ID present in invoice but not found in DB"));
//        }
//
////        for (Items item : invoiceData.getItems()) {
////            if (!item.getItemOrderStatus().equals(OrderStatus.ORDER_COMPLETED) || !item.getItemOrderStatus().equals(OrderStatus.ORDER_DELIVERED)) {
////                throw new APIException("Some items are still pending to be completed, please wait till completion");
////            }
////        }
//
//        // Generate new Receipt
////        String shopId = authUtil.loggedInUser().getAssignedShops().get(0).getId();
//        User user = authUtil.loggedInUser();
//
//        String userId = user.getUserId();
//        Query query = Query.query(Criteria.where("employeeIds").is(userId));
//        query.fields().include("id");
//        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);
//
//        if (shops.isEmpty()) {
//            throw new APIException("User is not assigned to any shop");
//        }
//
//        String shopId = shops.get(0).getId();
//
//        Shop shopFromDb = shopService.getShopById(shopId);
//        String shopName = shopFromDb.getShopName().toUpperCase().trim().substring(0,4);
//        String receiptNo = receiptSequenceService.generateReceiptId(shopId,shopName);
//
//        Receipt receipt = new Receipt();
//        receipt.setShopId(invoiceData.getShopId());
//        receipt.setReceiptDate(DateTimeUtils.getNowInIST());
//
//        receipt.setReceiptNo(receiptNo);
//
//        receipt.setPaymentStatus("Paid");
//        receipt.setTotalAmount(invoiceData.getTotalAmount());
//        receipt.setAdvancePaid(invoiceData.getAdvancePaid());
//        receipt.setExpectedDeliveryDate(invoiceData.getExpectedDeliveryDate());
//        receipt.setPaymentMethod(invoiceData.getPaymentMethod());
//        receipt.setReceiptNotes(invoiceData.getNotes());
//        receipt.setBalanceDue(0);
//        receipt.setOrderStatus(OrderStatus.ORDER_DELIVERED);
//        receipt.setInvoiceId(invoiceData.getId());
//        receipt.setCustomer(invoiceData.getCustomer());
//        receipt.setItems(invoiceData.getItems());
//
//        receipt.setCustomerState(invoiceData.getCustomerState());
//
//        double taxableAmount = invoiceData.getTotalAmount() - invoiceData.getDiscount();
//        receipt.setTaxableAmount(taxableAmount);
//
//        // Fetch shop GSTIN
//        receipt.setGstin(shopFromDb.getGstNumber());
//        receipt.setSacCode("9988");
//        receipt.setHsnCode("998821");
//
//
//        logger.info("Receipt after GST Breakdown{}", receipt);
//
//        String shopState = shopFromDb.getShopState() != null ? shopFromDb.getShopState() : "TamilNadu";
//        String customerState = invoiceData.getCustomerState();
//
//        if (customerState != null && customerState.equalsIgnoreCase(shopState)) {
//            // Intra-state → Apply CGST + SGST
//            double cgstRate = 2.5;
//            double sgstRate = 2.5;
//            double cgstAmount = taxableAmount * (cgstRate / 100);
//            double sgstAmount = taxableAmount * (sgstRate / 100);
//
//            receipt.setCgstRate(cgstRate);
//            receipt.setSgstRate(sgstRate);
//            receipt.setCgstAmount(cgstAmount);
//            receipt.setSgstAmount(sgstAmount);
//            receipt.setIgstRate(0);
//            receipt.setIgstAmount(0);
//
//            receipt.setTotalAmountWithGST(taxableAmount + cgstAmount + sgstAmount);
//        } else {
//            // Inter-state (or customerState missing) → Apply IGST
//            double igstRate = 5.0;
//            double igstAmount = taxableAmount * (igstRate / 100);
//
//            receipt.setIgstRate(igstRate);
//            receipt.setIgstAmount(igstAmount);
//            receipt.setCgstRate(0);
//            receipt.setSgstRate(0);
//            receipt.setCgstAmount(0);
//            receipt.setSgstAmount(0);
//
//            receipt.setTotalAmountWithGST(taxableAmount + igstAmount);
//
//        }
//
//        // Save Receipt
//        Receipt receiptSaved = receiptRepository.save(receipt);
//
//        // Add receipt to the Shop
//        Shop shopDataFromDb = shopService.getShopById(invoiceData.getShopId());
//        List<Receipt> allReceiptFromShop = shopDataFromDb.getAllReceipts();
//        allReceiptFromShop.add(receiptSaved);
//        shopDataFromDb.setAllReceipts(allReceiptFromShop);
//        Shop shop = shopService.updateShop(shopDataFromDb.getId(), modelMapper.map(shopDataFromDb, ShopUpdateDTO.class));
//        // Update Invoice
//        invoiceData.setPaymentStatus("Paid");
//        invoiceData.setOrderStatus(OrderStatus.ORDER_DELIVERED);
//        invoiceData.setBalanceDue(0);
//        invoiceData.setPaymentCompletedDate(DateTimeUtils.getNowInIST());
//        invoiceData.setReceiptId(receiptSaved.getId());
//        invoiceRepository.save(invoiceData);
//
//        // Update Customer with new receipt
//        Customer customer = invoiceData.getCustomer();
////        List<Receipt> allReceipts = customer.getAllReceipts() != null ? customer.getAllReceipts() : new ArrayList<>();
////        allReceipts.add(receiptSaved);
////        customer.setAllReceipts(allReceipts);
//        List<String> allReceiptIds = customer.getAllReceiptIds() != null ? customer.getAllReceiptIds() : new ArrayList<>();
//        allReceiptIds.add(receiptSaved.getReceiptNo());
//        customer.setAllReceiptIds(allReceiptIds);
//        customerRepository.save(customer);
//
////        twilioWhatsAppSender.sendWhatsAppMessage("+91"+customer.getPhone(),"Receipt",receiptSaved.getReceiptNo());
//
//        return receiptSaved;
//    }

    @Override
    public Optional<Invoice> getInvoice(String id) {
        User user = authUtil.loggedInUser();
//        String shopId = user.getAssignedShops().get(0).getId();

        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        Optional<Invoice> invoice = invoiceRepository.findByInvoiceId(id);
        if (invoice.isPresent()) {
            if (!invoice.get().getShopId().equals(shopId)){
                throw new APIException("You are not allowed to view this invoice");
            }
        }
        return invoiceRepository.findByInvoiceId(id);
    }

    @Override
    public Invoice getInvoiceWithAssignedItems(String invoiceId) {
        User user = authUtil.loggedInUser();
        String tailorId = user.getUserId();
//        String shopId = user.getAssignedShops().get(0).getId();

        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        Optional<Invoice> invoiceOptional = invoiceRepository
                .findInvoiceWithAssignedItemsExcludingDeliveredForTailorMaster(invoiceId, shopId);

        if (invoiceOptional.isEmpty()) {
            throw new APIException(
                    "No items assigned to you found in the searched Invoice or invoice is already delivered"
            );
        }

        // The invoice will only contain items assigned to this tailor
        return invoiceOptional.get();
    }

    @Override
    public boolean updatePaymentStatus(String id, String status) {
        Optional<Invoice> invoice = invoiceRepository.findById(id);
        if (invoice.isPresent()) {
            Invoice inv = invoice.get();
            inv.setPaymentStatus(status);
            try{
                invoiceRepository.save(inv);
                return true;
            }catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }else {
            return false;
        }
    }

    @Override
    public List<Invoice> getInvoicesByShop(String shopId) {
        return invoiceRepository.findByShopId(shopId);
    }

    @Override
    public InvoiceResponse getAllInvoiceFromParticularShopForTailorMaster(Integer page, Integer size, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();
//        String shopId = user.getAssignedShops().get(0).getId();

        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        List<String> excludedItems = List.of(
//                "Embroidery",
//                "Lining Material",
//                "Saree Fall",
//                "Saree Pico",
//                "Designing"
                "NoUse"
        );

        List<String> allowedStatuses = List.of(
                "CUTTING",
                "TAILOR_ASSIGNED",
                "STITCHING",
                "IRONING",
                "ORDER_COMPLETED",
                "ORDER_ACCEPTED"
        );

        Sort sort = sortOrder.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Invoice> invoicePage = invoiceRepository
                .findByShopIdAndItemsItemNameNotInAndOrderStatusIn(shopId, excludedItems, allowedStatuses, pageable);

        if (invoicePage.getContent().isEmpty()) {
            throw new APIException("No Invoices found");
        }

        InvoiceResponse invoiceResponse = new InvoiceResponse();
        invoiceResponse.setContent(invoicePage.getContent());
        invoiceResponse.setPageNumber(invoicePage.getNumber());
        invoiceResponse.setPageSize(invoicePage.getSize());
        invoiceResponse.setTotalElements(invoicePage.getTotalElements());
        invoiceResponse.setTotalPages(invoicePage.getTotalPages());
        invoiceResponse.setLastPage(invoicePage.isLast());

        return invoiceResponse;
    }


    @Override
    public InvoiceResponse getAllInvoiceFromParticularShopInSpecialCategory(
            Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        User user = authUtil.loggedInUser();
//        String shopId = user.getAssignedShops().get(0).getId();

        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        List<String> targetItems = List.of(
                "Embroidery", "Lining Material", "Saree Fall", "Saree Pico", "Designing");

        // Create sort
        Sort sort = sortOrder.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        // Create pageable with sort
        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                sort
        );

        // Execute query
        Page<Invoice> invoicePage = invoiceRepository.findInvoicesWithItems(
                shopId, targetItems, pageable);

        if (invoicePage.isEmpty()) {
            throw new APIException("No matching invoices found");
        }

        return new InvoiceResponse(
                invoicePage.getContent(),
                invoicePage.getNumber(),
                invoicePage.getSize(),
                invoicePage.getTotalElements(),
                invoicePage.getTotalPages(),
                invoicePage.isLast()
        );
    }

//    @Override
//    public InvoiceResponse getAllInvoiceFromParticularShopInSpecialCategory(Integer page, Integer size, String sortBy, String sortOrder) {
//        User user = authUtil.loggedInUser();
//        String shopId = user.getAssignedShops().get(0).getId();
//
//        // Renamed to better reflect what we're including
//        List<String> includedItems = List.of(
//                "Embroidery",
//                "Lining Material",
//                "Saree Fall",
//                "Saree Pico",
//                "Designing"
//        );
//
//        Sort sort = sortOrder.equalsIgnoreCase("asc")
//                ? Sort.by(sortBy).ascending()
//                : Sort.by(sortBy).descending();
//        Pageable pageable = PageRequest.of(page, size, sort);
//
//        // Changed method name to match new behavior
//        Page<Invoice> invoicePage = invoiceRepository.findByShopIdAndItemsItemNameIn(
//                shopId, includedItems, pageable);
//
//        if (invoicePage.isEmpty()) {
//            throw new APIException("No Invoices with specified items found");
//        }
//
//        InvoiceResponse response = new InvoiceResponse();
//        response.setContent(invoicePage.getContent());
//        response.setPageNumber(invoicePage.getNumber());
//        response.setPageSize(invoicePage.getSize());
//        response.setTotalElements(invoicePage.getTotalElements());
//        response.setTotalPages(invoicePage.getTotalPages());
//        response.setLastPage(invoicePage.isLast());
//
//        return response;
//    }

//    @Override
//    public InvoiceResponse getAllInvoiceFromParticularShop(Integer page, Integer size, String sortBy, String sortOrder) {
//        User user = authUtil.loggedInUser();
//        Sort sortByAndOrder = sortOrder.equalsIgnoreCase(sortBy)
//                ? Sort.by(sortBy).ascending()
//                : Sort.by(sortBy).descending();
//
//        Pageable pageable = PageRequest.of(page, size, sortByAndOrder);
//        Page<Invoice> invoicePage = invoiceRepository.findByShopId(user.getAssignedShops().get(0).getId(), pageable);
//        List<Invoice> invoiceList = invoicePage.getContent();
//        if (invoiceList.isEmpty()) {
//            throw new APIException("No Invoices found");
//        }
//
////        List<Invoice> invoices = invoiceList.stream()
////                .map(invoice -> modelMapper.map(invoice, Invoice.class))
////                .toList();
//        InvoiceResponse invoiceResponse = new InvoiceResponse();
//        invoiceResponse.setContent(invoiceList);
//        invoiceResponse.setPageNumber(invoicePage.getNumber());
//        invoiceResponse.setPageSize(invoicePage.getSize());
//        invoiceResponse.setTotalElements(invoicePage.getTotalElements());
//        invoiceResponse.setTotalPages(invoicePage.getTotalPages());
//        invoiceResponse.setLastPage(invoicePage.isLast());
//        return invoiceResponse;
//    }

//    @Override
//    public InvoiceResponse getAllInvoiceFromParticularShop(Integer page, Integer size, String sortBy, String sortOrder) {
//        logger.info("Method started at: {}", Instant.now());
//
//        long startTime = System.currentTimeMillis();
//        User user = authUtil.loggedInUser();
//        logger.info("User fetched in {} ms: {}", System.currentTimeMillis() - startTime, user.getUserId());
//
////        startTime = System.currentTimeMillis();
////        String shopId = user.getAssignedShops().get(0).getId();
////        logger.info("Shop ID fetched in {} ms: {}", System.currentTimeMillis() - startTime, shopId);
//
//        // Eagerly fetch shop IDs to avoid lazy loading
//        startTime = System.currentTimeMillis();
//        List<String> shopIds = mongoTemplate.find(
//                Query.query(Criteria.where("employees.$id").is(new ObjectId(user.getUserId()))),
//                Shop.class
//        ).stream().map(Shop::getId).collect(Collectors.toList());
//
//        if (shopIds.isEmpty()) {
//            throw new APIException("User is not assigned to any shop");
//        }
//        String shopId = shopIds.get(0);
//        logger.info("Shop ID fetched in {} ms: {}", System.currentTimeMillis() - startTime, shopId);
//
//        // Validate sort parameters
//        startTime = System.currentTimeMillis();
//        sortBy = validateSortField(sortBy);
//        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
//        logger.info("Sort parameters validated in {} ms", System.currentTimeMillis() - startTime);
//
//        // Create pageable with sorting
//        startTime = System.currentTimeMillis();
//        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
//        logger.info("Pageable created in {} ms", System.currentTimeMillis() - startTime);
//
//        // Build aggregation pipeline
//        startTime = System.currentTimeMillis();
//        Aggregation aggregation = Aggregation.newAggregation(
//                Aggregation.match(Criteria.where("shopId").is(shopId)),
//                Aggregation.project()
//                        .and("invoiceId").as("invoiceId")
//                        .and("invoiceDate").as("invoiceDate")
//                        .and("totalAmount").as("totalAmount")
//                        .and("paymentStatus").as("paymentStatus")
//                        .and("orderStatus").as("orderStatus")
//                        .and("id").as("id")
//                        .and("shopId").as("shopId"),
//                Aggregation.sort(direction, sortBy),
//                Aggregation.facet()
//                        .and(Aggregation.count().as("totalElements")).as("totalCount")
//                        .and(
//                                Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()),
//                                Aggregation.limit(pageable.getPageSize())
//                        ).as("data")
//        );
//        logger.info("Aggregation pipeline built in {} ms", System.currentTimeMillis() - startTime);
//
//        // Execute aggregation
//        startTime = System.currentTimeMillis();
//        AggregationResults<InvoiceAggregationResult> results = mongoTemplate.aggregate(
//                aggregation, "invoice", InvoiceAggregationResult.class);
//        long queryTime = System.currentTimeMillis() - startTime;
//        logger.info("Aggregation query executed in {} ms", queryTime);
//
//        startTime = System.currentTimeMillis();
//        InvoiceAggregationResult result = results.getUniqueMappedResult();
//        logger.info("Result mapped in {} ms", System.currentTimeMillis() - startTime);
//
//        if (result == null || result.getData().isEmpty()) {
//            logger.warn("No invoices found for shop: {}", shopId);
//            throw new APIException("No Invoices found");
//        }
//
//        // Get total count
//        startTime = System.currentTimeMillis();
//        long totalElements = result.getTotalCount().isEmpty() ? 0 : result.getTotalCount().get(0).getTotalElements();
//        logger.info("Total elements calculated in {} ms: {}", System.currentTimeMillis() - startTime, totalElements);
//
//        // Create response
//        startTime = System.currentTimeMillis();
//        InvoiceResponse response = new InvoiceResponse();
//        response.setContent(result.getData());
//        response.setPageNumber(page);
//        response.setPageSize(size);
//        response.setTotalElements(totalElements);
//        response.setTotalPages((int) Math.ceil((double) totalElements / size));
//        response.setLastPage((page + 1) * size >= totalElements);
//        logger.info("Response constructed in {} ms", System.currentTimeMillis() - startTime);
//
//        logger.info("Method completed in {} ms. Total query time: {} ms",
//                System.currentTimeMillis() - startTime, queryTime);
//
//        return response;
//    }

    @Override
    public InvoiceResponse getAllInvoiceFromParticularShop(Integer page, Integer size, String sortBy, String sortOrder) {
        logger.info("Method started at: {}", Instant.now());

        // 1. Get logged in user
        long startTime = System.currentTimeMillis();
        User user = authUtil.loggedInUser();
        logger.info("User fetched in {} ms: {}", System.currentTimeMillis() - startTime, user.getUserId());

        // 2. Get shop ID using optimized query
        startTime = System.currentTimeMillis();
        String shopId = getShopIdForUser(user.getUserId());
        logger.info("Shop ID fetched in {} ms: {}", System.currentTimeMillis() - startTime, shopId);

        // 3. Validate sort parameters
        startTime = System.currentTimeMillis();
        sortBy = validateSortField(sortBy);
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        logger.info("Sort parameters validated in {} ms", System.currentTimeMillis() - startTime);

        // 4. Create pageable with sorting
        startTime = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        logger.info("Pageable created in {} ms", System.currentTimeMillis() - startTime);

        // 5. Execute optimized aggregation
        startTime = System.currentTimeMillis();
        InvoiceResponse response = getInvoicesByShopId(shopId, pageable, sortBy, sortOrder);
        logger.info("Aggregation completed in {} ms", System.currentTimeMillis() - startTime);

        return response;
    }

    private String getShopIdForUser(String userId) {
        // First try the optimized query
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");

        List<Shop> shops = mongoTemplate.find(query, Shop.class);

        if (!shops.isEmpty()) {
            return shops.get(0).getId();
        }

        // Fallback to DBRef query if needed
        DBRef userRef = new DBRef("users", new ObjectId(userId));
        query = Query.query(Criteria.where("employees").is(userRef));
        query.fields().include("id");

        shops = mongoTemplate.find(query, Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        return shops.get(0).getId();
    }

    private InvoiceResponse getInvoicesByShopId(String shopId, Pageable pageable,String sortBy, String sortOrder) {
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("shopId").is(shopId)),
                Aggregation.lookup("customers", "customer.$id", "_id", "customerData"),
                Aggregation.unwind("customerData", true), // preserveNullAndEmptyArrays=true
                Aggregation.project()
                        .and("invoiceId").as("invoiceId")
                        .and("invoiceDate").as("invoiceDate")
                        .and("totalAmount").as("totalAmount")
                        .and("paymentStatus").as("paymentStatus")
                        .and("paymentMethod").as("paymentMethod")
                        .and("advancePaid").as("advancePaid")
                        .and("balanceDue").as("balanceDue")
                        .and("orderStatus").as("orderStatus")
                        .and("id").as("id")
                        .and("shopId").as("shopId")
                        // Customer fields
                        .and("customerData._id").as("customerId") // MongoDB _id
                        .and("customerData.name").as("customerName") // Customer name
                        .and("customerData.customerId").as("customerNumber"), // Your customer ID field
                Aggregation.sort(direction, validateSortField(sortBy)),
                Aggregation.facet()
                        .and(Aggregation.count().as("totalElements")).as("totalCount")
                        .and(
                                Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()),
                                Aggregation.limit(pageable.getPageSize())
                        ).as("data")
        );

        AggregationResults<InvoiceAggregationResult> results = mongoTemplate.aggregate(
                aggregation, "invoice", InvoiceAggregationResult.class);

        InvoiceAggregationResult result = results.getUniqueMappedResult();
        if (result == null || result.getData().isEmpty()) {
            throw new APIException("No Invoices found");
        }

        long totalElements = result.getTotalCount().isEmpty() ? 0 : result.getTotalCount().get(0).getTotalElements();

        InvoiceResponse response = new InvoiceResponse();
        List<Invoice> invoices = result.getData().stream()
                .map(invoiceWithCustomer -> {
                    Invoice invoice = new Invoice();

                    // Map all Invoice fields
                    invoice.setId(invoiceWithCustomer.getId());
                    invoice.setInvoiceId(invoiceWithCustomer.getInvoiceId());
                    invoice.setInvoiceDate(invoiceWithCustomer.getInvoiceDate());
                    invoice.setTotalAmount(invoiceWithCustomer.getTotalAmount());
                    invoice.setAdvancePaid(invoiceWithCustomer.getAdvancePaid());
                    invoice.setBalanceDue(invoiceWithCustomer.getBalanceDue());
                    invoice.setPaymentMethod(invoiceWithCustomer.getPaymentMethod());
                    invoice.setPaymentStatus(invoiceWithCustomer.getPaymentStatus());
                    invoice.setOrderStatus(invoiceWithCustomer.getOrderStatus());
                    invoice.setShopId(invoiceWithCustomer.getShopId());

                    // Map customer fields
                    Customer customer = new Customer();
                    customer.setId(invoiceWithCustomer.getCustomerId());
                    customer.setName(invoiceWithCustomer.getCustomerName());
                    customer.setCustomerId(invoiceWithCustomer.getCustomerNumber());

                    // Set the customer (assuming Invoice has @DBRef Customer field)
                    invoice.setCustomer(customer);

                    // Map additional fields if needed
                    invoice.setReceiptId(invoiceWithCustomer.getReceiptId());
                    invoice.setExpectedDeliveryDate(invoiceWithCustomer.getExpectedDeliveryDate());
                    invoice.setPaymentCompletedDate(invoiceWithCustomer.getPaymentCompletedDate());
                    invoice.setOrderDate(invoiceWithCustomer.getOrderDate());

                    return invoice;
                })
                .collect(Collectors.toList());

        response.setContent(invoices);
        response.setPageNumber(pageable.getPageNumber());
        response.setPageSize(pageable.getPageSize());
        response.setTotalElements(totalElements);
        response.setTotalPages((int) Math.ceil((double) totalElements / pageable.getPageSize()));
        response.setLastPage((pageable.getPageNumber() + 1) * pageable.getPageSize() >= totalElements);

        return response;
    }

    // Validate sort fields to prevent injection
    private String validateSortField(String sortBy) {
        return switch (sortBy) {
            case "invoiceId" -> "invoiceId";
            case "orderStatus" -> "orderStatus";
            default -> "invoiceDate";
        };
    }

//    Below code for all invoices with all data

    @Override
    public InvoiceResponse getAllInvoiceFromParticularShopWithDetails(Integer page, Integer size, String sortBy, String sortOrder) {
        logger.info("Method started at: {}", Instant.now());

        // 1. Get logged in user
        long startTime = System.currentTimeMillis();
        User user = authUtil.loggedInUser();
        logger.info("User fetched in {} ms: {}", System.currentTimeMillis() - startTime, user.getUserId());

        // 2. Get shop ID using optimized query
        startTime = System.currentTimeMillis();
        String shopId = getShopIdForUserWithAllDetails(user.getUserId());
        logger.info("Shop ID fetched in {} ms: {}", System.currentTimeMillis() - startTime, shopId);

        // 3. Validate sort parameters
        startTime = System.currentTimeMillis();
        sortBy = validateSortField(sortBy);
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        logger.info("Sort parameters validated in {} ms", System.currentTimeMillis() - startTime);

        // 4. Create pageable with sorting
        startTime = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        logger.info("Pageable created in {} ms", System.currentTimeMillis() - startTime);

        // 5. Execute optimized aggregation
        startTime = System.currentTimeMillis();
        InvoiceResponse response = getInvoicesByShopIdWithAllDetails(shopId, pageable, sortBy, sortOrder);
        logger.info("Aggregation completed in {} ms", System.currentTimeMillis() - startTime);

        return response;
    }

    private String getShopIdForUserWithAllDetails(String userId) {
        // First try the optimized query
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");

        List<Shop> shops = mongoTemplate.find(query, Shop.class);

        if (!shops.isEmpty()) {
            return shops.get(0).getId();
        }

        // Fallback to DBRef query if needed
        DBRef userRef = new DBRef("users", new ObjectId(userId));
        query = Query.query(Criteria.where("employees").is(userRef));
        query.fields().include("id");

        shops = mongoTemplate.find(query, Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        return shops.get(0).getId();
    }

    private InvoiceResponse getInvoicesByShopIdWithAllDetails(String shopId, Pageable pageable,String sortBy, String sortOrder) {
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;

        Aggregation aggregation = Aggregation.newAggregation(
                // Stage 1: Match by shopId
                Aggregation.match(Criteria.where("shopId").is(shopId)),

                // Stage 2: Preserve the entire document first
                Aggregation.project().andExclude("_id"),

                // Stage 3: Join with customers collection
                Aggregation.lookup("customers", "customer.$id", "_id", "customerData"),

                // Stage 4: Unwind customer data (preserve invoices without customers)
                Aggregation.unwind("customerData", true),

                Aggregation.sort(direction, validateSortField(sortBy)),

                // Stage 5: Project required fields including items and subfields
                Aggregation.project()
                        .and("id").as("id")
                        .and("invoiceId").as("invoiceId")
                        .and("invoiceDate").as("invoiceDate")
                        .and("totalAmount").as("totalAmount")
                        .and("paymentStatus").as("paymentStatus")
                        .and("paymentMethod").as("paymentMethod")
                        .and("advancePaid").as("advancePaid")
                        .and("balanceDue").as("balanceDue")
                        .and("orderStatus").as("orderStatus")
                        .and("shopId").as("shopId")
                        .and("expectedDeliveryDate").as("expectedDeliveryDate")

                        // Entire 'items' list with all subfields
                        .and("items").as("items")

                        // Customer fields
                        .and("customerData._id").as("customerId")
                        .and("customerData.name").as("customerName")
                        .and("customerData.customerId").as("customerNumber")
                        .and("customerData.phone").as("customerPhone"),

                // Stage 7: Facet for pagination
                Aggregation.facet()
                        .and(Aggregation.count().as("totalElements")).as("totalCount")
                        .and(
                                Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()),
                                Aggregation.limit(pageable.getPageSize())
                        ).as("data")
        );


        AggregationResults<InvoiceAggregationResult> results = mongoTemplate.aggregate(
                aggregation, "invoice", InvoiceAggregationResult.class);

        InvoiceAggregationResult result = results.getUniqueMappedResult();
        if (result == null || result.getData().isEmpty()) {
            throw new APIException("No Invoices found");
        }

        long totalElements = result.getTotalCount().isEmpty() ? 0 : result.getTotalCount().get(0).getTotalElements();

        InvoiceResponse response = new InvoiceResponse();
        List<Invoice> invoices = result.getData().stream()
                .map(invoiceWithCustomer -> {
                    Invoice invoice = new Invoice();

                    // Map all Invoice fields
                    invoice.setId(invoiceWithCustomer.getId());
                    invoice.setInvoiceId(invoiceWithCustomer.getInvoiceId());
                    invoice.setInvoiceDate(invoiceWithCustomer.getInvoiceDate());
                    invoice.setTotalAmount(invoiceWithCustomer.getTotalAmount());
                    invoice.setAdvancePaid(invoiceWithCustomer.getAdvancePaid());
                    invoice.setBalanceDue(invoiceWithCustomer.getBalanceDue());
                    invoice.setPaymentMethod(invoiceWithCustomer.getPaymentMethod());
                    invoice.setPaymentStatus(invoiceWithCustomer.getPaymentStatus());
                    invoice.setOrderStatus(invoiceWithCustomer.getOrderStatus());
                    invoice.setShopId(invoiceWithCustomer.getShopId());
                    invoice.setItems(invoiceWithCustomer.getItems());
                    invoice.setExpectedDeliveryDate(invoiceWithCustomer.getExpectedDeliveryDate());

                    // Map customer fields
                    Customer customer = new Customer();
                    customer.setId(invoiceWithCustomer.getCustomerId());
                    customer.setName(invoiceWithCustomer.getCustomerName());
                    customer.setCustomerId(invoiceWithCustomer.getCustomerNumber());

                    // Set the customer (assuming Invoice has @DBRef Customer field)
                    invoice.setCustomer(customer);

                    // Map additional fields if needed
                    invoice.setReceiptId(invoiceWithCustomer.getReceiptId());
                    invoice.setExpectedDeliveryDate(invoiceWithCustomer.getExpectedDeliveryDate());
                    invoice.setPaymentCompletedDate(invoiceWithCustomer.getPaymentCompletedDate());
                    invoice.setOrderDate(invoiceWithCustomer.getOrderDate());

                    return invoice;
                })
                .collect(Collectors.toList());

        response.setContent(invoices);
        response.setPageNumber(pageable.getPageNumber());
        response.setPageSize(pageable.getPageSize());
        response.setTotalElements(totalElements);
        response.setTotalPages((int) Math.ceil((double) totalElements / pageable.getPageSize()));
        response.setLastPage((pageable.getPageNumber() + 1) * pageable.getPageSize() >= totalElements);

        return response;
    }


    public String generateUniqueReceiptId() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String invoiceId;
        do {
            invoiceId = "REC-" + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet.toCharArray(), 10);
        } while (invoiceRepository.existsByInvoiceId(invoiceId));
        return invoiceId;
    }
    public String generateUniqueInvoiceIdForCloudBucket() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String invoiceId;
        do {
            invoiceId = "CLD-IMG-" + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet.toCharArray(), 10);
        } while (invoiceRepository.existsByInvoiceIdForCloudBucket(invoiceId));
        return invoiceId;
    }

    public String generateUniqueCustomerId(String text) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String customerId;
        do {
            customerId = text + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet.toCharArray(), 10);
        } while (customerRepository.existsByCustomerId(customerId));
        return customerId;
    }

    @Override
    public ResponseEntity<byte[]> createInvoiceWithImages(Invoice invoice, List<MultipartFile> itemImages, int num) throws Exception {
        // Validate measurements for each item
//        validateItemMeasurements(invoice.getItems());

//        String shopId = authUtil.loggedInUser().getAssignedShops().get(0).getId();
        User user = authUtil.loggedInUser();
        Shop shop = mongoTemplate.findOne(
                Query.query(Criteria.where("employeeIds").is(user.getUserId())),
                Shop.class
        );
//        Shop shop = shopService.getShopById(shopId);
        String shopId = shop.getId();
        String shopName = shop.getShopName().toUpperCase().trim().substring(0,4);

//        String invoiceId = invoiceSequenceService.generateInvoiceId(invoice.getTransactionId());
//        String invoiceId = invoiceSequenceService.generateInvoiceId(invoice.getTransactionId(),shopName);
        String invoiceId = invoiceSequenceService.generateInvoiceId(shopId,shopName);

        invoice.setInvoiceId(invoiceId);
        invoice.setCustomerState(invoice.getCustomerState());

        Map<String, String> uploadedImages = new HashMap<>();

        try {
            List<Items> updatedItems = new ArrayList<>();
            logger.debug("Received {} items and {} images", invoice.getItems().size(),
                    itemImages != null ? itemImages.size() : 0);

            if (itemImages != null && !itemImages.isEmpty()) {
                // Create a map of item indexes to their images
                Map<Integer, MultipartFile> itemIndexToImageMap = new HashMap<>();

                // Parse filenames to extract item indexes
                for (MultipartFile file : itemImages) {
                    String originalFilename = file.getOriginalFilename();
                    if (originalFilename != null) {
                        try {
                            // Extract index from filename (format "item_{index}_{name}.ext")
                            String[] parts = originalFilename.split("_");
                            if (parts.length >= 2) {
                                // The index is the part after "item_"
                                int itemIndex = Integer.parseInt(parts[1]);
                                itemIndexToImageMap.put(itemIndex, file);
                            }
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid index in filename: {}", originalFilename);
                            continue;
                        }
                    }
                }

                // Process all items, adding images where available
                for (int i = 0; i < invoice.getItems().size(); i++) {
                    Items item = invoice.getItems().get(i);
                    MultipartFile file = itemIndexToImageMap.get(i);

                    if (file != null && !file.isEmpty()) {
                        try {
                            // Generate unique filename to prevent collisions
                            String timestamp = String.valueOf(System.currentTimeMillis());
                            String safeItemName = item.getItemName().replaceAll("[^a-zA-Z0-9]", "_");
                            String uniqueFilename = String.format("%s_%s_%d", safeItemName, timestamp, i);

                            logger.info("Uploading image for item {}: {}", i, item.getItemName());

                            String imageUrl = gcsImageUploader.uploadFile(
                                    invoiceId,
                                    uniqueFilename,
                                    file
                            );

                            item.setImageUrl(imageUrl);
//                            item.setItemOrderStatus(OrderStatus.ORDER_ACCEPTED);
//                            item.setItemUniqueId(timestamp);
                            uploadedImages.put(uniqueFilename, imageUrl);
                        } catch (IOException e) {
                            logger.error("Failed to upload image for item {}: {}", i, item.getItemName(), e);
                            throw new APIException("Failed to upload image for item: " + item.getItemName());
                        }
                    }

                    List<String> excludedItems = List.of(
                            "Embroidery",
                            "Lining Material",
                            "Saree Fall",
                            "Saree Pico",
                            "Designing"
                    );

                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String safeItemName = item.getItemName().replaceAll("[^a-zA-Z0-9]", "_");
                    String uniqueFilename = String.format("%s_%s_%d", safeItemName, timestamp, i);

                    // Set status based on whether item is in excluded list
                    if (excludedItems.contains(item.getItemName())) {
                        item.setItemOrderStatus(OrderStatus.CUTTING);
                    } else {
                        item.setItemOrderStatus(OrderStatus.ORDER_ACCEPTED);
                    }

                    item.setItemUniqueId(uniqueFilename);
                    updatedItems.add(item);
                }

                invoice.setItems(updatedItems);
            } else {
                // No images to process
                List<Items> items = invoice.getItems();
                int i = 0;
                for (Items item : items) {
                    List<String> excludedItems = List.of(
                            "Embroidery",
                            "Lining Material",
                            "Saree Fall",
                            "Saree Pico",
                            "Designing"
                    );

                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String safeItemName = item.getItemName().replaceAll("[^a-zA-Z0-9]", "_");
                    String uniqueFilename = String.format("%s_%s_%d", safeItemName, timestamp, i);

                    // Set status based on whether item is in excluded list
                    if (excludedItems.contains(item.getItemName())) {
                        item.setItemOrderStatus(OrderStatus.CUTTING);
                    } else {
                        item.setItemOrderStatus(OrderStatus.ORDER_ACCEPTED);
                    }
                    item.setItemUniqueId(uniqueFilename);
                    i++;
                }
                invoice.setItems(new ArrayList<>(items));
            }

            logger.info("Generating PDF for invoice {}", invoiceId);
            return receiptService.generateReceiptAndInvoice(invoice, num);

        } catch (Exception e) {
            logger.error("Invoice generation failed for invoice {}", invoiceId, e);
            rollbackUploadedImages(uploadedImages);
            throw e;
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new APIException("Invalid file type. Only images are allowed.");
        }

        // Validate file extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            if (!Arrays.asList("jpg", "jpeg", "png", "gif").contains(extension)) {
                throw new APIException("Unsupported file extension.");
            }
        }
    }

    private void validateItemMeasurements(List<Items> items) {
        if (items == null) return;

        // List of items that don't require measurements
        Set<String> noMeasurementItems = Set.of(
                "Embroidery",
                "Lining Material",
                "Saree Fall",
                "Saree Pico",
                "Designing"
        );

        for (Items item : items) {
            String itemName = item.getItemName();

            // Skip validation if item is in noMeasurementItems set
            if (noMeasurementItems.contains(itemName)) {
                continue;
            }

            if (item.getMeasurements() == null || item.getMeasurements().isEmpty()) {
                throw new APIException("Measurements are required for item: " + itemName);
            }

            // Item-specific measurement validation
            validateMeasurementsForItemType(itemName, item.getMeasurements());
        }
    }

    private void validateMeasurementsForItemType(String itemType, Map<String, String> measurements) {
        // List of items that don't require measurements
        Set<String> noMeasurementItems = Set.of(
                "Embroidery",
                "Lining Material",
                "Saree Fall",
                "Saree Pico",
                "Designing"
        );

        // Skip validation if item is in noMeasurementItems set
        if (noMeasurementItems.contains(itemType)) {
            return;
        }

        if (measurements == null || measurements.isEmpty()) {
            throw new APIException("No measurements provided for item: " + itemType);
        }

        Map<String, List<String>> requiredMeasurements = Map.ofEntries(
                entry("Top", List.of("Full Length", "Shoulder", "Upper Chest", "Chest", "Waist",
                        "Hip Front", "Hip Back", "Seat", "Sleeve Length",
                        "Sleeve Loose", "Arm Hole", "Bicep", "Front Neck", "Back Neck")),
                entry("Blouse", List.of("F.Length", "B.Length", "Shoulder", "Upper Chest", "Chest",
                        "Waist", "Point", "Sleeve Length", "Sleeve Loose",
                        "Arm Hole", "Bicep", "F.Neck", "B.Neck")),
                entry("Bottom", List.of("Bottom Length", "Leg Loose", "Knee Round", "Thyes", "Hip", "Seat")),
                entry("Salwar Set", List.of("Bottom Length", "Leg Loose", "Remarks")),
                entry("Lining Blouse", List.of("F.Length", "B.Length", "Shoulder", "Upper Chest", "Chest",
                        "Waist", "Point", "Sleeve Length", "Sleeve Loose",
                        "Arm Hole", "Bicep", "F.Neck", "B.Neck")),
                entry("Other", List.of())
        );

        List<String> required = requiredMeasurements.getOrDefault(itemType, List.of());

        // Check for missing required measurements
        List<String> missingMeasurements = required.stream()
                .filter(measurement -> !measurements.containsKey(measurement))
                .collect(Collectors.toList());

        if (!missingMeasurements.isEmpty()) {
            throw new APIException("Missing required measurements for " + itemType + ": " +
                    String.join(", ", missingMeasurements));
        }

        // Validate measurement values
        for (Map.Entry<String, String> entry : measurements.entrySet()) {
            String measurementName = entry.getKey();
            String measurementValue = entry.getValue();

            // Check if value is numeric (for measurements that should be numbers)
            if (!measurementName.equals("Remarks") && !measurementValue.matches("^\\d*\\.?\\d+$")) {
                throw new APIException("Invalid measurement value for " + measurementName +
                        " in " + itemType + ". Must be a number.");
            }

            // Check for empty values
            if (measurementValue == null || measurementValue.trim().isEmpty()) {
                throw new APIException("Measurement value cannot be empty for " +
                        measurementName + " in " + itemType);
            }
        }
    }

    private void rollbackUploadedImages(Map<String, String> uploadedImages) {
        for (Map.Entry<String, String> entry : uploadedImages.entrySet()) {
            try {
                String imagePath = entry.getValue();
                gcsImageUploader.deleteFile(
                        imagePath.replace("https://storage.googleapis.com/" + gcsImageUploader.bucketName + "/", "")
                );
            } catch (Exception ex) {
                logger.error("Failed to delete image {} during rollback: {}", entry.getKey(), ex.getMessage());
            }
        }
    }
}

