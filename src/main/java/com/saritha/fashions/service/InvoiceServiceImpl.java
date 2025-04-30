package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.saritha.fashions.config.OrderStatus;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.exception.ResourceNotFoundException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.payload.invoice.InvoiceAssignmentRequestDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponseDTO;
import com.saritha.fashions.payload.invoice.UpdateItemsRequest;
import com.saritha.fashions.payload.items.Items;
import com.saritha.fashions.payload.shop.ShopUpdateDTO;
import com.saritha.fashions.repository.CustomerRepository;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.ReceiptService;
import com.saritha.fashions.service.Interface.ShopService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import com.saritha.fashions.util.GcsImageUploader;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Map.entry;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final GcsImageUploader gcsImageUploader;
    private final CustomerRepository customerRepository;
    private final ReceiptRepository receiptRepository;
    private final InvoiceSequenceService invoiceSequenceService;
    private final ReceiptSequenceService receiptSequenceService;
    private final WhatsAppService whatsAppService;
    private final ShopService shopService;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;
    private final ReceiptService receiptService;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                              GcsImageUploader gcsImageUploader,
                              CustomerRepository customerRepository,
                              ReceiptRepository receiptRepository,
                              InvoiceSequenceService invoiceSequenceService,
                              ReceiptSequenceService receiptSequenceService,
                              WhatsAppService whatsAppService,
                              ShopService shopService,
                              ModelMapper modelMapper,
                              AuthUtil authUtil, @Lazy ReceiptService receiptService) {
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
    }

    @Override
    public InvoiceResponseDTO assignTailorToInvoice(InvoiceAssignmentRequestDTO requestDTO) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findByInvoiceId(requestDTO.getInvoiceId());
        if (optionalInvoice.isPresent()) {
            Invoice invoice = optionalInvoice.get();

            if (invoice.getAssignedTailorId() != null) {
                throw new APIException("Tailor already assigned! Please unassign first.");
            }

            invoice.setAssignedTailorId(requestDTO.getTailorId());
            invoice.setAssignmentStatus("ASSIGNED");
            invoice.getAssignmentHistory().add(new Invoice.AssignmentHistory(
                    requestDTO.getTailorId(),
                    "ASSIGNED",
                    DateTimeUtils.getNowInIST()
            ));
            invoice.setOrderStatus(OrderStatus.TAILOR_ASSIGNED);

            Invoice updated = invoiceRepository.save(invoice);
            return mapToResponseDTO(updated);
        } else {
            throw new APIException("Invoice not found with ID: " + requestDTO.getInvoiceId());
        }
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

    private InvoiceResponseDTO mapToResponseDTO(Invoice invoice) {
        InvoiceResponseDTO dto = new InvoiceResponseDTO();
        dto.setInvoiceId(invoice.getId());
        dto.setCustomerName(invoice.getCustomer().getName());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setAssignedTailorId(invoice.getAssignedTailorId());
        dto.setAssignmentStatus(invoice.getAssignmentStatus());

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
        Optional<Invoice> invoice = invoiceRepository.findById(request.getInvoiceId());

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

        for (int i = 0; i < imageFiles.size(); i++) {
            MultipartFile file = imageFiles.get(i);
            String itemIndex = imageFileNames.get(i);

            if (file != null && !file.isEmpty()) {
                Items item = invoice.getItems().get(i);

                // Delete old image if exists
                if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                    try {
                        // Extract the actual GCS object path from the URL
                        String imageUrl = item.getImageUrl();
                        String objectPath = gcsImageUploader.extractGcsObjectPath(imageUrl);
                        if (objectPath != null) {
                            logger.info("Object Path : " + objectPath);
//                            gcsImageUploader.storage.delete(gcsImageUploader.bucketName, objectPath);
                            logger.info("Deleted old image at path: {}", objectPath);
                            gcsImageUploader.deleteFile(objectPath);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to delete old image for item {}: {}", item.getItemName(), e.getMessage());
                        // Continue with upload despite delete failure
                    }
                }

                // Upload new image - use the same invoiceId
                String storageId = invoice.getInvoiceId();

                String newImageUrl = gcsImageUploader.uploadFile(
                        storageId,
                        "item_" + itemIndex,
                        file
                );

                // Update the item's image URL
                request.getItems().get(i).setImageUrl(newImageUrl);
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
                if (customer.getId() == null) { // Only save if it's a new customer
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
            Shop shop = user.getAssignedShops().get(0);
            finalInvoice.setShopId(shop.getId());
//            finalInvoice.setInvoiceId(generateUniqueCustomerId("INV-"));

//            String invoiceId = invoiceSequenceService.generateInvoiceId(invoice.getTransactionId());
//            finalInvoice.setInvoiceId(invoiceId);

            finalInvoice.setInvoiceDate(DateTimeUtils.getNowInIST());

            // Set additional fields for Invoice
            finalInvoice.setAdvancePaid(invoice.getAdvancePaid());
            finalInvoice.setBalanceDue(invoice.getTotalAmount() - invoice.getAdvancePaid());
            finalInvoice.setOrderStatus(OrderStatus.ORDER_ACCEPTED);
            finalInvoice.setExpectedDeliveryDate(invoice.getExpectedDeliveryDate());
            finalInvoice.setPaymentMethod(invoice.getPaymentMethod());
            if (invoice.getTotalAmount() - invoice.getAdvancePaid() > 0) {
                finalInvoice.setPaymentStatus("Partial");
            } else if(invoice.getAdvancePaid() == 0) {
                finalInvoice.setPaymentStatus("Dues");
            }
            else {
                finalInvoice.setPaymentStatus("Paid");
            }

            if (invoice.getNotes() == null) {
                finalInvoice.setNotes("Thank you for your order");
            }

            Invoice savedInvoice = invoiceRepository.save(finalInvoice);

            List<Invoice> allInvoiceFromShop = shop.getAllInvoice();
            allInvoiceFromShop.add(savedInvoice);
            shop.setAllInvoice(allInvoiceFromShop);
            shopService.updateShop(shop.getId(), modelMapper.map(shop, ShopUpdateDTO.class));

            Customer customerUpdated = invoice.getCustomer();
            List<Invoice> allInvoiceFromCustomer = customerUpdated.getAllInvoices();
            allInvoiceFromCustomer.add(savedInvoice);
            customerUpdated.setAllInvoices(allInvoiceFromCustomer);

            customerRepository.save(customerUpdated);
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
        Receipt receipt = new Receipt();
        receipt.setShopId(invoiceData.getShopId());
        receipt.setReceiptDate(DateTimeUtils.getNowInIST());
        receipt.setReceiptNo(receiptSequenceService.generateReceiptId(invoiceData.getTransactionId()));
        receipt.setPaymentStatus("Paid");
        receipt.setTotalAmount(invoiceData.getTotalAmount());
        receipt.setAdvancePaid(invoiceData.getAdvancePaid());
        receipt.setExpectedDeliveryDate(invoiceData.getExpectedDeliveryDate());
        receipt.setPaymentMethod(invoiceData.getPaymentMethod());
        receipt.setReceiptNotes(invoiceData.getNotes());
        receipt.setBalanceDue(0);
        receipt.setOrderStatus("Delivered");
        receipt.setInvoiceId(invoiceData.getId());
        receipt.setCustomer(invoiceData.getCustomer());
        receipt.setItems(invoiceData.getItems());

        // Save Receipt
        Receipt receiptSaved = receiptRepository.save(receipt);

        // Add receipt to the Shop
        Shop shopDataFromDb = shopService.getShopById(invoiceData.getShopId());
        List<Receipt> allReceiptFromShop = shopDataFromDb.getAllReceipts();
        allReceiptFromShop.add(receiptSaved);
        shopDataFromDb.setAllReceipts(allReceiptFromShop);
        Shop shop = shopService.updateShop(shopDataFromDb.getId(), modelMapper.map(shopDataFromDb, ShopUpdateDTO.class));
        System.out.println("Total Receipts under " + shop.getShopName() + "is : " + shop.getAllReceipts().size());
        // Update Invoice
        invoiceData.setPaymentStatus("Paid");
        invoiceData.setOrderStatus("Delivered");
        invoiceData.setBalanceDue(0);
        invoiceData.setPaymentCompletedDate(DateTimeUtils.getNowInIST());
        invoiceData.setReceiptId(receiptSaved.getId());
        invoiceRepository.save(invoiceData);

        // Update Customer with new receipt
        Customer customer = invoiceData.getCustomer();
        List<Receipt> allReceipts = customer.getAllReceipts() != null ? customer.getAllReceipts() : new ArrayList<>();
        allReceipts.add(receiptSaved);
        customer.setAllReceipts(allReceipts);
        customerRepository.save(customer);

        return receiptSaved;
    }

    @Override
    public Optional<Invoice> getInvoice(String id) {
        return invoiceRepository.findByInvoiceId(id);
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
    public InvoiceResponse getAllInvoiceFromParticularShop(Integer page, Integer size, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase(sortBy)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sortByAndOrder);
        Page<Invoice> invoicePage = invoiceRepository.findByAssignedTailorId(user.getAssignedShops().get(0).getId(), pageable);
        List<Invoice> invoiceList = invoicePage.getContent();
        if (invoiceList.isEmpty()) {
            throw new APIException("No Invoices found");
        }

//        List<Invoice> invoices = invoiceList.stream()
//                .map(invoice -> modelMapper.map(invoice, Invoice.class))
//                .toList();
        InvoiceResponse invoiceResponse = new InvoiceResponse();
        invoiceResponse.setContent(invoiceList);
        invoiceResponse.setPageNumber(invoicePage.getNumber());
        invoiceResponse.setPageSize(invoicePage.getSize());
        invoiceResponse.setTotalElements(invoicePage.getTotalElements());
        invoiceResponse.setTotalPages(invoicePage.getTotalPages());
        invoiceResponse.setLastPage(invoicePage.isLast());
        return invoiceResponse;
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
        validateItemMeasurements(invoice.getItems());

        String invoiceId = invoiceSequenceService.generateInvoiceId(invoice.getTransactionId());
        invoice.setInvoiceId(invoiceId);

//        String invoiceIdForCloudBucket = generateUniqueInvoiceIdForCloudBucket();
//        invoice.setInvoiceIdForCloudBucket(invoiceIdForCloudBucket);

        Map<String, String> uploadedImages = new HashMap<>();

        try {
            List<Items> updatedItems = new ArrayList<>();

            for (int i = 0; i < invoice.getItems().size(); i++) {
                Items item = invoice.getItems().get(i);
                MultipartFile file = itemImages.get(i);

                if (file != null && !file.isEmpty()) {
                    try {
                        logger.info("Uploading image for item: {}", item.getItemName());
                        String imageUrl = gcsImageUploader.uploadFile(
                                invoiceId,
                                item.getItemName(),
                                file
                        );
                        item.setImageUrl(imageUrl);
                        logger.info("after validateItem measurement:");
                        uploadedImages.put(item.getItemName(), imageUrl);
                    } catch (IOException e) {
                        logger.error("Failed to upload image for item: {}", item.getItemName(), e);
                        throw new APIException("Failed to upload image for item: " + item.getItemName());
                    }
                }
                updatedItems.add(item);
            }

            invoice.setItems(updatedItems);
            logger.info("Calling receipt service for PDF generation");
            return receiptService.generateReceiptAndInvoice(invoice, num);

        } catch (Exception e) {
            // Rollback all uploaded images
            logger.error("Invoice generation failed", e);
            rollbackUploadedImages(uploadedImages);
            throw e;
        }
    }

    private void validateItemMeasurements(List<Items> items) {
        if (items == null) return;

        for (Items item : items) {
            if (item.getMeasurements() == null || item.getMeasurements().isEmpty()) {
                throw new APIException("Measurements are required for item: " + item.getItemName());
            }

            // Item-specific measurement validation
            validateMeasurementsForItemType(item.getItemName(), item.getMeasurements());
        }
    }

    private void validateMeasurementsForItemType(String itemType, Map<String, String> measurements) {
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
                // For all other items not explicitly listed, use Default template
                entry("Saree Pico", List.of("Length", "Width", "Height", "Remarks")),
                entry("Embroidery", List.of("Length", "Width", "Height", "Remarks")),
                entry("Lining Blouse", List.of("F.Length", "B.Length", "Shoulder", "Upper Chest", "Chest",
                        "Waist", "Point", "Sleeve Length", "Sleeve Loose",
                        "Arm Hole", "Bicep", "F.Neck", "B.Neck")),
                entry("Lining Material", List.of("Length", "Width", "Height", "Remarks")),
                entry("Saree Fall", List.of("Length", "Width", "Height", "Remarks")),
                entry("Designing", List.of("Length", "Width", "Height", "Remarks")),
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

