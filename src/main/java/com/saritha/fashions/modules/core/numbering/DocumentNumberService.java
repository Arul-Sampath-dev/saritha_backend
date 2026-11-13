package com.saritha.fashions.modules.core.numbering;

import com.saritha.fashions.exception.APIException;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Issues invoice and receipt numbers: {prefix}-{dd-MM-yyyy}-{00001}, with a running number per
 * shop and calendar year (IST). Replaces the legacy InvoiceSequenceService and
 * ReceiptSequenceService for the modules; the stored counters and the number format are unchanged.
 *
 * How a number is issued:
 *  1. Atomically increment the shop's counter for the current year ($inc with upsert, so the first
 *     number of a year is 1). The date and the year come from the same moment, so a number issued
 *     around midnight on 31 December cannot mix the two years.
 *  2. If the number is already used (a legacy delete moved the counter back, or a number was
 *     issued by hand), take the next one; numbers of deleted invoices are never re-used.
 *
 * Differences from the legacy services:
 * - two simultaneous first numbers of a year no longer fail: the losing upsert is retried;
 * - a deleted invoice's number is never issued again;
 * - failure is a clear APIException, not a RuntimeException ("...invoice ID..." also for receipts).
 */
@Service
public class DocumentNumberService {

    private static final Logger log = LoggerFactory.getLogger(DocumentNumberService.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    /** Numbers tried before giving up (each try takes the next counter value). */
    private static final int MAX_ATTEMPTS = 20;

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Autowired
    public DocumentNumberService(MongoTemplate mongoTemplate) {
        this(mongoTemplate, Clock.system(IST));
    }

    DocumentNumberService(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    /**
     * The next unused number for the shop, e.g. "SARI-12-11-2026-00042".
     *
     * @param prefix already cleaned by the caller (e.g. the first letters of the shop name)
     */
    public String nextNumber(DocumentType type, String shopId, String prefix) {
        if (shopId == null || shopId.isBlank()) {
            throw new IllegalArgumentException("shopId is required");
        }
        LocalDate today = LocalDate.now(clock.withZone(IST));
        String year = String.valueOf(today.getYear());
        String datePart = today.format(DATE);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long sequence = nextSequence(type, year, shopId);
            String number = prefix + "-" + datePart + "-" + String.format("%05d", sequence);
            if (!isUsed(type, number)) {
                return number;
            }
            log.warn("{} number {} is already used; taking the next one", type, number);
        }
        throw new APIException("Could not generate a unique " + type.name().toLowerCase() + " number, please try again");
    }

    /** Atomically increments and returns the counter of {year}-{shopId}. */
    private long nextSequence(DocumentType type, String year, String shopId) {
        Query query = new Query(Criteria.where("_id").is(year + "-" + shopId));
        Update update = new Update().inc("sequence", 1).setOnInsert("year", year).setOnInsert("shopId", shopId);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        Document counter;
        try {
            counter = mongoTemplate.findAndModify(query, update, options, Document.class, type.counterCollection());
        } catch (DuplicateKeyException exception) {
            // Two requests created the year's counter at the same moment; it exists now, so just increment.
            counter = mongoTemplate.findAndModify(query, update, options, Document.class, type.counterCollection());
        }
        Object sequence = counter == null ? null : counter.get("sequence");
        if (!(sequence instanceof Number number)) {
            throw new APIException("Could not read the " + type.name().toLowerCase() + " number counter");
        }
        return number.longValue();
    }

    private boolean isUsed(DocumentType type, String number) {
        for (DocumentType.UsedNumberSource source : type.usedNumberSources()) {
            if (mongoTemplate.exists(new Query(Criteria.where(source.field()).is(number)), source.collection())) {
                return true;
            }
        }
        return false;
    }
}
