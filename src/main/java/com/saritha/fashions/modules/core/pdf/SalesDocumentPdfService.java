package com.saritha.fashions.modules.core.pdf;

import com.mongodb.DBRef;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.core.date.DeliveryDates;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Invoice, receipt and payment receipt PDFs from the Word templates in src/main/resources:
 * - Invoice.docx        the tax invoice (items, totals, CGST / SGST or IGST, advance paid, balance due);
 * - Receipt.docx        the full receipt at delivery (items, totals, advance paid, total paid and every payment);
 * - PaymentReceipt.docx one recorded payment (with the invoice's paid / balance position after it).
 *
 * The documents are read as raw MongoDB documents (invoice / receipt / payment receipt, their customer
 * DBRef and the shop), the values formatted for print (amounts "1,20,000.00", dates dd-MM-yyyy IST),
 * WordTemplate fills the template and DocxToPdfConverter turns it into a PDF.
 *
 * GST rows: CGST + SGST for a same-state bill, IGST for another state (from the stored rates); the
 * unused rows are removed, all three when the bill has no GST.
 *
 * Shop scope: pass the caller's shop id to restrict the lookup, or null for any shop (only for
 * callers that already proved access, e.g. the platform owner or a valid customer link token).
 * A document of another shop is reported as "not found".
 */
@Service
public class SalesDocumentPdfService {

    private static final Logger log = LoggerFactory.getLogger(SalesDocumentPdfService.class);

    static final String INVOICE_TEMPLATE = "Invoice.docx";
    static final String RECEIPT_TEMPLATE = "Receipt.docx";
    static final String PAYMENT_RECEIPT_TEMPLATE = "PaymentReceipt.docx";

    /** The invoice shows at least this many item lines (blank ones fill the rest). */
    private static final int INVOICE_MIN_LINES = 6;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a", Locale.ENGLISH);

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
        Map<String, String> values = new HashMap<>();
        putShop(values, findShop(invoice.getString("shopId")));
        putCustomer(values, findCustomer(invoice.get("customer")));
        values.put("invoiceNo", invoice.getString("invoiceId"));
        values.put("invoiceDate", date(invoice.get("invoiceDate")));
        values.put("paymentMethod", invoice.getString("paymentMethod"));
        values.put("paymentStatus", invoice.getString("paymentStatus"));
        values.put("hsnCode", invoice.getString("hsnCode"));
        values.put("expectedDelivery", deliveryDate(invoice.getString("expectedDeliveryDate")));
        putAmounts(values, invoice);
        values.put("advancePaid", money(number(invoice.get("advancePaid"))));
        values.put("balanceDue", money(number(invoice.get("balanceDue"))));

        return render(INVOICE_TEMPLATE, "Invoice.docx", values,
                List.of(new WordTemplate.Rows("item", items(invoice), INVOICE_MIN_LINES)),
                gstRowsToDrop(invoice), "invoice", invoiceId);
    }

    /**
     * The full receipt at delivery: items, totals and every payment of the invoice.
     *
     * @param shopId the caller's shop, or null for any shop
     * @throws APIException "Receipt not found", or "Could not generate the receipt PDF, please try again"
     */
    public byte[] receiptPdf(String receiptNo, String shopId) {
        Document receipt = findOne("receipt", "receiptNo", receiptNo, shopId)
                .orElseThrow(() -> new APIException("Receipt not found"));
        // receipt.invoiceId holds the invoice's database id
        Document invoice = findById("invoice", receipt.getString("invoiceId")).orElse(new Document());

        Map<String, String> values = new HashMap<>();
        putShop(values, findShop(receipt.getString("shopId")));
        putCustomer(values, findCustomer(receipt.get("customer")));
        values.put("receiptNo", receipt.getString("receiptNo"));
        values.put("receiptDate", date(receipt.get("receiptDate")));
        values.put("deliveredOn", date(receipt.get("receiptDate")));
        values.put("invoiceNo", invoice.getString("invoiceId"));
        values.put("invoiceDate", date(invoice.get("invoiceDate")));
        values.put("hsnCode", firstText(receipt.getString("hsnCode"), invoice.getString("hsnCode")));
        putAmounts(values, receipt);

        List<Document> payments = payments(invoice);
        double advance = payments.stream().filter(p -> p.get("paymentReceiptNo") == null)
                .mapToDouble(p -> number(p.get("amount"))).sum();
        values.put("advancePaid", money(advance));
        values.put("totalPaid", money(number(receipt.get("advancePaid"))));
        values.put("balanceDue", money(number(receipt.get("balanceDue"))));

        List<Map<String, String>> rows = new ArrayList<>();
        for (Document payment : payments) {
            Map<String, String> row = new HashMap<>();
            row.put("no", String.valueOf(rows.size() + 1));
            row.put("paidAt", dateTime(payment.get("paidAt")));
            row.put("method", text(payment.get("paidVia")));
            row.put("transactionId", text(payment.get("transactionId")));
            Object slipNo = payment.get("paymentReceiptNo");
            row.put("receiptNo", slipNo != null ? slipNo.toString() : rows.isEmpty() ? "Advance (with invoice)" : null);
            row.put("amount", money(number(payment.get("amount"))));
            rows.add(row);
        }

        return render(RECEIPT_TEMPLATE, "Receipt.docx", values,
                List.of(new WordTemplate.Rows("item", items(receipt), 0), new WordTemplate.Rows("pay", rows, 0)),
                gstRowsToDrop(receipt), "receipt", receiptNo);
    }

    /**
     * The payment receipt of one recorded payment.
     *
     * @param shopId the caller's shop, or null for any shop
     * @throws APIException "Payment receipt not found", or "Could not generate the payment receipt PDF, please try again"
     */
    public byte[] paymentReceiptPdf(String paymentReceiptNo, String shopId) {
        Document slip = findOne("payment_receipts", "paymentReceiptNo", paymentReceiptNo, shopId)
                .orElseThrow(() -> new APIException("Payment receipt not found"));
        Document invoice = findById("invoice", slip.getString("invoiceDbId"))
                .or(() -> findOne("invoice", "invoiceId", slip.getString("invoiceId"), slip.getString("shopId")))
                .orElse(new Document());

        double amount = number(slip.get("amount"));
        double totalPaid = number(slip.get("totalPaid"));
        Map<String, String> values = new HashMap<>();
        putShop(values, findShop(slip.getString("shopId")));
        putCustomer(values, findCustomer(slip.get("customer")));
        values.put("paymentReceiptNo", slip.getString("paymentReceiptNo"));
        values.put("paidDate", date(slip.get("paidAt")));
        values.put("paidAt", dateTime(slip.get("paidAt")));
        values.put("paidVia", slip.getString("paidVia"));
        values.put("transactionId", slip.getString("transactionId"));
        values.put("amount", money(amount));
        values.put("invoiceTotal", money(number(slip.get("invoiceTotalWithGST"))));
        values.put("paidBefore", money(Math.max(0, totalPaid - amount)));
        values.put("totalPaid", money(totalPaid));
        values.put("balanceDue", money(number(slip.get("balanceDue"))));
        values.put("paymentStatus", slip.getString("paymentStatus"));
        values.put("invoiceNo", firstText(slip.getString("invoiceId"), invoice.getString("invoiceId")));
        values.put("invoiceDate", date(invoice.get("invoiceDate")));
        values.put("expectedDelivery", deliveryDate(invoice.getString("expectedDeliveryDate")));

        return render(PAYMENT_RECEIPT_TEMPLATE, "PaymentReceipt.docx", values, List.of(), Set.of(),
                "payment receipt", paymentReceiptNo);
    }

    // ------------------------------------------------------------------ rendering

    private byte[] render(String template, String uploadName, Map<String, String> values,
                          List<WordTemplate.Rows> rows, Set<String> dropRowsWith, String kind, String number) {
        try {
            byte[] docx = WordTemplate.fill(template, values, rows, dropRowsWith);
            return converter.convert(docx, uploadName);
        } catch (PdfGenerationException exception) {
            log.error("Could not generate the {} PDF for {}: {}", kind, number, exception.getMessage(), exception);
            throw new APIException("Could not generate the " + kind + " PDF, please try again");
        }
    }

    /** Item lines: qty, name, unit price, line total. */
    static List<Map<String, String>> items(Document source) {
        List<Map<String, String>> lines = new ArrayList<>();
        if (source.get("items") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Document item) {
                    double quantity = number(item.get("itemQuantity"));
                    double price = number(item.get("itemPrice"));
                    Map<String, String> line = new HashMap<>();
                    line.put("qty", quantity(quantity));
                    line.put("name", text(item.get("itemName")));
                    line.put("price", money(price));
                    line.put("total", money(quantity * price));
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /** Totals and GST shared by the invoice and the receipt. */
    static void putAmounts(Map<String, String> values, Document source) {
        values.put("totalAmount", money(number(source.get("totalAmount"))));
        values.put("discount", money(number(source.get("discount"))));
        values.put("taxableAmount", money(number(source.get("taxableAmount"))));
        values.put("cgstRate", rate(number(source.get("cgstRate"))));
        values.put("sgstRate", rate(number(source.get("sgstRate"))));
        values.put("igstRate", rate(number(source.get("igstRate"))));
        values.put("cgstAmount", money(number(source.get("cgstAmount"))));
        values.put("sgstAmount", money(number(source.get("sgstAmount"))));
        values.put("igstAmount", money(number(source.get("igstAmount"))));
        values.put("totalWithGst", money(number(source.get("totalAmountWithGST"))));
    }

    /**
     * GST rows to remove: IGST bill (other state) -> the CGST and SGST rows; CGST + SGST bill (same state)
     * -> the IGST row; no GST at all -> all three.
     */
    static Set<String> gstRowsToDrop(Document source) {
        boolean igst = number(source.get("igstRate")) > 0 || number(source.get("igstAmount")) > 0;
        boolean split = number(source.get("cgstRate")) > 0 || number(source.get("sgstRate")) > 0
                || number(source.get("cgstAmount")) > 0 || number(source.get("sgstAmount")) > 0;
        Set<String> drop = new HashSet<>();
        if (igst || !split) {
            drop.add("cgstAmount");
            drop.add("sgstAmount");
        }
        if (!igst) {
            drop.add("igstAmount");
        }
        return drop;
    }

    /** Recorded payments of the invoice (advance first), only real amounts. */
    private static List<Document> payments(Document invoice) {
        List<Document> payments = new ArrayList<>();
        if (invoice.get("paymentHistories") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Document payment && number(payment.get("amount")) > 0) {
                    payments.add(payment);
                }
            }
        }
        return payments;
    }

    // ------------------------------------------------------------------ loading

    private Optional<Document> findOne(String collection, String numberField, String number, String shopId) {
        if (number == null || number.isBlank()) {
            return Optional.empty();
        }
        Criteria criteria = Criteria.where(numberField).is(number);
        if (shopId != null) {
            criteria = criteria.and("shopId").is(shopId);
        }
        return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), Document.class, collection));
    }

    private Optional<Document> findById(String collection, String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(new Query(Criteria.where("_id").is(toId(id))), Document.class, collection));
    }

    /** Address, contact number and GSTIN for the header. Missing shop: an error. */
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
        values.put("shopAddress", shop.getString("shopAddress"));
        values.put("shopPhone", shop.getString("shopContactNumber"));
        values.put("shopGst", shop.getString("gstNumber"));
    }

    private static void putCustomer(Map<String, String> values, Document customer) {
        values.put("customerName", customer.getString("name"));
        values.put("customerPhone", customer.getString("phone"));
    }

    // ------------------------------------------------------------------ formatting

    /** 120000 -> "1,20,000.00" (Indian grouping, 2 decimals). */
    static String money(double value) {
        // java.text has no Indian grouping (last 3 digits, then pairs), so group by hand
        String plain = BigDecimal.valueOf(Math.abs(value)).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        String whole = plain.substring(0, plain.indexOf('.'));
        StringBuilder grouped = new StringBuilder();
        int length = whole.length();
        for (int i = 0; i < length; i++) {
            int left = length - i; // digits from here to the end
            if (i > 0 && (left == 3 || (left > 3 && (left - 3) % 2 == 0))) {
                grouped.append(',');
            }
            grouped.append(whole.charAt(i));
        }
        return (value < 0 ? "-" : "") + grouped + plain.substring(plain.indexOf('.'));
    }

    /** 2.5 -> "2.5", 5.0 -> "5". */
    static String rate(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** 2.0 -> "2", 2.5 -> "2.5". */
    static String quantity(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** A stored timestamp as dd-MM-yyyy (IST); null when missing. */
    static String date(Object value) {
        return value instanceof Date date ? DATE.format(date.toInstant().atZone(IST)) : null;
    }

    /** A stored timestamp as dd-MM-yyyy hh:mm AM (IST); null when missing. */
    static String dateTime(Object value) {
        return value instanceof Date date ? DATE_TIME.format(date.toInstant().atZone(IST)) : null;
    }

    /** The expected delivery date (stored as text in several formats) as dd-MM-yyyy, or as stored. */
    static String deliveryDate(String value) {
        return DeliveryDates.parse(value).map(DATE::format).orElse(value);
    }

    private static String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static Object toId(String id) {
        return id != null && ObjectId.isValid(id) ? new ObjectId(id) : id;
    }
}
