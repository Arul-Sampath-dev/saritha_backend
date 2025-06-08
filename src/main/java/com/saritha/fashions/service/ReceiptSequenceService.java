package com.saritha.fashions.service;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.InvoiceSequence;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.model.ReceiptSequence;
import com.saritha.fashions.util.DateTimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

@Service
public class ReceiptSequenceService {


    @Autowired
    private MongoTemplate mongoTemplate;

    // Get the next sequence number for the current year and shop
    private int getNextSequence(String shopId) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        String currentYear = String.valueOf(calendar.get(Calendar.YEAR));
        String sequenceId = currentYear + "-" + shopId; // Unique ID for year+shop combination

        Query query = new Query(Criteria.where("_id").is(sequenceId));
        Update update = new Update().inc("sequence", 1);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);

        ReceiptSequence counter = mongoTemplate.findAndModify(
                query,
                update,
                options,
                ReceiptSequence.class
        );

        // If it's the first invoice of the year for this shop, reset sequence to 1
        if (counter == null || counter.getSequence() == 1) {
            mongoTemplate.upsert(
                    query,
                    new Update()
                            .set("year", currentYear)
                            .set("shopId", shopId)
                            .set("sequence", 1),
                    ReceiptSequence.class
            );
            return 1;
        }

        return counter.getSequence();
    }

    @Transactional
    public String generateReceiptId(String shopId, String shopName) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        String currentDate = dateFormat.format(DateTimeUtils.getNowInIST());

        // Max 5 retries in case of duplicate
        for (int attempt = 0; attempt < 5; attempt++) {
            int sequence = getNextSequence(shopId);
            String receiptNo = shopName + "-" + currentDate + "-" + String.format("%05d", sequence);

            if (!isReceiptIdExists(receiptNo)) {
                return receiptNo;
            }
        }

        throw new RuntimeException("Failed to generate a unique invoice ID after multiple attempts.");
    }

    private boolean isReceiptIdExists(String receiptNo) {
        Query query = new Query(Criteria.where("receiptNo").is(receiptNo));
        return mongoTemplate.exists(query, Receipt.class);
    }

//    @Autowired
//    private MongoTemplate mongoTemplate;
//
//    // Get the next sequence number for the current year (Ensuring Year in IST)
//    private int getNextSequence() {
//        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
//        String currentYear = String.valueOf(calendar.get(Calendar.YEAR));
//
//        Query query = new Query(Criteria.where("_id").is(currentYear));
//        Update update = new Update().inc("sequence", 1);
//        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);
//        ReceiptSequence counter = mongoTemplate.findAndModify(query, update, options, ReceiptSequence.class);
//
//        // If it's the first invoice of the year, reset sequence to 1
//        if (counter == null || counter.getSequence() == 1) {
//            mongoTemplate.updateFirst(query, new Update().set("sequence", 1), ReceiptSequence.class);
//            return 1;
//        }
//
//        return counter.getSequence();
//    }
//
//    // Generate Invoice ID ensuring uniqueness
//    @Transactional
//    public String generateReceiptId(String transactionId, String shopName) {
//        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
//        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
//        String currentDate = dateFormat.format(new Date());
//
//        // Max 5 retries in case of duplicate
//        for (int attempt = 0; attempt < 5; attempt++) {
//            int sequence = getNextSequence();
//            String invoiceId = shopName + "-" + currentDate + "-" + String.format("%05d", sequence);
//
//            if (!isReceiptIdExists(invoiceId)) {
//                return invoiceId;
//            }
//        }
//
//        throw new RuntimeException("Failed to generate a unique invoice ID after multiple attempts.");
//    }
//
//    // Check if Invoice ID already exists in MongoDB
//    private boolean isReceiptIdExists(String invoiceId) {
//        Query query = new Query(Criteria.where("invoiceId").is(invoiceId));
//        return mongoTemplate.exists(query, Invoice.class);
//    }
}
