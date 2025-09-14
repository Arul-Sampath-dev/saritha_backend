package com.saritha.fashions.service.ShopOwnerServices;

import com.mongodb.DBRef;
import com.mongodb.client.result.UpdateResult;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.payload.items.Items;
import com.saritha.fashions.payload.shop.ShopUpdateDTO;
import com.saritha.fashions.repository.*;
import com.saritha.fashions.service.Interface.ReceiptService;
import com.saritha.fashions.service.Interface.ShopOwner.InvoiceAPIService;
import com.saritha.fashions.service.Interface.ShopService;
import com.saritha.fashions.service.InvoiceSequenceService;
import com.saritha.fashions.service.ReceiptSequenceService;
import com.saritha.fashions.service.WhatsAppService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import com.saritha.fashions.util.GcsImageUploader;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

@Service
public class InvoiceAPIServiceImpl implements InvoiceAPIService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceAPIServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final GcsImageUploader gcsImageUploader;
    private final InvoiceSequenceService invoiceSequenceService;
    private final ReceiptSequenceService receiptSequenceService;
    private final WhatsAppService whatsAppService;
    private final ShopService shopService;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;
    private final ReceiptService receiptService;
    private final MongoTemplate mongoTemplate;

    public InvoiceAPIServiceImpl(InvoiceRepository invoiceRepository,
                                 GcsImageUploader gcsImageUploader,
                                 CustomerRepository customerRepository,
                                 ReceiptRepository receiptRepository,
                                 ShopRepository shopRepository,
                                 InvoiceSequenceService invoiceSequenceService,
                                 ReceiptSequenceService receiptSequenceService,
                                 WhatsAppService whatsAppService,
                                 ShopService shopService,
                                 ModelMapper modelMapper,
                                 MongoTemplate mongoTemplate,
                                 AuthUtil authUtil, UserRepository userRepository, @Lazy ReceiptService receiptService) {
        this.invoiceRepository = invoiceRepository;
        this.gcsImageUploader = gcsImageUploader;
        this.customerRepository = customerRepository;
        this.receiptRepository = receiptRepository;
        this.shopRepository = shopRepository;
        this.invoiceSequenceService = invoiceSequenceService;
        this.receiptSequenceService = receiptSequenceService;
        this.whatsAppService = whatsAppService;
        this.shopService = shopService;
        this.modelMapper = modelMapper;
        this.authUtil = authUtil;
        this.receiptService = receiptService;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    @Transactional
    public Invoice deleteInvoiceByInvoiceId(String shopId, String invoiceId) {
        // 1. Find and verify invoice
        Invoice invoice = invoiceRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new APIException("Invoice not found"));

        if (!invoice.getShopId().equals(shopId)) {
            throw new APIException("Invoice doesn't belong to this shop");
        }

        String customerId = invoice.getCustomer().getId();

        // 2. Delete all associated images from GCS (continue if fail)
        try {
            deleteAllInvoiceImages(invoice);
        } catch (Exception e) {
            logger.error("Failed to delete images for invoice {}", invoiceId, e);
        }

        // 3. Remove from shop
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(shopId)),
                new Update().pull("allInvoice", Query.query(Criteria.where("_id").is(invoice.getId()))),
                Shop.class
        );

        // 4. Remove customer references
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(customerId)),
                new Update().pull("allInvoiceIds", invoiceId),
                Customer.class
        );

        // ✅ 5. Archive deleted invoice for auditing
        DeletedInvoice deletedInvoice = new DeletedInvoice();
        deletedInvoice.setInvoice(invoice);
        deletedInvoice.setDeletedAt(DateTimeUtils.getNowInIST());
        deletedInvoice.setDeletedBy(authUtil.loggedInUserId());
        deletedInvoice.setReason("Shop Owner Deleted the invoice -> this is default reason");
        mongoTemplate.save(deletedInvoice);

        // ✅ 6. Adjust sequence if this was the latest invoice
        adjustInvoiceSequenceIfLatest(shopId, invoice.getInvoiceId());

        // 7. Delete invoice from main collection
        invoiceRepository.delete(invoice);

        return invoice;
    }

    private void adjustInvoiceSequenceIfLatest(String shopId, String deletedInvoiceId) {
        // Find the sequence record for this shop/year
        String currentYear = String.valueOf(Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
                .get(Calendar.YEAR));
        String sequenceId = currentYear + "-" + shopId;

        InvoiceSequence currentSeq = mongoTemplate.findById(sequenceId, InvoiceSequence.class);
        if (currentSeq == null) return;

        // Extract numeric part from invoiceId (last 5 digits in your format)
        String[] parts = deletedInvoiceId.split("-");
        String seqPart = parts[parts.length - 1]; // last section
        int deletedSeq = Integer.parseInt(seqPart);

        if (deletedSeq == currentSeq.getSequence()) {
            // If the deleted invoice was the latest → decrement sequence
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(sequenceId)),
                    new Update().inc("sequence", -1),
                    InvoiceSequence.class
            );
            logger.info("Invoice sequence decremented for shop {}. New sequence: {}", shopId, currentSeq.getSequence() - 1);
        }
    }



//    @Override
//    @Transactional
//    public Invoice deleteInvoiceByInvoiceId(String shopId, String invoiceId) {
//        // 1. Find and verify invoice
//        Invoice invoice = invoiceRepository.findByInvoiceId(invoiceId)
//                .orElseThrow(() -> new APIException("Invoice not found"));
//
//        if (!invoice.getShopId().equals(shopId)) {
//            throw new APIException("Invoice doesn't belong to this shop");
//        }
//
//        String customerId = invoice.getCustomer().getId();
//
//        // 2. First delete all associated images from GCS
//        try {
//            deleteAllInvoiceImages(invoice);
//        } catch (Exception e) {
//            logger.error("Failed to delete images for invoice {}", invoiceId, e);
//            // Continue with deletion even if image deletion fails
//        }
//
//        // 3. Remove from shop (using DBRef)
//        UpdateResult shopUpdate = mongoTemplate.updateFirst(
//                Query.query(Criteria.where("_id").is(shopId)),
////                new Update().pull("allInvoice", new DBRef("invoice", invoice.getId())),
//                new Update().pull("allInvoice", Query.query(Criteria.where("_id").is(invoice.getId()))),
//                Shop.class
//        );
//
//        // 4. Handle customer references (both old and new schema)
//        Update customerUpdate = new Update()
//                .pull("allInvoiceIds", invoiceId); // New schema
//
//        UpdateResult customerUpdateResult = mongoTemplate.updateFirst(
//                Query.query(Criteria.where("_id").is(customerId)),
//                customerUpdate,
//                Customer.class
//        );
//
//        // 5. Delete invoice
//        invoiceRepository.delete(invoice);
//
//        return invoice;
//    }

    private void deleteAllInvoiceImages(Invoice invoice) {
        if (invoice.getItems() == null) {
            return;
        }

        // Track if we find any images to delete
        boolean imagesFound = false;

        // First delete all item images
        for (Items item : invoice.getItems()) {
            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                try {
                    // Extract the GCS object path from the URL
                    String imagePath = gcsImageUploader.extractGcsObjectPath(item.getImageUrl());
                    if (imagePath != null) {
                        gcsImageUploader.deleteFile(imagePath);
                        imagesFound = true;
                    }
                } catch (Exception ex) {
                    logger.error("Failed to delete image for item {}: {}", item.getItemName(), ex.getMessage());
                }
            }
        }

        // Then delete the invoice folder if we found any images
        if (imagesFound) {
            try {
                // Delete the entire invoice folder
                String folderPath = "invoices/" + invoice.getInvoiceId() + "/";
                gcsImageUploader.deleteFolder(folderPath);
            } catch (Exception ex) {
                logger.error("Failed to delete invoice folder {}: {}", invoice.getInvoiceId(), ex.getMessage());
            }
        }
    }

//    @Override
//    @Transactional
//    public Invoice deleteInvoiceByInvoiceId(String shopId, String invoiceId) {
//        // 1. Find and verify invoice
//        Invoice invoice = invoiceRepository.findByInvoiceId(invoiceId)
//                .orElseThrow(() -> new APIException("Invoice not found"));
//
//        if (!invoice.getShopId().equals(shopId)) {
//            throw new APIException("Invoice doesn't belong to this shop");
//        }
//
//        String customerId = invoice.getCustomer().getId();
//
//        // 2. Remove from shop (using DBRef)
//        UpdateResult shopUpdate = mongoTemplate.updateFirst(
//                Query.query(Criteria.where("_id").is(shopId)),
//                new Update().pull("allInvoice", new DBRef("invoice", invoice.getId())),
//                Shop.class
//        );
//
//        // 3. Handle customer references (both old and new schema)
//        Update customerUpdate = new Update()
////                .pull("allInvoices", Criteria.where("invoiceId").is(invoiceId)) // Old schema
//                .pull("allInvoiceIds", invoiceId); // New schema
//
//        UpdateResult customerUpdateResult = mongoTemplate.updateFirst(
//                Query.query(Criteria.where("_id").is(customerId)),
//                customerUpdate,
//                Customer.class
//        );
//
//        // 4. Delete invoice
//        invoiceRepository.delete(invoice);
//
//        return invoice;
//    }

    @Override
    public InvoiceDTO findByInvoiceId(String id) {
        User user = authUtil.loggedInUser();

        String userId = authUtil.loggedInUser().getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        Invoice invoice = invoiceRepository.findByInvoiceId(id)
                .orElseThrow(() -> new APIException("Invoice not found"));

        if (!invoice.getShopId().equals(shopId)) {
            throw new APIException("Invoice doesn't belong to this shop");
        }

        return modelMapper.map(invoice, InvoiceDTO.class);
    }
}
