package com.saritha.fashions.modules.core.pdf;

import com.mongodb.DBRef;
import com.saritha.fashions.exception.APIException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Invoice and receipt PDFs. Replaces ReceiptServiceImpl.getInvoicePdfMethod / getReceiptPdfMethod
 * (and the PDF part of its customer download methods) for the modules.
 *
 * The documents are read as raw MongoDB documents (invoice / receipt, their customer DBRef and the
 * shop), so this does not depend on any legacy model. Values are formatted exactly as the legacy
 * code formatted the legacy model's fields (amounts as Java doubles, e.g. "1850.0"; quantities as
 * ints), then SalesDocumentTemplate fills the same Word templates and DocxToPdfConverter turns
 * them into a PDF with the same Cloud Run service.
 *
 * Shop scope: pass the caller's shop id to restrict the lookup, or null for any shop (only for
 * callers that already proved access, e.g. the platform owner or a valid customer link token).
 * A document of another shop is reported as "not found".
 */
@Service
public class SalesDocumentPdfService {

    private static final Logger log = LoggerFactory.getLogger(SalesDocumentPdfService.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter STORED_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final MongoTemplate mongoTemplate;
    private final DocxToPdfConverter converter;

    public SalesDocumentPdfService(MongoTemplate mongoTemplate, DocxToPdfConverter converter) {
        this.mongoTemplate = mongoTemplate;
        this.converter = converter;
    }

    /**
     * @param shopId the caller's shop, or null for any shop
     * @throws APIException "Invoice not found", or "Could not generate the invoice PDF, please try again"
     */
    public byte[] invoicePdf(String invoiceId, String shopId) {
        Document invoice = findOne("invoice", "invoiceId", invoiceId, shopId)
                .orElseThrow(() -> new APIException("Invoice not found"));
        Document shop = findShop(invoice.getString("shopId"));
        Document customer = findCustomer(invoice.get("customer"));

        Map<String, String> values = new HashMap<>();
        values.put("date", formatStoredDate(invoice.get("invoiceDate")));
        values.put("InvoiceId", invoice.getString("invoiceId"));
        putShop(values, shop);
        values.put("customer_name", customer.getString("name"));
        values.put("customer_phone", customer.getString("phone"));

        return render(SalesDocumentTemplate.INVOICE_TEMPLATE, "Invoice.docx", values, invoice, "invoice", invoiceId);
    }

    /**
     * @param shopId the caller's shop, or null for any shop
     * @throws APIException "Receipt not found", or "Could not generate the receipt PDF, please try again"
     */
    public byte[] receiptPdf(String receiptNo, String shopId) {
        Document receipt = findOne("receipt", "receiptNo", receiptNo, shopId)
                .orElseThrow(() -> new APIException("Receipt not found"));
        Document shop = findShop(receipt.getString("shopId"));
        Document customer = findCustomer(receipt.get("customer"));
        String deliveryDate = receipt.getString("expectedDeliveryDate");

        Map<String, String> values = new HashMap<>();
        values.put("Date", formatStoredDate(receipt.get("receiptDate")));
        values.put("Receipt_No", receipt.getString("receiptNo"));
        putShop(values, shop);
        values.put("Customer_Name", customer.getString("name"));
        values.put("Customer_Contact", customer.getString("phone"));
        values.put("Customer_Id", customer.getString("customerId"));
        values.put("Due_Date", deliveryDate != null ? deliveryDate : "N/A");

        return render(SalesDocumentTemplate.RECEIPT_TEMPLATE, "Receipt.docx", values, receipt, "receipt", receiptNo);
    }

    // ------------------------------------------------------------------ rendering

    private byte[] render(String template, String uploadName, Map<String, String> values, Document source,
                          String kind, String number) {
        try {
            byte[] docx = SalesDocumentTemplate.fill(template, values, lines(source), totals(source));
            return converter.convert(docx, uploadName);
        } catch (PdfGenerationException exception) {
            log.error("Could not generate the {} PDF for {}: {}", kind, number, exception.getMessage(), exception);
            throw new APIException("Could not generate the " + kind + " PDF, please try again");
        }
    }

    private static List<SalesDocumentTemplate.Line> lines(Document source) {
        List<SalesDocumentTemplate.Line> lines = new ArrayList<>();
        Object items = source.get("items");
        if (items instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Document item) {
                    lines.add(new SalesDocumentTemplate.Line(item.getString("itemName"),
                            (int) number(item.get("itemQuantity")), number(item.get("itemPrice"))));
                }
            }
        }
        return lines;
    }

    private static SalesDocumentTemplate.Totals totals(Document source) {
        return new SalesDocumentTemplate.Totals(
                number(source.get("totalAmount")), number(source.get("discount")), number(source.get("taxableAmount")),
                number(source.get("cgstRate")), number(source.get("sgstRate")), number(source.get("igstRate")),
                number(source.get("cgstAmount")), number(source.get("sgstAmount")), number(source.get("igstAmount")),
                number(source.get("totalAmountWithGST")), number(source.get("advancePaid")), number(source.get("balanceDue")),
                source.getString("paymentMethod"), source.getString("paymentStatus"),
                source.getString("expectedDeliveryDate"), source.getString("hsnCode"));
    }

    // ------------------------------------------------------------------ loading

    private java.util.Optional<Document> findOne(String collection, String numberField, String number, String shopId) {
        if (number == null || number.isBlank()) {
            return java.util.Optional.empty();
        }
        Criteria criteria = Criteria.where(numberField).is(number);
        if (shopId != null) {
            criteria = criteria.and("shopId").is(shopId);
        }
        return java.util.Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), Document.class, collection));
    }

    /** Address, contact number and GSTIN for the header. Missing shop: as before, an error. */
    private Document findShop(String shopId) {
        Query query = new Query(Criteria.where("_id").is(toId(shopId)));
        query.fields().include("shopAddress").include("shopContactNumber").include("gstNumber");
        Document shop = shopId == null ? null : mongoTemplate.findOne(query, Document.class, "shops");
        if (shop == null) {
            throw new APIException("Shop not found for ID: " + shopId);
        }
        return shop;
    }

    /** The customer behind the document's DBRef; an empty document if it no longer exists. */
    private Document findCustomer(Object customerRef) {
        if (customerRef instanceof DBRef ref) {
            Document customer = mongoTemplate.findOne(new Query(Criteria.where("_id").is(ref.getId())),
                    Document.class, ref.getCollectionName());
            if (customer != null) {
                return customer;
            }
        }
        return new Document();
    }

    private static void putShop(Map<String, String> values, Document shop) {
        values.put("Mob_No", shop.getString("shopContactNumber"));
        values.put("Shop_Address", shop.getString("shopAddress"));
        values.put("Shop_GST", shop.getString("gstNumber"));
    }

    // ------------------------------------------------------------------ formatting

    /** Stored date as yyyy-MM-dd in IST; today as dd-MM-yyyy when missing (as the legacy code). */
    private static String formatStoredDate(Object value) {
        if (value instanceof Date date) {
            return date.toInstant().atZone(IST).toLocalDate().format(STORED_DATE);
        }
        return LocalDate.now(IST).format(TODAY);
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static Object toId(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id) : id;
    }
}
