package com.saritha.fashions.service;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.ReceiptSequence;
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

    // Get the next sequence number for the current year (Ensuring Year in IST)
    private int getNextSequence() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        String currentYear = String.valueOf(calendar.get(Calendar.YEAR));

        Query query = new Query(Criteria.where("_id").is(currentYear));
        Update update = new Update().inc("sequence", 1);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);
        ReceiptSequence counter = mongoTemplate.findAndModify(query, update, options, ReceiptSequence.class);

        // If it's the first invoice of the year, reset sequence to 1
        if (counter == null || counter.getSequence() == 1) {
            mongoTemplate.updateFirst(query, new Update().set("sequence", 1), ReceiptSequence.class);
            return 1;
        }

        return counter.getSequence();
    }

    // Generate Invoice ID ensuring uniqueness
    @Transactional
    public String generateReceiptId(String transactionId) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        String currentDate = dateFormat.format(new Date());

        // Max 5 retries in case of duplicate
        for (int attempt = 0; attempt < 5; attempt++) {
            int sequence = getNextSequence();
            String invoiceId = transactionId + "-" + currentDate + "-" + String.format("%05d", sequence);

            if (!isReceiptIdExists(invoiceId)) {
                return invoiceId;
            }
        }

        throw new RuntimeException("Failed to generate a unique invoice ID after multiple attempts.");
    }

    // Check if Invoice ID already exists in MongoDB
    private boolean isReceiptIdExists(String invoiceId) {
        Query query = new Query(Criteria.where("invoiceId").is(invoiceId));
        return mongoTemplate.exists(query, Invoice.class);
    }
}
